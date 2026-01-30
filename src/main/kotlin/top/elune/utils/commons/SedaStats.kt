package top.elune.utils.commons

import java.util.concurrent.atomic.AtomicLong

/**
 * 2.0 分层统计指标：支持进程流、受试者与文件三维监控
 */
class SedaStats {
    // === 进程/线程类信息 ===
    val fileScanned = AtomicLong(0)    // 已发现数量 (Scanner 扫到的物理文件总数)
    val tasksDelivered = AtomicLong(0) // 已投递任务数量 (进入脱敏管道的任务数)
    val fileProcessed = AtomicLong(0)  // 已处理数量 (CPU 脱敏完成)

    // === 受试者类信息 ===
    val subjectSuccess = AtomicLong(0) // 成功完成受试者数 (Scanner 识别成功)
    val subjectIgnored = AtomicLong(0) // 忽略受试者数 (无字典或路径不合规)
    val subjectError = AtomicLong(0)   // 失败受试者数 (预留，用于严重目录错误)

    // === 文件类信息 ===
    val fileSuccess = AtomicLong(0)    // 主路 (NTFS) 成功数量
    val fileError = AtomicLong(0)      // 主路 (NTFS) 失败数量
    val fileIgnored = AtomicLong(0)    // 已忽略数量 (非影像、跳过文件)

    val backupSuccess = AtomicLong(0)  // 副路 (NFS) 成功数量
    val backupError = AtomicLong(0)    // 副路 (NFS) 失败数量

    /**
     * 待完成总数 = 已发现 - (成功 + 失败 + 忽略)
     * 代表“已经知道有这个文件，但还没跑完流程”的存量
     */
    val remainingTotal: Long
        get() = fileScanned.get() - (fileSuccess.get() + fileError.get() + fileIgnored.get())

    /**
     * 进度百分比 (基于文件成功率)
     */
    val progress: Double
        get() = if (fileScanned.get() == 0L) 0.0
        else (fileSuccess.get() + fileError.get() + fileIgnored.get()).toDouble() / fileScanned.get() * 100
}