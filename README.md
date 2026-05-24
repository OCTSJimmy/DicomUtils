# DicomUtils / CT-GenAI-DICOM

DICOM 影像批量脱敏与分发工具。基于 Kotlin + DCM4CHE 构建，采用 SEDA 2.0 三阶流水线架构，支持双路流式写入与动态 Series 过滤。

---

## 快速开始

### 环境要求

- JDK 21+
- Gradle 8.x

### 构建

```bash
./gradlew jar
```

输出目录默认为 `release/`，可通过 `-PoutputDir=...` 覆盖。

### 运行

```bash
java -jar release/DicomUtils-4.7-SNAPSHOT.jar
```

运行前确保当前目录存在 `program_settings.properties` 配置文件。

---

## 配置

复制 `program_settings.properties` 或 `program_settings-linux.properties` 到工作目录，按需修改：

```properties
# 编码映射表（CSV，六列）
VCODE_CSV_FILE_PATH = /data01/<ProjectDir>/TextFiles/CodeN.csv

# 输入输出路径
SRC_DICOM_PATH  = /data01/<ProjectDir>/Images/<DICOM_Origin>
DST_DICOM_PATH  = /data02/<ProjectDir>/Images/<DICOM_Release>
DST_DICOM_PATH2 = /data03/<ProjectDir>/Images/<DICOM_Release>
LOG_PATH        = /home/user/<ProjectDir>/Logs

# 受试者目录验证正则
SUBJECT_DIR_VALID_REGEX=^.*\/[0-9]{14}_[0-9]{9}_[^\/]*$

# Series 过滤策略（可选，默认已内置三条硬编码规则）
# filter.strategy[0].type=regex_match
# filter.strategy[0].tag=SeriesDescription
# filter.strategy[0].pattern=^.*(3D Saved State).*$
# filter.strategy[0].ignoreCase=true
```

CSV 编码映射格式：

```
原始中心编码,原始受试者编码,原始受试者编号,脱敏中心编码,脱敏受试者编码,脱敏受试者编号
```

---

## 架构

```
Scanner ──taskChannel──→ Processor ──writeChannel──→ Writer
   (IO)                   (CPU)                     (IO)
   BFS扫描                元数据读取                  双路写入
   动态黑名单             脱敏替换                    幂等跳过
                          Series过滤
```

三阶段通过 Kotlin `Channel` 解耦，各自独立并发：

| 阶段 | 并发数 | 调度器 |
|------|--------|--------|
| Scanner | 1 | IO |
| Processor | CPU 核心数 | Default |
| Writer | 8（可配置） | 自定义线程池 |

核心特性：

- **流式处理**：`readDatasetUntilPixelData()` 不加载像素数据，元数据-only 处理
- **双路写入**：`DualOutputStream` 一次读取同时写入 NTFS + NFS，不缓存完整文件
- **动态黑名单**：Processor 判定违规 Series 后实时通知 Scanner 丢弃同目录后续文件
- **Series 过滤**：可配置策略树（AND / OR / NOT + 正则 / 文件数），支持 `program_settings.properties` 热配置
- **审计日志**：`mapping_audit_*.csv` 留存原始隐私信息，便于溯源

---

## 目录结构

```
src/main/kotlin/top/elune/utils/
├── Main.kt                          # 入口
├── commons/                         # 配置、上下文、统计
│   ├── Settings.kt                  # 全局配置加载
│   ├── SedaConfig.kt                # 流水线配置
│   ├── SedaContext.kt               # 运行时上下文（Channel、调度器）
│   ├── SedaStats.kt                 # 原子统计
│   ├── CodeManager.kt               # 编码映射表加载
│   └── CodeModule.kt                # 单条编码映射
├── engine/                          # SEDA 三阶流水线
│   ├── SedaEngine.kt                # 编排器
│   ├── SedaScanner.kt               # 扫描器
│   ├── SedaProcessor.kt             # 处理器（脱敏核心）
│   ├── SedaWriter.kt                # 写入器（含 DualOutputStream）
│   ├── DicomTask.kt                 # 任务定义
│   └── ProcessedResult.kt           # 结果定义
├── dicom/                           # DICOM 流封装
│   ├── CustomDicomInputStream.kt
│   ├── CustomDicomOutputStream.kt
│   └── OriginDicomData.kt           # 原始隐私数据结构
├── filter/                          # Series 过滤策略
│   ├── factory/SeriesFilterStrategyFactory.kt
│   └── strategy/
│       ├── SeriesFilterStrategy.kt
│       └── CompositeFilterStrategies.kt
└── utils/
    ├── LogUtils.kt                  # 异步日志
    └── StringUtils.java             # Levenshtein 相似度
```

---

## 日志

运行时自动在 `LOG_PATH` 下创建以时间戳命名的日志目录：

- `yyyy-MM-dd_HH_mm_ss.log` —— 通用日志
- `yyyy-MM-dd_HH_mm_ss_info.log` —— 信息日志
- `yyyy-MM-dd_HH_mm_ss_debug.log` —— 调试日志
- `yyyy-MM-dd_HH_mm_ss.err` —— 错误日志（含堆栈）
- `mapping_audit_*.csv` —— 审计日志（原始隐私数据留底）

---

## 版本

- 当前版本：`4.7-SNAPSHOT`
- 变更详情见 [CHANGELOG.md](./CHANGELOG.md)

---

## 许可证

本项目自有代码 **All Rights Reserved**（闭源，未授权公开分发）。

本项目包含以下第三方开源组件：

| 组件 | 许可证 |
|------|--------|
| [DCM4CHE](https://www.dcm4che.org) | MPL 1.1 / GPL 2.0 / LGPL 2.1（三重许可） |
| Kotlin / Kotlinx Coroutines | Apache 2.0 |
| Guava | Apache 2.0 |
| Apache Commons | Apache 2.0 |

使用、修改或分发 DCM4CHE 组件时，须遵守其原始许可证条款。
