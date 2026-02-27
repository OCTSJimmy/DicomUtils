package top.elune.utils.commons

import java.util.concurrent.atomic.AtomicLong

/**
 * 2.0 分层统计指标：支持进程流、受试者与文件三维监控
 */
@Suppress("unused")
class SedaStats {
    // === 进程/线程类信息 ===
// === 进程/线程/任务类信息 ===
    val fileScanned = AtomicLong(0)    // 已发现数量 (Scanner 扫到的所有文件)
    val tasksDelivered = AtomicLong(0) // 已投递任务数量 (进入管道的任务)
    val fileProcessed = AtomicLong(0)  // 已脱敏处理完成数量
    val fileSuccess = AtomicLong(0)    // 已成功任务数量 (主路落地)
    val fileError = AtomicLong(0)      // 已失败任务数量 (主路失败或处理错误)

    // === 受试者类信息 ===
    val subjectSuccess = AtomicLong(0) // 成功完成受试者数 (识别并开始处理)
    val subjectIgnored = AtomicLong(0) // 忽略受试者数 (无字典)
    val subjectError = AtomicLong(0)   // 失败受试者数 (如受试者目录无法读取)

    // === 文件类信息补充 ===
    val fileIgnored = AtomicLong(0)    // 已忽略文件数量 (非影像、正则排除)
    val backupSuccess = AtomicLong(0)  // 副路成功
    val backupError = AtomicLong(0)    // 副路失败

    /**
     * 待完成总数 = 已发现 - (成功 + 失败 + 忽略)
     * 代表“已经知道有这个文件，但还没跑完流程”的存量
     */
    val remainingTotal: Long
        get() = fileScanned.get() - (fileSuccess.get() + fileError.get())

    /**
     * 进度百分比 (基于文件成功率)
     */
    val progress: Double
        get() = if (fileScanned.get() == 0L) 0.0
        else (fileSuccess.get() + fileError.get() + fileIgnored.get()).toDouble() / fileScanned.get() * 100
}