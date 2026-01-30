package top.elune.utils.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.elune.utils.commons.CodeManager
import top.elune.utils.commons.CodeModule
import top.elune.utils.commons.SedaContext
import top.elune.utils.commons.Settings
import top.elune.utils.utils.LogUtils
import java.io.File
import java.util.*

class SedaScanner(private val ctx: SedaContext) {

    private val rootDir = File(ctx.config.inputPath)

    fun start() = ctx.engineScope.launch(Dispatchers.IO) {
        val queue: Deque<File> = ArrayDeque()
        queue.add(rootDir)

        try {
            while (queue.isNotEmpty() && isActive) {
                val currentFolder = queue.poll() ?: continue

                // 解决 Windows FileSystemException
                val children = currentFolder.listFiles()
                if (children == null) {
                    LogUtils.errNoPrint("【扫描跳过】目录无法读取或无权限: ${currentFolder.absolutePath}")
                    continue
                }

                for (child in children) {
                    val path = child.absolutePath
                    if (child.isDirectory) {
                        // 目录判定
                        if (isProjectRelevantDir(path)) {
                            queue.add(child)
                        }
                    } else {
                        // 文件判定：排除非法后缀
                        if (checkIllegalFile(path)) continue
                        // 执行分发逻辑
                        inspectAndDispatch(child)
                    }
                }
            }
        } finally {
            ctx.taskChannel.close()
            LogUtils.info("Scanner 扫描阶段结束，总计下发任务: ${ctx.stats.fileScanned.get()}")
        }
    }

    private fun checkIllegalFile(srcStr: String): Boolean {
        return if (srcStr.matches("^.*\\.nii.gz$".toRegex(RegexOption.IGNORE_CASE))) {
            LogUtils.err(IllegalArgumentException("File $srcStr is NIFITI"))
            true
        } else if (srcStr.matches("^.*\\.json$".toRegex(RegexOption.IGNORE_CASE))) {
            LogUtils.err(IllegalArgumentException("File $srcStr is JSON"))
            true
        } else if (srcStr.matches("^.*\\.jpge?$".toRegex(RegexOption.IGNORE_CASE))) {
            LogUtils.err(IllegalArgumentException("File $srcStr is Picture"))
            true
        } else if (srcStr.matches("^.*\\.bmp$".toRegex(RegexOption.IGNORE_CASE))) {
            LogUtils.err(IllegalArgumentException("File $srcStr is Picture"))
            true
        } else {
            false
        }
    }

    private suspend fun inspectAndDispatch(file: File) {
        val path = file.absolutePath

        // 1. 提取受试者编号 (保留路径边界校验逻辑)
        val originCode = path.replace(
            Settings.ORIGIN_SUBJECT_CODE_REPLACE_REGEX,
            Settings.ORIGIN_SUBJECT_CODE_REPLACE_DST
        )

        // 2. 匹配字典
        val codeModule = CodeManager.INSTANCE[originCode]
        if (codeModule == null) {
            LogUtils.errNoPrint("【受试者编号未匹配】受试者编号[$originCode]未在字典中找到。路径: $path")
            ctx.stats.subjectIgnored.incrementAndGet()
            ctx.stats.fileIgnored.incrementAndGet()
            return
        }
        // 4. 判定 DW 序列合规性
        val isDW = path.matches(Settings.DICOM_DW_DIR_VALID_REGEX)

        // 3. 预计算目标路径 (与 1.0 逻辑对齐)
        val targetRelPath = calculateRelPath(path, codeModule)

        // 5. 下发任务包
        ctx.taskChannel.send(DicomTask(file, codeModule, targetRelPath, isDW))
        ctx.stats.fileScanned.incrementAndGet()
    }

    private fun isProjectRelevantDir(path: String): Boolean {
        return when {
            path.matches(Settings.SUBJECT_DIR_VALID_REGEX) -> {
                LogUtils.infoNoPrint("Current Subject Dir: %s", path)
                true
            }
            path.matches(Settings.DICOM_DW_DIR_VALID_REGEX) -> {
                LogUtils.debugNoPrint("DICOM DW Dir: %s", path)
                true
            }
            path.matches(Settings.DICOM_OTHER_DIRS_VALID_REGEX) -> {
                LogUtils.debugNoPrint("DICOM Other Dir: %s", path)
                true
            }
            path == rootDir.absolutePath -> true // 根目录放行
            else -> {
                LogUtils.errNoPrint("【未匹配目录跳过】路径: %s", path)
                false
            }
        }
    }

    /**
     * 实现与 1.0 对齐的路径替换逻辑，并支持 CodeModule 中的多字段占位符
     * @param fullPath 原始影像的绝对路径
     * @param codeModule 匹配到的受试者对照信息
     */
    private fun calculateRelPath(fullPath: String, codeModule: CodeModule): String {
        // 1. 获取配置中的原始正则字符串和目标替换字符串
        var finalRegexStr = Settings.ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_REGEX_STR
        var finalDstStr = Settings.ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_DST_STR

        // 2. 定义占位符映射表 (未来可以轻松在这里增加 sideCode, vSiteCode 等)
        val placeholderMap = mapOf(
            "@originSubjectNumber" to codeModule.originSubjectNumber,
            "@desensitizedSubjectNumber" to codeModule.desensitizedSubjectNumber,
            "@originSiteCode" to codeModule.originSiteCode,
            "@desensitizedSiteCode" to codeModule.desensitizedSiteCode,
            "@originSubjectCode" to codeModule.originSubjectCode,
            "@desensitizedSubjectCode" to codeModule.desensitizedSubjectCode
            // 如果 CodeModule 后续增加了 siteCode 或 vSiteCode，只需在这里追加一行：
            // "@vSiteCode" to codeModule.vSiteCode
        )

        // 3. 执行占位符替换，构建真正的正则与目标值
        placeholderMap.forEach { (placeholder, value) ->
            // 注意：在 Regex 中使用的值必须转义，防止编号中包含特殊字符（如 . + ? 等）导致正则崩溃
            finalRegexStr = finalRegexStr.replace(placeholder, Regex.escape(value))
            // 目标字符串是普通文本替换，直接 replace 即可
            finalDstStr = finalDstStr.replace(placeholder, value)
        }

        // 4. 执行脱敏路径替换（按照 1.0 的逻辑，仅替换第一处匹配，即受试者级目录）
        val desensitizedFullPath = try {
            fullPath.replaceFirst(finalRegexStr.toRegex(), finalDstStr)
        } catch (e: Exception) {
            LogUtils.err("路径正则替换失败! Regex: $finalRegexStr, Path: $fullPath")
            fullPath // 降级处理：如果替换失败，保持原路径结构（或根据业务抛出异常）
        }

        // 5. 提取相对于输入根目录的相对路径
        // 这是 SEDA 2.0 Writer 能够跨存储（NTFS/NFS）保持一致性的关键
        val inputRootPrefix = rootDir.absolutePath

        // 兼容处理：确保前缀匹配逻辑不受 Windows/Linux 路径斜杠差异影响
        return desensitizedFullPath.removePrefix(inputRootPrefix).trimStart(File.separatorChar, '/')
    }
}