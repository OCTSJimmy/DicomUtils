package top.elune.utils.commons

import java.io.File

/**
 * 代表一个待处理的 DICOM 文件任务
 */
data class DicomTask(
    val originFile: File,
    val codeModule: CodeModule,       // 包含原始/虚拟编号、分中心等所有映射信息
    val targetRelativePath: String,   // 预计算的目标存储相对路径
    val isDW: Boolean = false         // 标记是否为 DW 序列目录下的影像
)