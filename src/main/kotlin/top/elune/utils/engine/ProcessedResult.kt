package top.elune.utils.engine

import top.elune.utils.commons.CodeModule
import top.elune.utils.dicom.OriginDicomData
import java.io.File

/**
 * 脱敏后的成品数据包
 */
@Suppress("unused")
class ProcessedResult(
    val originFile: File,
    val targetRelativePath: String,
    val codeModule: CodeModule,
    var data: ByteArray?,       // 脱敏后的二进制流，写完后需置空
    val originDicomData: OriginDicomData? = null,
    val isSuccess: Boolean = true,
    val errorMsg: String? = null
) {
    fun cleanup() {
        data = null // 显式释放内存，辅助 GC
    }
}