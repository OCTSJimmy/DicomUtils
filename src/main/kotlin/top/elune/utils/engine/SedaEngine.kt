package top.elune.utils.engine

import kotlinx.coroutines.*
import top.elune.utils.commons.SedaContext
import top.elune.utils.utils.LogUtils
import kotlin.system.measureTimeMillis

class SedaEngine(private val ctx: SedaContext) {

    private val scanner = SedaScanner(ctx)
    private val processor = SedaProcessor(ctx)
    private val writer = SedaWriter(ctx)

    fun run() = runBlocking {
        LogUtils.info(">>> SedaEngine 2.0 启动 (稳定版流水线) <<<")

        val timeConsumed = measureTimeMillis {
            // 1. 启动 Writer 协程
            val writerJob = ctx.engineScope.launch {
                writer.start()
            }

            // 2. 启动多个 Processor 协程，并保留它们的 Job 引用
            val processorJobs = (1..ctx.config.cpuParallelism).map {
                ctx.engineScope.launch(Dispatchers.Default) {
                    processor.start() // 修改：让 processor 暴露一个消费循环
                }
            }

            // 3. 启动 Scanner 并等待它扫描完成
            // Scanner 完成后会执行 ctx.taskChannel.close()
            scanner.start().join()

            // 4. 【关键稳定替代】：等待所有 Processor 处理完存量数据并退出
            // 只有当 taskChannel 关闭且所有任务被消费完，processorJobs 才会完成
            processorJobs.joinAll()

            // 5. 此时确定 Processor 不会再产生新数据，安全关闭 writeChannel
            ctx.writeChannel.close()

            // 6. 最后等待 Writer 彻底写完磁盘
            writerJob.join()
        }

        printFinalStats(timeConsumed)
    }

    private fun printFinalStats(timeConsumed: Long) {
        val s = ctx.stats
        LogUtils.info("""
            ================================================
            脱敏任务完成！总耗时: ${timeConsumed / 1000}s
            ------------------------------------------------
            
            ------------------------------------------------
            【主路文件(NTFS)】: 成功: ${s.fileSuccess} | 错误: ${s.fileError}
            【备份路(NFS)】: 成功: ${s.backupSuccess} | 错误: ${s.backupError}
            【统计】: 扫描: ${s.fileScanned} | 脱敏: ${s.fileProcessed} | 忽略: ${s.fileIgnored}
            ================================================
        """.trimIndent())
    }
}