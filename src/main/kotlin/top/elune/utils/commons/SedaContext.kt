package top.elune.utils.commons

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * SEDA 运行环境：持有调度器和生命周期
 */
class SedaContext(val config: SedaConfig) : AutoCloseable {
    // 1. 引擎主作用域：负责管理所有阶段（Stage）的协程
    val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 2. 隔离调度器：专门用于 NTFS，防止其 IO 阻塞影响其他任务
    val ntfsDispatcher = Executors.newFixedThreadPool(config.ntfsWriterParallelism)
        .asCoroutineDispatcher()
    // 在 SedaContext 类定义中增加：
    val taskChannel = Channel<DicomTask>(config.scanQueueSize)
    val writeChannel = Channel<ProcessedResult>(config.processQueueSize) // 后续处理完的结果

    val totalScanned = AtomicLong(0)
    val stats = SedaStats()
    // 4. 资源回收
    override fun close() {
        engineScope.cancel()
        (ntfsDispatcher.executor as java.util.concurrent.ExecutorService).shutdown()
    }
}