package top.elune.utils.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import top.elune.utils.commons.SedaContext
import top.elune.utils.dicom.CustomDicomInputStream
import top.elune.utils.dicom.CustomDicomOutputStream
import top.elune.utils.dicom.OriginDicomData
import top.elune.utils.utils.LogUtils
import java.io.*

class SedaWriter(private val ctx: SedaContext) {
    // 在 SedaWriter 中补充审计写入逻辑
    private val auditLogChannel = Channel<OriginDicomData>(2000)


    fun start() {
        // 启动主分发协程，消费 Processor 产出的成品
        startAuditLogger()
        // ==========================================
        // 【修正 1】：启动多工位并发池 (Worker Pool)
        // 这里的并发度决定了同时有多少个文件在进行双路对齐写入
        // ==========================================
        val workerCount = ctx.config.ntfsWriterParallelism
        LogUtils.info("SedaWriter: 启动 $workerCount 个写入并发器...")
        repeat(workerCount) { workerId ->
            ctx.engineScope.launch(ctx.ntfsDispatcher) {
                writerDispatcherLoop(workerId)
            }
        }
    }

    private fun startAuditLogger() {
        ctx.engineScope.launch(Dispatchers.IO) {
            // 打开 SAS 盘上的 CSV 文件流
            val auditFile = File(ctx.config.logPath, "mapping_audit_${System.currentTimeMillis()}.csv")
            auditFile.bufferedWriter().use { writer ->
                // 写入 CSV 表头
                writer.write("OriginPath,PatientID,DesID,StudyDate,...\n")
                for (data in auditLogChannel) {
                    // 将 OriginDicomData 序列化为 CSV 行
                    writer.write("${data.toCsvLine()}\n")
                }
            }
        }
    }

    // 在 saveAuditLog 中调用
    private suspend fun saveAuditLog(result: ProcessedResult) {
        result.originDicomData?.let {
            auditLogChannel.send(it)
        }
    }


    // 在 SedaWriter 类中重写分发器
    private suspend fun writerDispatcherLoop(workerId: Int) {
        for (result in ctx.writeChannel) {
            ctx.writeQueuePending.decrementAndGet()
            if (!result.isSuccess) {
                ctx.stats.fileError.incrementAndGet()
                continue
            }

            try {
                // 执行一次“三通管”双写
                val (ntfsStatus, nfsStatus) = writeToDualTarget(result)

                // 统一统计主路
                when (ntfsStatus) {
                    0 -> ctx.stats.fileError.incrementAndGet()
                    2 -> ctx.stats.fileIgnored.incrementAndGet()
                    1 -> {
                        ctx.stats.fileSuccess.incrementAndGet()
                        saveAuditLog(result)
                    }
                }

                // 统一统计辅路
                if (ctx.config.nfsOutputPath != null && ctx.config.nfsOutputPath.isNotBlank()) {
                    when (nfsStatus) {
                        0 -> ctx.stats.backupError.incrementAndGet()
                        1 -> ctx.stats.backupSuccess.incrementAndGet()
                    }
                }
            } finally {
                result.cleanup()
            }
        }
    }

