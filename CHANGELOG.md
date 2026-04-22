# CT-GenAI-DICOM 更新日志

## [4.7-SNAPSHOT] - 2026-04-22

### 🔴 严重问题修复（Critical Fixes）

#### 1. 修复 DICOM 像素数据 Header 缺失导致图像全黑
- **问题描述**：旧版在流式双写时，直接拷贝像素数据但未先写入 `PixelData` 的 Tag/VR/Length 头部。DICOM 解析器无法识别像素边界，将像素二进制流误解释为 Header 数据，导致输出的影像文件在所有查看器中显示为纯黑。
- **影响范围**：所有经脱敏处理的 DICOM 输出文件均不可用。
- **修复方式**：在 `SedaWriter.writeToDualTarget()` 中，于 `copyTo()` 之前增加判断：
  ```kotlin
  if (dis.tag() == org.dcm4che3.data.Tag.PixelData) {
      dos.writeHeader(dis.tag(), dis.vr(), dis.length())
  }
  ```

#### 2. 修复 DicomOutputStream 构造时 TransferSyntaxUID 使用错误
- **问题描述**：旧版将源文件的 `TransferSyntaxUID` 直接传入 `CustomDicomOutputStream` 构造函数，或回退到硬编码的旧 UID。在 `dcm4che` 的设计规范中，该构造参数应固定使用 `ExplicitVRLittleEndian`，具体语法特性通过后续 FileMetaInformation 写入来覆盖。
- **影响范围**：可能导致输出文件的传输语法标识不兼容，部分 PACS 系统或查看器拒绝导入。
- **修复方式**：固定使用 `org.dcm4che3.data.UID.ExplicitVRLittleEndian`。

#### 3. 修复 NFS / NTFS 第二路写入的空字符串穿透
- **问题描述**：旧版仅判断 `nfsOutputPath != null`，但配置项可能为空字符串 `""`，导致程序向非法路径（甚至当前工作目录）写入备份文件，造成数据污染或覆盖。
- **影响范围**：当 `DST_DICOM_PATH2` 未配置或配置为空时，第二路写入行为不可控。
- **修复方式**：
  - `Main.kt` 中：`Settings.DST_DICOM_PATH2.ifBlank { null }`
  - `SedaWriter.kt` 中：`nfsOutputPath != null && nfsOutputPath.isNotBlank()`

#### 4. 修复日志 `%` 格式化闪退导致程序终止
- **问题描述**：当日志参数为文件路径且路径中包含 `%` 字符（如 `%20`、`%s` 等）时，如果调用的是无参或空参重载（如 `log(path)`），`String.format` 会尝试解析路径中的 `%` 为格式控制符。一旦遇到非法格式定义（如 `%` 后跟随不认识的转换符），直接抛出 `IllegalFormatException`，导致日志线程甚至业务线程崩溃闪退。
- **影响范围**：任何记录用户文件路径、网络 URL、或包含 `%` 的目录名的日志调用均可能触发。
- **修复方式**：在 `LogUtils` 中新增统一的 `sendToLogQueue()` 安全分发方法：
  - 若 `content` 为空，或仅包含一个 `null` / 空字符串参数，则**直接拼接不解析 `String.format`**；
  - 仅当存在有效格式化参数时，才安全调用 `String.format`。
  - `log()`、`info()`、`debug()`、`err()` 全部委托此统一入口。

#### 5. 修复计数系统双重累加导致待完成数变为负数
- **问题描述**：旧版中存在多处重复计数：
  1. **异常分支**：`SedaProcessor` 的 `catch` 块中对失败文件计数一次，`SedaWriter` 收到 `isSuccess=false` 的结果后又计数一次，导致单个异常文件被统计两次 `fileError`。
  2. **黑名单分支**：`SedaProcessor` 中黑名单拦截时计数一次 `fileIgnored`，后续 `printSkipInfoMessage()` 内部再次计数一次，导致单个跳过文件被统计两次 `fileIgnored`。
- **影响范围**：`SedaStats.remainingTotal`（待完成总数）会随着处理进行不断减少，最终变为负数，进度百分比失真，监控日志显示异常。
- **修复方式**：
  - 删除 `SedaProcessor.catch` 块中的 `fileError.incrementAndGet()`，统一由 `SedaWriter` 根据结果状态单点统计；
  - 注释掉 `printSkipInfoMessage()` 内部的 `fileIgnored.incrementAndGet()`，仅保留调用方的单次计数。

### 🟠 功能增强（Enhancements）

