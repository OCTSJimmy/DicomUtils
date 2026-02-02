package top.elune.utils.utils

import org.apache.commons.io.output.WriterOutputStream
import top.elune.utils.commons.Settings
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.system.exitProcess

@Suppress("unused")
object LogUtils {
    var SDF = SimpleDateFormat("yyyy-MM-dd_HH_mm_ss")
    lateinit var logCurrentPath: File
    private lateinit var LOG_FILE: File
    private lateinit var LOG_DEBUG_FILE: File
    private lateinit var LOG_ERR_FILE: File
    private lateinit var LOG_INFO_FILE: File
    private var sLogDebugBw: BufferedWriter? = null
    private var sLogBw: BufferedWriter? = null
    private var sLogErrBw: BufferedWriter? = null
    private var sLogInfoBw: BufferedWriter? = null

    // 1. 定义日志类型枚举
    private enum class LogType {
        LOG, DEBUG, INFO, ERROR
    }

    // 2. 定义日志条目数据类（包含内容和类型）
    private data class LogEntry(val content: String, val type: LogType, val shouldPrint: Boolean)

    // 3. 创建阻塞队列（生产者-消费者模型的核心）
    private val logQueue = LinkedBlockingQueue<LogEntry>()

    // 4. 消费者线程引用
    private var logDaemonThread: Thread? = null

    // 5. 错误日志专用锁（对应您之前提到的防止 Throwable 堆栈混杂的逻辑）
    private val errorFileLock = ReentrantLock()

