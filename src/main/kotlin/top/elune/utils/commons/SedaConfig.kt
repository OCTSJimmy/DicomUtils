package top.elune.utils.commons

/**
 * 2.0 核心配置：定义物理边界
 */
data class SedaConfig(
    val inputPath: String,
    val ntfsOutputPath: String,
    val nfsOutputPath: String?,

    // 背压阈值：根据内存情况调整，1000个小文件任务大约占用几百MB内存
    val scanQueueSize: Int = 5000,
    val processQueueSize: Int = 1000,

    // 调度器并发限制
    val ntfsWriterParallelism: Int = 2, // 限制 NTFS 写入并发，防止卡死
    val nfsWriterParallelism: Int = 8,  // NFS 相对稳健
    val cpuParallelism: Int = Runtime.getRuntime().availableProcessors()
)