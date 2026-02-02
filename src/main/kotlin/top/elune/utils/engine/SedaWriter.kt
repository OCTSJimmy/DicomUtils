package top.elune.utils.engine

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import top.elune.utils.commons.SedaContext
import top.elune.utils.dicom.OriginDicomData
import top.elune.utils.utils.LogUtils
import java.io.File
import java.io.FileOutputStream

class SedaWriter(private val ctx: SedaContext) {
    // 在 SedaWriter 中补充审计写入逻辑
    private val auditLogChannel = Channel<OriginDicomData>(2000)

    fun start() {
        // 启动主分发协程，消费 Processor 产出的成品
        startAuditLogger()
        ctx.engineScope.launch {
            writerDispatcherLoop()
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


    private suspend fun writerDispatcherLoop() {
        for (result in ctx.writeChannel) {
            ctx.writeQueuePending.decrementAndGet() // 离开积压队列

            if (!result.isSuccess) {
                ctx.stats.fileError.incrementAndGet() // 处理环节报错的文件
                continue
            }

            // 使用 coroutineScope 确保两路异步任务在本轮循环中被追踪
            coroutineScope {
                // 1. 第一路：主路 NTFS (严格限流模式)
                val ntfsJob = launch(ctx.ntfsDispatcher) {
                    val ok = writeToFile(result, ctx.config.ntfsOutputPath, "NTFS")
                    if (ok) ctx.stats.fileSuccess.incrementAndGet() // 主路：成功
                    else ctx.stats.fileError.incrementAndGet()       // 主路：失败

                    // 主路成功后，可以在此处触发审计日志写入 SAS 盘逻辑
                    if (ok) saveAuditLog(result)
                }

                // 2. 第二路：副路 NFS (高并发模式)
                val nfsJob = if (ctx.config.nfsOutputPath != null) {
                    launch(Dispatchers.IO) { // NFS 相对稳健，直接使用 IO 调度器
                        val ok = writeToFile(result, ctx.config.nfsOutputPath!!, "NFS")
                        if (ok) ctx.stats.backupSuccess.incrementAndGet()
                        else ctx.stats.backupError.incrementAndGet()
                    }
                } else null

                // 3. 等待所有开启的写入任务完成
                joinAll(*listOfNotNull(ntfsJob, nfsJob).toTypedArray())
            }

            // 4. 【内存释放哨兵】：两路均确认写完，释放 ByteArray 占用的内存
            result.cleanup()
        }
        LogUtils.info("SedaWriter: 管道关闭，所有文件写入尝试结束。")
    }

    /**
     * 执行具体的二进制写入操作
     */
    private fun writeToFile(result: ProcessedResult, baseRoot: String, label: String): Boolean {
        val data = result.data ?: return false
        var targetFile = File(baseRoot, result.targetRelativePath)
        if (!targetFile.endsWith(".dcm")) {
            targetFile = File(targetFile.parentFile, "${targetFile.name}.dcm")
        }
        return try {
            // 自动创建脱敏后的层级目录
            if (!targetFile.parentFile.exists()) {
                targetFile.parentFile.mkdirs()
            }

            FileOutputStream(targetFile).use { fos ->
                fos.write(data)
                fos.flush()
            }
            LogUtils.debugNoPrint("【写入成功-$label】: ${result.originFile.absolutePath}%n ->  ${targetFile.absolutePath}")
            true
        } catch (e: Exception) {
            LogUtils.errNoPrint("【写入失败-$label】目标: ${result.originFile.absolutePath}%n -> ${targetFile.absolutePath}, 原因: ${e.message}")
            false
        }
    }
}