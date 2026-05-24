# DicomUtils / CT-GenAI-DICOM 设计文档

> 版本：4.7-SNAPSHOT
> 最后更新：2026-05-24

---

## 1. 项目定位

DICOM 影像批量脱敏与分发工具。核心能力：从原始 DICOM 目录树中扫描、读取元数据、替换患者身份信息、过滤非必要序列、输出为脱敏后的 DICOM 文件，同时留存原始隐私数据审计日志。

---

## 2. 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 2.1.21 (JVM 21) |
| 构建 | Gradle 8.x + Kotlin DSL |
| DICOM 引擎 | DCM4CHE 5.28.0 (`dcm4che-core`) |
| 并发 | Kotlinx Coroutines 1.10.1 (BOM) |
| 辅助库 | Guava 32.1.3, Apache Commons Compress/IO |

---

## 3. 核心架构：SEDA 2.0 三阶流水线

采用 **Staged Event-Driven Architecture (SEDA)**，三阶段通过 Kotlin `Channel` 解耦，各阶段独立扩缩容。

```
┌─────────┐   taskChannel    ┌───────────┐   writeChannel   ┌─────────┐
│ Scanner │ ───────────────→ │ Processor │ ───────────────→ │ Writer  │
│  (IO)   │  DicomTask       │   (CPU)   │  ProcessedResult │  (IO)   │
└─────────┘                  └───────────┘                  └─────────┘
     ↑                            ↑                              ↑
   单协程                    cpuParallelism               ntfsWriterParallelism
  BFS 扫描                   元数据读取+脱敏                双路流式写入
```

### 3.1 Scanner（扫描器）

- **调度器**：`Dispatchers.IO`，单协程执行 BFS 目录遍历
- **限速**：Guava `RateLimiter` 控制 `scanIOPS`
- **目录过滤**：三层正则匹配（受试者目录 / DW 目录 / 其他目录）
- **文件前置过滤**：纯内存判定，短路非 DICOM 文件（`.nii.gz`, `.json`, `.jpg` 等）
- **黑名单**：动态目录级拦截。Processor 发现违规 Series 后写入 `ctx.blacklistedDirs`，Scanner 后续同目录文件直接丢弃
- **分发**：每个合法 DICOM 文件封装为 `DicomTask`，通过 `taskChannel` 投递

### 3.2 Processor（处理器）

- **调度器**：`Dispatchers.Default`，`cpuParallelism = CPU 核心数`
- **流式读取**：`CustomDicomInputStream.readDatasetUntilPixelData()` —— 仅读取元数据到 PixelData 前停止，不加载图像体数据
- **Series 过滤**：可配置的组合策略树（AND / OR / NOT + regex_match / file_count_*），命中后整目录拉黑
- **脱敏操作**：
  - `PatientName` / `PatientID` → 脱敏编号
  - `InstitutionName`, `PatientSex`, `PatientAge`, `PatientWeight`, `PatientBirthDate`, `StudyDescription`, `DeviceSerialNumber` → 置空
  - 日期标签（StudyDate 等）**保留**（v4.7 策略变更）
- **审计数据提取**：构建 `OriginDicomData`，包含原始隐私信息，随结果包投递至 Writer

### 3.3 Writer（写入器）

- **调度器**：自定义线程池 `ntfsDispatcher`，并发度 `ntfsWriterParallelism`（默认 8）
- **双路写入**：
  - 主路：NTFS 本地盘（`ntfsOutputPath`）
  - 辅路：NFS 远程盘（`nfsOutputPath`，可选）
- **三通管 `DualOutputStream`**：一次 `read`，同时 `write` 到两路输出，不缓存完整文件
- **幂等**：目标文件已存在且 `length > 0` 则跳过
- **DICOM 格式修正**：
  - 固定 `TransferSyntaxUID = ExplicitVRLittleEndian`
  - PixelData 前补写 `Tag/VR/Length` Header（修复 v4.7 之前的黑图问题）

---

## 4. 数据模型

### 4.1 DicomTask
```kotlin
data class DicomTask(
    val originFile: File,
    val codeModule: CodeModule,       // 原始/脱敏编号映射
    val targetRelativePath: String,   // 目标相对路径
    val isDW: Boolean = false,         // 是否 DW 序列
    val parentFileCount: Int           // 父目录文件数（用于过滤策略）
)
```

### 4.2 ProcessedResult
```kotlin
class ProcessedResult(
    val originFile: File,
    val targetRelativePath: String,
    val codeModule: CodeModule,
    val attributes: Attributes?,        // 脱敏后元数据
    val originDicomData: OriginDicomData?,  // 原始隐私数据（审计用）
    val isSuccess: Boolean
)
```