#### 6. 编码映射 CSV 格式扩展（2列 → 6列）
- 旧版 CSV 仅支持 `原始受试者编码,脱敏受试者编码` 两列；
- 新版扩展为六列：`原始中心编码,原始受试者编码,原始受试者编号,脱敏中心编码,脱敏受试者编码,脱敏受试者编号`；
- `CodeModule` 新增 `mSiteCode`、`vSiteCode`、`mSubjectCode`、`mvSubjectCode` 字段，支持中心级别脱敏；
- Map 检索 Key 从 `originSubjectCode` 调整为 `originSubjectNumber`，稳定性更强；
- 增加 `isBlank()` 校验，自动过滤 CSV 中的空记录。

#### 7. 路径替换占位符系统扩展
- `Settings` / `SedaScanner.calculateRelPath` 的占位符从 2 个扩展到 4 个：
  - `@originSubjectNumber` / `@desensitizedSubjectNumber`
  - `@originSubjectCode` / `@desensitizedSubjectCode`（新增）
- 默认正则表达式更复杂，支持保留目录结构中特定信息，如 `F__$1__$2` 形式的后续目录名重组。

#### 8. 动态黑名单审计（目录级实时拦截）
- `SedaProcessor` 在读取 DICOM Header 后实时判定是否命中黑名单规则（`3D Saved State`、`biomind`、血流动力学后处理图像等）；
- 命中后通过 `ctx.blacklistedDirs` 立即拉黑整个目录，后续同目录文件在 Scanner/Processor 双阶段均被丢弃，避免无效 I/O。

#### 9. 日期/时间标签保留策略调整
- 旧版默认强制清空 `StudyDate`、`SeriesDate`、`AcquisitionDate`、`ContentDate` 及对应 Time 标签；
- **新版已将这些清空逻辑注释掉**，脱敏后的影像保留原始时间元数据，避免业务层面的时间信息不可逆丢失。

#### 10. 异常日志上下文增强
- `LogUtils` 新增 `err(tips: String?, err: Throwable)` 与 `errNoPrint(tips: String?, err: Throwable)` 重载；
- 允许在记录异常堆栈前附加自定义前缀提示，便于快速定位问题源文件或操作上下文。

### 🟢 工程化与架构改进（Engineering Improvements）

#### 11. Kotlin 与依赖升级
- **Kotlin**：`1.7.10` → `2.1.21`
- **Coroutines**：硬编码 `1.6.4` → BOM 统一管控 `1.10.1`
- **新增依赖**：
  - `com.google.guava:guava:32.1.3-jre`（替换危险的 Kotlin 编译器内部 Guava）
  - `org.jetbrains.kotlinx:kotlinx-coroutines-jdk8`
- **JVM Target**：`21`

#### 12. 移除危险内部 API 依赖
- 旧版 `SedaScanner` 错误引用 `org.jetbrains.kotlin.com.google.common.util.concurrent.RateLimiter`（Kotlin 编译器内部包，非公共 API，随时可能随 Kotlin 升级失效）；
- 新版修复为正式依赖 `com.google.common.util.concurrent.RateLimiter`。

#### 13. 构建系统重构
- **项目名**：`DicomUtils` → `CT-GenAI-DICOM`
- **版本**：`2.4-SNAPSHOT` → `4.7-SNAPSHOT`
- 移除 Maven Wrapper（`mvnw` / `mvnw.cmd`），统一为 Gradle 构建；
- 新增 `settings.gradle.kts` 的 `pluginManagement` 与 FooJay Toolchain Resolver；
- 新增完整的 Fat-Jar 打包配置（`tasks.jar`），输出目录支持 `outputDir` 属性注入；
- 移除仓库配置中的 `isAllowInsecureProtocol = true`，禁止明文 HTTP 拉取依赖。

#### 14. 外置配置模板化
- 新增 `program_settings.properties`（Windows 模板）与 `program_settings-linux.properties`（Linux 模板）；
- 默认路径从具体绝对路径改为占位符形式（如 `/data01/<ProjectDir>/Images/<DICOM_Origin>`），便于跨环境部署。

#### 15. 扫描与写入并发优化
- `SedaScanner` 中 `fileScanned` 计数位置从 `inspectAndDispatch()` 末尾调整至开头，统计更准确；
- `SedaWriter` 双路写入异常时，`nfsFile?.delete()` 从强制非空调用 `!!` 改为安全调用，避免 NPE。

---

### 已知限制与注意事项

1. **日志队列关闭**：`LogUtils.release()` 在程序退出时最多等待约 5 秒刷盘，若积压海量日志可能丢失最后几条；
2. **审计日志**：`mapping_audit_*.csv` 的表头为固定模板，实际写入字段以 `OriginDicomData.toCsvLine()` 为准，建议后续版本对齐表头与字段；
3. **黑名单规则硬编码**：`3D Saved State`、`biomind`、血流动力学后处理等跳过规则目前写死在 `SedaProcessor` 中，如需调整需改源码重新打包。
