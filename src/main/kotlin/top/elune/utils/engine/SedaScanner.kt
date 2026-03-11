package top.elune.utils.engine

import top.elune.utils.commons.CodeManager
import top.elune.utils.commons.CodeModule
import top.elune.utils.commons.SedaContext
import top.elune.utils.commons.Settings
import top.elune.utils.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.io.DicomInputStream
import org.jetbrains.kotlin.com.google.common.util.concurrent.RateLimiter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class SedaScanner(private val ctx: SedaContext) {

    private val rootDir = File(ctx.config.inputPath)
    private val trackedSubjects = ConcurrentHashMap.newKeySet<String>()
    private val dicomParserDispatcher = Dispatchers.IO.limitedParallelism(8)

    fun start() = ctx.engineScope.launch(Dispatchers.IO) {
        val queue: Deque<File> = ArrayDeque()
        queue.add(rootDir)
        var lastLogTime = System.currentTimeMillis()
        var scannedDirCount = 0L
        try {
            while (queue.isNotEmpty() && isActive) {
                val currentFolder = queue.poll() ?: continue
                // === 修改点 2: 心跳日志逻辑 ===
                scannedDirCount++
                val now = System.currentTimeMillis()
                if (now - lastLogTime > ctx.config.logIntervalMs) {
                    LogUtils.info(
                        "【扫描中...】积压目录: ${queue.size} | 已扫目录: $scannedDirCount | " +
                                "已发现影像: ${ctx.stats.fileScanned.get()} | " + "当前位置: .../${currentFolder.name}"
                        // 注意：只打印文件夹名或截断路径，避免路径过长刷屏
                    )
                    lastLogTime = now
                }
                // 解决 Windows FileSystemException
                val children: ArrayList<File> = ArrayList<File>()

                Files.list(currentFolder.toPath()).use { stream ->
                    stream.limit(50000).forEach { path -> children.add(path.toFile()) }
                }

                if (children.isEmpty()) {
                    LogUtils.errNoPrint("【扫描跳过】目录无法读取或无权限: ${currentFolder.absolutePath}")
                    continue
                }
                val rateLimiter = RateLimiter.create(ctx.config.scanIOPS)
                for (child in children) {
                    val path = child.absolutePath
                    // 1. 【纯内存计算】：极速前置过滤（0 IOPS，不碰底层存储）
                    val isPotentiallyRelevantDir = isProjectRelevantDir(path)
                    val isIllegalFile = checkIllegalFile(path)

                    // 核心优化点：如果它既不是我们要找的目录类型，又是一个非法的/要排除的文件名
                    // 那么不管它实际上是文件还是目录，我们都不关心！直接丢弃！
                    if (!isPotentiallyRelevantDir && isIllegalFile) {
                        continue // 完美短路！这节约了一次极其昂贵的 GlusterFS IO 调用
                    }

                    rateLimiter.acquire()
                    if (child.isDirectory) {
                        // 目录判定
                        if (isPotentiallyRelevantDir) {
                            queue.add(child)
                        }
                    } else {
                        // 文件判定：排除非法后缀
                        if (isIllegalFile) continue
                        // 执行分发逻辑
                        inspectAndDispatch(child, children.size)
                    }
                }
            }
        } finally {
            ctx.taskChannel.close()
            LogUtils.info("Scanner 扫描阶段结束，总计下发任务: ${ctx.stats.fileScanned.get()}")
        }
    }

    private fun checkIllegalFile(srcStr: String): Boolean {
        val lowerStr = srcStr.lowercase()
        return when {
            lowerStr.endsWith(".nii.gz") -> {
                LogUtils.errNoPrint("File $srcStr is NIFITI")
                true
            }

            lowerStr.endsWith(".json") -> {
                LogUtils.errNoPrint("File $srcStr is JSON")
                true
            }

            lowerStr.endsWith(".jpg") || lowerStr.endsWith(".jpeg") || lowerStr.endsWith(".bmp") -> {
                LogUtils.errNoPrint("File $srcStr is Picture")
                true
            }

            else -> false
        }
    }

    // SedaScanner.kt 核心埋点逻辑
    private suspend fun inspectAndDispatch(file: File, parentFileCount: Int) {
        val parentPath = file.parentFile.absolutePath
        val originCode = file.absolutePath.replace(
            Settings.ORIGIN_SUBJECT_CODE_REPLACE_REGEX, Settings.ORIGIN_SUBJECT_CODE_REPLACE_DST
        )
        // 1. 【快速失败】如果父目录已被拉黑，直接跳过 (不需要 IO)
        if (ctx.blacklistedDirs.contains(parentPath)) {
            ctx.stats.fileIgnored.incrementAndGet()
            LogUtils.debugNoPrint("Skip this file at %s with Black List Dirs.", file.absolutePath)
            if (trackedSubjects.add(originCode)) {
                ctx.stats.subjectIgnored.incrementAndGet()
            }
            return
        }
        val codeModule = CodeManager.INSTANCE[originCode]
        if (codeModule == null) {
            ctx.stats.fileError.incrementAndGet()    // 文件类：忽略
            if (trackedSubjects.add(originCode)) {
                ctx.stats.subjectError.incrementAndGet()
            }
            return
        }

        val relPath = calculateRelPath(file.absolutePath, codeModule)
        val isDW = file.absolutePath.matches(Settings.DICOM_DW_DIR_VALID_REGEX)
        if (trackedSubjects.add(originCode)) {
            ctx.stats.subjectSuccess.incrementAndGet()
        }

        // 投递前计数
        ctx.stats.tasksDelivered.incrementAndGet() // 已投递
        ctx.scanQueuePending.incrementAndGet()      // 内存积压+1

        // 这里的 attributes 只有几 KB，完全可以放在内存里流转
        ctx.taskChannel.send(DicomTask(file, codeModule, relPath, isDW, attributes))
        ctx.stats.fileScanned.incrementAndGet()

    }

    private fun isProjectRelevantDir(path: String): Boolean {
        return when {
//            path.matches(Settings.SITE_DIR_VALID_REGEX) -> {
//                LogUtils.infoNoPrint("Current Site Dir: %s", path)
//                true
//            }
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
            LogUtils.err("路径正则替换失败! Regex: $finalRegexStr, Path: $fullPath, because with %n ${e.message}")
            fullPath // 降级处理：如果替换失败，保持原路径结构（或根据业务抛出异常）
        }

        // 5. 提取相对于输入根目录的相对路径
        // 这是 SEDA 2.0 Writer 能够跨存储（NTFS/NFS）保持一致性的关键
        val inputRootPrefix = rootDir.absolutePath

        // 兼容处理：确保前缀匹配逻辑不受 Windows/Linux 路径斜杠差异影响
        return desensitizedFullPath.removePrefix(inputRootPrefix).trimStart(File.separatorChar, '/')
    }
}