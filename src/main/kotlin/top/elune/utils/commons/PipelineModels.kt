package top.elune.utils.commons

/**
 * 脱敏后的成品数据包
 */
class ProcessedResult(
    val taskId: String,             // 任务唯一ID，用于日志追踪
    val relativePath: String,       // 保持原有的目录结构
    val fileName: String,           // 文件名
    var data: ByteArray?,           // 脱敏后的 DICOM 二进制数据
    val success: Boolean = true,
    val errorMsg: String? = null
) {
    fun clear() {
        data = null // 显式置空，辅助 GC
    }
}