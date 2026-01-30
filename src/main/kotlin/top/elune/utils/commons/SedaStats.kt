package top.elune.utils.commons

import java.util.concurrent.atomic.AtomicLong

/**
 * 2.0 分层统计指标
 */
class SedaStats {
    // 受试者维度
    val subjectSuccess = AtomicLong(0) // 成功完成脱敏并写出的受试者
    val subjectError = AtomicLong(0)   // 存在文件处理失败的受试者
    val subjectIgnored = AtomicLong(0) // 路径不合规或不在对照表中的受试者

    // 文件维度
    val fileSuccess = AtomicLong(0)    // 成功写出的影像文件
    val fileError = AtomicLong(0)      // 处理或写入失败的文件
    val fileIgnored = AtomicLong(0)    // 显式跳过（非DCM或非合规目录下的影像）
}