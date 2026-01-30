package top.elune.utils.engine

import kotlinx.coroutines.*
import top.elune.utils.commons.*
import top.elune.utils.utils.LogUtils
import java.io.File
import java.util.*

class SedaScanner(private val ctx: SedaContext) {

    private val rootDir = File(ctx.config.inputPath)

    fun start() = ctx.engineScope.launch(Dispatchers.IO) {
        val queue: Deque<File> = ArrayDeque()
        queue.add(rootDir)

        LogUtils.info("SEDA Scanner 启动，开始广度优先扫描: ${rootDir.absolutePath}")

        try {
            while (queue.isNotEmpty() && isActive) {
                val current = queue.poll() ?: continue
                val files = current.listFiles() ?: continue // 使用 listFiles 避免 SMB 句柄泄露

                for (file in files) {
                    if (file.isDirectory) {
                        // 1. 目录合规性检测 (受试者级、DW级、其他级)
                        val path = file.absolutePath
                        if (isValidDirectory(path)) {
                            queue.add(file)
                        } else {
                            // 不符合任何正则的目录直接跳过，保护系统不进入无关目录
                            LogUtils.debugNoPrint("跳过非合规目录: $path")
                        }
                    } else if (file.name.lowercase().endsWith(".dcm")) {
                        // 2. 发现影像文件，开始构建 DicomTask
                        processFileTask(file)
                    }
                }
            }
        } catch (e: Exception) {
            LogUtils.err("Scanner 发生致命异常: ${e.message}")
        } finally {
            ctx.taskChannel.close() // 扫描结束，关闭管道，通知 Processor 准备收尾
            LogUtils.info("Scanner 扫描阶段结束，总计下发: ${ctx.totalScanned.get()} 个任务")
        }
    }

    /**
     * 构建并下发任务
     */
    private suspend fun processFileTask(file: File) {
        val fullPath = file.absolutePath

        // A. 提取受试者原始编号 (严格遵守原正则，带路径边界)
        val originSubjectCode = fullPath.replace(
            Settings.ORIGIN_SUBJECT_CODE_REPLACE_REGEX,
            Settings.ORIGIN_SUBJECT_CODE_REPLACE_DST
        )

        // B. 从 CodeManager 获取完整的 CodeModule (包含所有映射关系)
        val codeModule = CodeManager.INSTANCE[originSubjectCode]
        if (codeModule == null) {
            // 【关键逻辑】：记录审计日志，标记为“未匹配丢弃”
            LogUtils.errNoPrint("【未匹配】受试者编号[$originSubjectCode]不在对照表中。路径: $fullPath")
            ctx.stats.subjectIgnored.incrementAndGet() // 统计忽略数
            return
        }
        // C. 计算目标相对路径 (保持原目录结构，但替换受试者一级目录)
        val targetRelativePath = calculateTargetPath(fullPath, codeModule.desensitizedSubjectCode)

        // D. 检测是否为 DW 序列 (正则匹配)
        val isDW = fullPath.matches(Settings.DICOM_DW_DIR_VALID_REGEX)
        var lowercaseFilename = file.name.lowercase()
        if (lowercaseFilename.endsWith(".jpg") ||
            lowercaseFilename.endsWith(".bmp") ||
            lowercaseFilename.endsWith(".jpeg") ||
            lowercaseFilename.endsWith(".pdf") ||
            lowercaseFilename.endsWith(".zip") ||
            lowercaseFilename.endsWith(".rar") ||
            lowercaseFilename.endsWith(".7z")
        ) {
            ctx.stats.fileIgnored.incrementAndGet()
            return
        }

        // E. 组装并发送到 Channel (此处产生背压)
        val task = DicomTask(
            originFile = file,
            codeModule = codeModule,
            targetRelativePath = targetRelativePath,
            isDW = isDW
        )

        ctx.taskChannel.send(task)
        ctx.totalScanned.incrementAndGet()
    }

    private fun isValidDirectory(path: String): Boolean {
        return path.matches(Settings.SUBJECT_DIR_VALID_REGEX) ||
                path.matches(Settings.DICOM_DW_DIR_VALID_REGEX) ||
                path.matches(Settings.DICOM_OTHER_DIRS_VALID_REGEX) ||
                path == rootDir.absolutePath // 根目录特许通过
    }

    private fun calculateTargetPath(fullPath: String, desCode: String): String {
        // 实现 1.0 中的路径替换逻辑：
        // 找到受试者那一级目录并替换为脱敏后的 ID
        val regexStr = Settings.ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_REGEX_STR
            .replace("@originSubjectCode", ".*") // 宽泛匹配该级目录
        val dstStr = Settings.ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_DST_STR
            .replace("@desensitizedSubjectCode", desCode)

        // 这里需要将 fullPath 转换为相对于输出根目录的路径
        // ... (保持原有的路径替换算法)
        return "" // 示例返回
    }
// 在 SedaScanner.kt 中的逻辑修正

    private suspend fun processSubjectDirectory(dir: File) {
        val path = dir.absolutePath

        // 提取受试者编号
        val originCode =
            path.replace(Settings.ORIGIN_SUBJECT_CODE_REPLACE_REGEX, Settings.ORIGIN_SUBJECT_CODE_REPLACE_DST)

        // 逻辑判定：
        val codeModule = CodeManager.INSTANCE[originCode]

        if (codeModule == null) {
            // 判定 1：受试者级别忽略
            ctx.stats.subjectIgnored.incrementAndGet()
            LogUtils.infoNoPrint("【受试者忽略】编号[$originCode]未在字典找到，路径: $path")
            return // 整个目录不再深入扫描
        }

        // 判定 2：受试者识别成功，深入扫描文件
        // 这里我们可以先暂存状态，或者在所有文件处理完后增加 subjectSuccess
    }
}