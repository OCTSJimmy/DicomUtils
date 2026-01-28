package top.elune.utils.commons

import top.elune.utils.utils.LogUtils
import java.lang.Thread.UncaughtExceptionHandler
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicLong

/**
 * 多线程管理工具
 * 请务必先执行init方法
 *
 * 用于管理线程池，
 * 往线程池中投递子任务，
 * 主线程的任务执行状态监听,
 * 成功执行文件标记，
 * 失败执行文件标记，
 * 已完成受试者标记
 */
class Context {
    companion object {
        public const val POOL_SIZE = 64
        var isRunning = true
        private val sRunningFutureList = Collections.synchronizedList(ArrayList<Future<*>>())
        private val sDoneFutureList = Collections.synchronizedList(ArrayList<Future<*>>())

        @Volatile
        var sExecutorService: ExecutorService = Executors.newWorkStealingPool(POOL_SIZE)
        private var sSuccessFiles = AtomicLong()
        private var sFailureFiles: AtomicLong = AtomicLong()
        private var sIgnoreFiles: AtomicLong = AtomicLong()
        private var sDoneSubjects = AtomicLong()
        private val sIgnoreSubjects = AtomicLong()

        @JvmStatic
        fun getRunningFutureListSize() :Int {return sRunningFutureList.size }
        @JvmStatic
        fun getDoneFutureListSize() :Int {return sDoneFutureList.size }
        @JvmStatic
        fun getSuccessFileCount() :Int {return sSuccessFiles.get().toInt() }
        @JvmStatic
        fun getFailureFileCount() :Int {return sFailureFiles.get().toInt() }
        @JvmStatic
        fun getIgnoreFileCount() :Int {return sIgnoreFiles.get().toInt() }
        @JvmStatic
        fun getDoneSubjectCount() :Int {return sDoneSubjects.get().toInt() }
        @JvmStatic
        fun getIgnoreSubjectCount() :Int {return sIgnoreSubjects.get().toInt() }

        @JvmStatic
        fun init() {
            Thread.currentThread().uncaughtExceptionHandler =
                UncaughtExceptionHandler { t: Thread?, e: Throwable -> LogUtils.err(e) }
            Thread.setDefaultUncaughtExceptionHandler { t, e -> LogUtils.err(e) }
        }

        @JvmStatic
        fun submitSuccessFile(): Long {
            return sSuccessFiles.incrementAndGet()
        }

        @JvmStatic
        fun submitFailureFile(): Long {
            return sFailureFiles.incrementAndGet()
        }

        @JvmStatic
        fun submitIgnoreFile(): Long {
            return sIgnoreFiles.incrementAndGet()
        }

        @JvmStatic
        fun submitDoneSubject(): Long {
            return sDoneSubjects.incrementAndGet()
        }

        @JvmStatic
        fun submitIgnoreSubject(): Long {
            return sIgnoreSubjects.incrementAndGet()
        }

        @JvmStatic
        fun submitIgnoreFile(number: Long): Long {
            return sIgnoreFiles.getAndAdd(number)
        }

        @Suppress("unused")
        @JvmStatic
        fun <T> submit(runnable: Callable<T>, failureCallback: Runnable) {
            val future = sExecutorService.submit {
                try {
                    runnable.call()
                } catch (e: Exception) {
                    failureCallback.run()
                }
            }
            sRunningFutureList.add(future)
        }

        @JvmStatic
        fun <T> submit(runnable: Callable<T>): Future<*> {
            val future = sExecutorService.submit {
                try {
                    runnable.call()
                } catch (e: Exception) {
                    LogUtils.err(e)
                }
            }
            sRunningFutureList.add(future)
            return future
        }

        @JvmOverloads
        fun waitAllFutureDone(actionStr: String = "Move", isRelease: Boolean = true) {

            while (sRunningFutureList.isNotEmpty()) {
                updateRunningFutureList()
                LogUtils.printMainProgress(actionStr,
                    sRunningFutureList.size,
                    sDoneFutureList.size,
                    sDoneSubjects.get().toInt(),
                    sIgnoreSubjects.get().toInt(),
                    sSuccessFiles.get().toInt(),
                    sFailureFiles.get().toInt(),
                    sIgnoreFiles.get().toInt())

                try {
                    Thread.sleep(5000)
                } catch (e: InterruptedException) {
                    LogUtils.err(e)
                }
            }
            LogUtils.err("Application Finish.")
            LogUtils.infoNoPrint("Application Finish.")
            LogUtils.debugNoPrint("Application Finish.")
            LogUtils.logNoPrint("Application Finish.")
            if (isRelease)
                LogUtils.release()
        }
        @JvmStatic
        fun updateRunningFutureList() {
            for (i in sRunningFutureList.indices.reversed()) {
                val future = sRunningFutureList[i]
                if (future.isDone || future.isCancelled) {
        /*                        if(future.isDone) {
                                    LogUtils.debug("Done Thread %s .%n", future.toString());
                                } else {
                                    LogUtils.debug("Canceled Thread %s .%n", future.toString());
                                }*/
                    sRunningFutureList.remove(future)
                    sDoneFutureList.add(future)
                } else {
        //                    LogUtils.log("Running Thread %s .%n", future.toString());
                }
            }
        }

        @JvmStatic
        fun futureCheckClear() {
            sRunningFutureList.clear()
            sDoneFutureList.clear()
            sFailureFiles.set(0)
            sSuccessFiles.set(0)
        }
    }

}