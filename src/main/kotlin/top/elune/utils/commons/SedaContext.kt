package top.elune.utils.commons

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import top.elune.utils.engine.DicomTask
import top.elune.utils.engine.ProcessedResult
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

@Suppress("unused")
class SedaContext(val config: SedaConfig) : AutoCloseable {
    // 调度器与管道
    val ntfsDispatcher = Executors.newFixedThreadPool(config.ntfsWriterParallelism).asCoroutineDispatcher()
    val taskChannel = Channel<DicomTask>(config.scanQueueSize)
    val writeChannel = Channel<ProcessedResult>(config.processQueueSize)

    val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val stats = SedaStats()

    // --- 待完成任务数量 (内存积压监控) ---
    val scanQueuePending = AtomicInteger(0)  // 待脱敏数
    val writeQueuePending = AtomicInteger(0) // 待写入数

    /** 待完成任务数量 (当前驻留在内存中的任务总数) */
    val totalPendingInFlight: Int
        get() = scanQueuePending.get() + writeQueuePending.get()

    /** 待完成总数 (全局视野：已发现 - 已结束) */
    val globalRemaining: Long
        get() = stats.fileScanned.get() - (stats.fileSuccess.get() + stats.fileError.get() + stats.fileIgnored.get())

    override fun close() {
        engineScope.cancel()
        (ntfsDispatcher.executor as java.util.concurrent.ExecutorService).shutdown()
    }
}