### 4.3 CodeModule
编码映射核心，支持六列 CSV：
```
原始中心编码,原始受试者编码,原始受试者编号,脱敏中心编码,脱敏受试者编码,脱敏受试者编号
```

---

## 5. 过滤策略系统

支持从 `program_settings.properties` 动态解析策略树。

| 策略类型 | 说明 |
|---------|------|
| `regex_match` | DICOM Tag 值匹配正则则跳过 |
| `regex_not_match` | DICOM Tag 值不匹配正则则跳过 |
| `file_count_lt/le/gt/ge/eq` | 父目录文件数比较 |
| `and` | 所有子策略命中才跳过 |
| `or` | 任一子策略命中就跳过 |
| `not` | 子策略未命中才跳过 |

策略之间为 **OR** 关系。默认硬编码三条规则：`3D Saved State`、`biomind`、血流动力学后处理图像（Processed Images / Blood Flow / Blood Volume / Mean Transit Time）且文件数 < 100。

---

## 6. 配置系统

单一 `program_settings.properties` 文件驱动全部行为：

| 配置项 | 说明 |
|--------|------|
| `VCODE_CSV_FILE_PATH` | 编码映射 CSV 路径 |
| `SRC_DICOM_PATH` | 输入 DICOM 根目录 |
| `DST_DICOM_PATH` | 主路输出目录 |
| `DST_DICOM_PATH2` | 辅路输出目录（可为空） |
| `LOG_PATH` | 日志输出目录 |
| `SUBJECT_DIR_VALID_REGEX` | 受试者目录验证正则 |
| `DICOM_DW_DIR_VALID_REGEX` | DW 层目录正则 |
| `ORIGIN_SUBJECT_*` | 路径截取与替换正则组 |
| `filter.strategy[N].*` | Series 过滤策略树 |

---

## 7. 统计与监控

`SedaStats` 提供三层原子计数器：

- **扫描层**：`scannedDirCount`, `scannerQueueSize`, `fileScanned`, `tasksDelivered`
- **处理层**：`fileProcessed`, `fileSuccess`, `fileError`, `fileIgnored`
- **受试者层**：`subjectSuccess`, `subjectIgnored`, `subjectError`
- **备份层**：`backupSuccess`, `backupError`

每 5 秒输出一次汇总日志（总耗时、各层计数、当前路径）。

---

## 8. 日志系统

`LogUtils` 异步生产者-消费者模型：

- 四条独立日志流：`*.log` / `*_debug.log` / `*_info.log` / `*.err`
- 守护线程消费 `LinkedBlockingQueue`，避免 I/O 阻塞业务协程
- `err()` 带 `ReentrantLock` 保证堆栈不穿插
- `%` 格式化安全处理：无有效参数时直接拼接，不调用 `String.format`

---

## 9. 关键设计决策

### 9.1 流式处理不缓存像素数据
`readDatasetUntilPixelData()` 确保内存中只保留元数据（几十 KB），PixelData 通过 `copyTo()` 直接管道到输出流，避免百 MB 级图像载入 JVM 堆。

### 9.2 双路写入异常隔离
`DualOutputStream` 对主/辅两路分别捕获异常，一路故障不影响另一路。两路均故障时抛 `IOException`，Writer 删残片后标记失败。

### 9.3 动态黑名单而非静态配置
黑名单在运行时由 Processor 根据 Series 内容判定后写入，Scanner 和后续 Processor Worker 共享同一个 `ConcurrentHashMap.newKeySet()`，实现跨阶段实时拦截。

### 9.4 日期标签保留
脱敏策略从"全部清空"改为"保留日期/时间标签"，避免下游时间序列分析不可逆丢失信息。

---

## 10. 许可证说明

本项目作为 DCM4CHE 的调用方（Maven 依赖引入，未修改 DCM4CHE 源码），不受 MPL/GPL/LGPL copyleft 传染。

- **本项目自有代码**：All Rights Reserved（闭源，未授权公开分发）
- **第三方依赖 DCM4CHE**：受其 MPL 1.1 / GPL 2.0 / LGPL 2.1 三重许可证约束，完整许可证文本见 [dcm4che.org](https://www.dcm4che.org)
- **其他第三方依赖**：Kotlin Coroutines / Guava / Apache Commons 等遵循各自原许可证

---

## 11. 已知限制

1. 黑名单规则默认硬编码在 `SedaProcessor`，修改需重新打包
2. `mapping_audit_*.csv` 表头与实际字段未对齐
3. `LogUtils.release()` 最多等待 5 秒刷盘，极端积压可能丢尾行
4. 多帧影像仅检测（`NumberOfFrames > 1`），不做特殊处理