    private fun logWriterTask() {
        while (true) {
            try {
                val entry = logQueue.take()

                if (entry.type == LogType.ERROR) {
                    errorFileLock.withLock {
                        writeEntryToFile(entry)
                        // 只有标记为应打印时才输出到控制台
                        if (entry.shouldPrint) {
                            System.err.println(entry.content)
                        }
                    }
                } else {
                    writeEntryToFile(entry)
                    if (entry.shouldPrint) {
                        println(entry.content)
                    }
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                System.err.println("LogDaemonThread 发生异常: ${e.message}")
            }
        }
    }

    // 辅助方法：仅负责写入文件，不负责打印
    private fun writeEntryToFile(entry: LogEntry) {
        val writer = when (entry.type) {
            LogType.LOG -> sLogBw
            LogType.DEBUG -> sLogDebugBw
            LogType.INFO -> sLogInfoBw
            LogType.ERROR -> sLogErrBw
        }
        writer?.let {
            it.write(entry.content)
            it.newLine()
            it.flush()
        }
    }

    @JvmStatic
    fun init(logPath: String = Settings.LOG_PATH) {
        try {
            logCurrentPath = File(logPath, SDF.format(System.currentTimeMillis()))
            LOG_FILE = File(logCurrentPath, SDF.format(System.currentTimeMillis()) + ".log")
            LOG_DEBUG_FILE = File(logCurrentPath, SDF.format(System.currentTimeMillis()) + "_debug.log")
            LOG_ERR_FILE = File(logCurrentPath, SDF.format(System.currentTimeMillis()) + ".err")
            LOG_INFO_FILE = File(logCurrentPath, SDF.format(System.currentTimeMillis()) + "_info.log")
            if (!logCurrentPath.exists()) {
                logCurrentPath.mkdirs()
            }
            sLogBw = BufferedWriter(FileWriter(LOG_FILE))
            sLogErrBw = BufferedWriter(FileWriter(LOG_ERR_FILE))
            sLogInfoBw = BufferedWriter(FileWriter(LOG_INFO_FILE))
            sLogDebugBw = BufferedWriter(FileWriter(LOG_DEBUG_FILE))
        } catch (e: IOException) {
            System.err.println("日志文件初始化失败")
            exitProcess(1)
        }
        // 启动异步写入守护线程
        if (logDaemonThread == null || !logDaemonThread!!.isAlive) {
            logDaemonThread = Thread(::logWriterTask, "LogDaemon-Thread").apply {
                isDaemon = true // 守护线程，随主线程退出
                start()
            }
        }
    }

    fun printMainProgress(actionStr: String,
                          runningSize : Int,
                          doneSize : Int,
                          doneSubject:Int,
                          ignoreSubject:Int,
                          successFile:Int,
                          failureFile:Int,
                          ignoreFile:Int) {
        log(
            "Current Time: %s",
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSSSSS").format(System.currentTimeMillis())
        )
        log("Running Thread is %s .", runningSize.toString())
        log("Done/Cancel Thread is %s .", doneSize.toString())
        log("Done Subjects is %d .", doneSubject)
        log("Ignore Subjects is %d .", ignoreSubject)
        log("Success $actionStr file is %d .", successFile)
        log("Failure $actionStr file is %d .", failureFile)
        log("Ignore $actionStr file is %d .%n", ignoreFile)
    }

    fun createLogs(vararg dirs: String?): File {
        val pathDir = Paths.get(logCurrentPath.absolutePath, *dirs)
        pathDir.toFile().mkdirs()
        val length = dirs.size + 1
        val exDirs = arrayOfNulls<String>(length)
        System.arraycopy(dirs, 0, exDirs, 0, dirs.size)
        exDirs[length - 1] = SDF.format(System.currentTimeMillis()) + ".log"
        val path = Paths.get(logCurrentPath.absolutePath, *exDirs)
        return path.toFile()
    }

    fun createError(vararg dirs: String?): File {
        val pathDir = Paths.get(logCurrentPath.absolutePath, *dirs)
        pathDir.toFile().mkdirs()
        val length = dirs.size + 1
        val exDirs = arrayOfNulls<String>(length)
        System.arraycopy(dirs, 0, exDirs, 0, dirs.size)
        exDirs[length - 1] = SDF.format(System.currentTimeMillis()) + ".err"
        val path = Paths.get(logCurrentPath.absolutePath, *exDirs)
        return path.toFile()
    }

    @JvmStatic
    fun logNoPrint(str: String) {
        logNoPrint(str, "")
    }

    @JvmStatic
    fun logNoPrint(format: String, vararg content: Any?) {
        log(format, false, *content)
    }

    @JvmStatic
    fun log(str: String) {
        log(str, "")
    }

    @JvmStatic
    fun log(format: String, vararg content: Any?) {
        log(format, true, *content)
    }

    @JvmStatic
    fun log(format: String, shouldPrint: Boolean, vararg content: Any?) {
        val str = String.format("thread:${Thread.currentThread().name}, $format", *content)
        logQueue.put(LogEntry(str, LogType.LOG, shouldPrint)) // Print
    }
    @JvmStatic
    fun info(str: String) {
        info(str, "")
    }

    @JvmStatic
    fun info(format: String, vararg content: Any?) {
        info(format, true, *content)
    }

    @JvmStatic
    fun info(format: String, shouldPrint: Boolean, vararg content: Any?) {
        val str = String.format("thread:${Thread.currentThread().name}, $format", *content)
        logQueue.put(LogEntry(str, LogType.INFO, shouldPrint)) // Print
    }

    @JvmStatic
    fun infoNoPrint(str: String) {
        logNoPrint(str, "")
    }

    @JvmStatic
    fun infoNoPrint(format: String, vararg content: Any?) {
        info(format, false, *content)
    }

    fun debug(str: String) {
        debug(str, "")
    }

    @JvmStatic
    fun debug(format: String, shouldPrint: Boolean, vararg content: Any?) {
        val str = String.format("thread:${Thread.currentThread().name}, $format", *content)
        logQueue.put(LogEntry(str, LogType.DEBUG, shouldPrint)) // Print
    }

    @JvmStatic
    fun debug(format: String, vararg content: Any?) {
        debug(format, true, *content)
    }

    @JvmStatic
    fun debugNoPrint(str: String) {
        logNoPrint(str, "")
    }

    @JvmStatic
    fun debugNoPrint(format: String, vararg content: Any?) {
        debug(format, false, *content)
    }

    @JvmStatic
    fun err(err: Throwable) {
        // 微调点 1：生产者线程直接竞争锁。
        // 如果 LogDaemon 正在处理别的 Error 日志，这里会阻塞，直到那边处理完当前行。
        errorFileLock.withLock {
            try {
                // 微调点 2：先对已有的 Buffer 进行冲刷，确保堆栈前没有残留半行日志
                sLogErrBw?.flush()

                // 打印到控制台
                err.printStackTrace()

                // 微调点 3：使用带编码保障的流包装
                val wos = WriterOutputStream(sLogErrBw, StandardCharsets.UTF_8)
                val ps = PrintStream(wos, true, StandardCharsets.UTF_8.name())

                // 写入完整堆栈
                err.printStackTrace(ps)

                // 微调点 4：强制再次冲刷，确保堆栈信息物理落地，不留在内存里
                sLogErrBw?.flush()
            } catch (e: Exception) {
                // 即使堆栈记录失败，也别让日志逻辑把业务主流程搞崩溃
                e.printStackTrace()
            }
        }
    }

    @JvmStatic
    fun err(str: String) {
        err(str, "")
    }

    @JvmStatic
    fun err(format: String, shouldPrint: Boolean, vararg content: Any?) {
        val str = String.format("thread:${Thread.currentThread().name}, $format", *content)
        logQueue.put(LogEntry(str, LogType.ERROR, shouldPrint)) // Print
    }

    @JvmStatic
    fun err(format: String, vararg content: Any?) {
        err(format, true, *content)
    }
    @JvmStatic
    fun errNoPrint(format: String, vararg content: Any?) {
        err(format, false, *content)
    }

    @JvmStatic
    fun release() {
        // 1. 停止接收新日志（可选：可以设置一个 flag）

        // 2. 给守护线程一点时间处理剩余队列
        var waitTime = 0
        while (logQueue.isNotEmpty() && waitTime < 50) { // 最多等待约 5 秒
            Thread.sleep(100)
            waitTime++
        }

        // 3. 中断守护线程并关闭流
        logDaemonThread?.interrupt()
        try {
            sLogBw?.close()
            sLogDebugBw?.close()
            sLogErrBw?.close()
            sLogInfoBw?.close()
        } catch (e: IOException) {
            // ignore
        }
    }
}