    /**
     * 终极流式双写：一读两写，绝不缓存，绝不嵌套
     * 返回值：Pair<主路状态, 辅路状态> (0:失败, 1:成功, 2:跳过, -1:未开启)
     */
    private fun writeToDualTarget(result: ProcessedResult): Pair<Int, Int> {
        val bufferSize = 256 * 1024

        // 1. 路径和幂等性准备
        val ntfsFile = resolveTargetFile(ctx.config.ntfsOutputPath, result.targetRelativePath)
        val nfsFile = ctx.config.nfsOutputPath?.let { resolveTargetFile(it, result.targetRelativePath) }

        var ntfsStatus = checkIdempotency(ntfsFile) // 返回 2 表示存在，0 表示需要写
        var nfsStatus = nfsFile?.let { checkIdempotency(it) } ?: -1

        // 如果两路都不需要写（要么已存在，要么未配置），直接短路返回
        if (ntfsStatus != 0 && (nfsStatus != 0)) {
            return Pair(ntfsStatus, nfsStatus)
        }

        try {
            // ==========================================
            // 【黑科技 1：构造链消除嵌套】
            // 一行代码打开 源文件 -> 大缓冲 -> Dicom流，只需要一个 use！
            // ==========================================
            CustomDicomInputStream(BufferedInputStream(FileInputStream(result.originFile), bufferSize)).use { dis ->

                val fmi = dis.readFileMetaInformation()
                dis.readDatasetUntilPixelData()
//                val tsuid = fmi?.getString(org.dcm4che3.data.Tag.TransferSyntaxUID) ?: org.dcm4che3.data.UID.ExplicitVRLittleEndian
                val tsuid = org.dcm4che3.data.UID.ExplicitVRLittleEndian

                // 2. 准备物理输出流 (谁需要写，就打开谁)
                val outNtfs =
                    if (ntfsStatus == 0) BufferedOutputStream(FileOutputStream(ntfsFile), bufferSize) else null
                val outNfs =
                    if (nfsStatus == 0) BufferedOutputStream(FileOutputStream(nfsFile!!), bufferSize) else null

                // ==========================================
                // 【黑科技 2：三通管双路写】
                // 把两个流塞进三通管，包装成唯一的输出流
                // ==========================================
                DualOutputStream(outNtfs, outNfs).use { dualOut ->
                    val dos = CustomDicomOutputStream(dualOut, tsuid)

                    dos.writeDataset(fmi, result.attributes)

                    // 【修复逻辑】判断当前流是否停在了 PixelData，如果是，先补写 Header
                    if (dis.tag() == org.dcm4che3.data.Tag.PixelData) {
                        dos.writeHeader(dis.tag(), dis.vr(), dis.length())
                    }

                    dos.flush()

                    // 然后再拷贝后续真正的二进制像素数据
                    dis.copyTo(dos)

                    // 写完后检查两路有没有发生局部异常
                    if (ntfsStatus == 0) ntfsStatus = if (dualOut.mainError == null) 1 else 0
                    if (nfsStatus == 0) nfsStatus = if (dualOut.subError == null) 1 else 0
                }
            }
        } catch (e: Exception) {
            // 全局灾难异常（比如源盘断开了），正在写的那路全部判定为失败，并删残片
            LogUtils.errNoPrint(tips = "【双写整体异常】源: ${result.originFile.absolutePath}", e)
            if (ntfsStatus == 0) {
                ntfsFile.delete(); ntfsStatus = 0
            }
            if (nfsStatus == 0) {
                nfsFile?.delete(); nfsStatus = 0
            }
        }

        return Pair(ntfsStatus, nfsStatus)
    }

    // --- 辅助方法抽离，代码更干净 ---
    private fun resolveTargetFile(base: String, relPath: String): File {
        var f = File(base, relPath)
        if (!f.name.endsWith(".dcm", ignoreCase = true)) f = File(f.parentFile, "${f.name}.dcm")
        if (!f.parentFile.exists()) f.parentFile.mkdirs()
        return f
    }

    private fun checkIdempotency(f: File): Int {
        return if (f.exists() && f.length() > 0) {
            LogUtils.infoNoPrint("Skip, because found dst %s is exists.", f.absolutePath)
            2
        } else {
            0
        }
    }
}


/**
 * 流式广播器 (三通管)：一次 write，双路落盘
 */
class DualOutputStream(
    private val mainOut: OutputStream?,
    private val subOut: OutputStream?
) : OutputStream() {
    var mainError: Exception? = null
    var subError: Exception? = null

    override fun write(b: Int) {
        if (mainOut != null && mainError == null) try {
            mainOut.write(b)
        } catch (e: Exception) {
            mainError = e
        }
        if (subOut != null && subError == null) try {
            subOut.write(b)
        } catch (e: Exception) {
            subError = e
        }
        checkAllDead()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        // 核心：一份数据，同时写给两路
        if (mainOut != null && mainError == null) try {
            mainOut.write(b, off, len)
        } catch (e: Exception) {
            mainError = e
        }
        if (subOut != null && subError == null) try {
            subOut.write(b, off, len)
        } catch (e: Exception) {
            subError = e
        }
        checkAllDead()
    }

    override fun flush() {
        if (mainOut != null && mainError == null) try {
            mainOut.flush()
        } catch (e: Exception) {
            mainError = e
        }
        if (subOut != null && subError == null) try {
            subOut.flush()
        } catch (e: Exception) {
            subError = e
        }
    }

    override fun close() {
        // 安全关闭两路，互不影响
        try {
            mainOut?.close()
        } catch (e: Exception) {
            mainError = e
        }
        try {
            subOut?.close()
        } catch (e: Exception) {
            subError = e
        }
    }

    private fun checkAllDead() {
        val mainDead = mainOut == null || mainError != null
        val subDead = subOut == null || subError != null
        if (mainDead && subDead) throw IOException("双路输出均已断开或异常")
    }
}