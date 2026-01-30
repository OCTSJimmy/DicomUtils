package top.elune.utils

import top.elune.utils.commons.*
import top.elune.utils.dicom.WriteDicomProcessor
import top.elune.utils.utils.LogUtils
import java.io.*
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.function.Function
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name


private val sWriterIgnoreLocker: Any = Any()

fun main(args: Array<String>) {
    Locale.setDefault(Locale.ENGLISH)
    Settings.init()
    LogUtils.init(Settings.LOG_PATH)
    CodeManager.init(Settings.VCODE_CSV_FILE_PATH)
    Context.init()

    val rootPath = Paths.get(Settings.SRC_DICOM_PATH)

    val originDataCsvStream: ThreadLocal<Writer> = ThreadLocal()
    val writeIgnore = setupOriginDataWriter(originDataCsvStream, rootPath)
    val subjectDirFiles = rootPath.toFile().listFiles()
    if (null == subjectDirFiles || subjectDirFiles.isEmpty()) {
        LogUtils.err("Fail to list the path at ${Settings.SRC_DICOM_PATH}")
        return
    }
// 1. 扫描阶段 (IO密集型)
    LogUtils.info("正在扫描 GlusterFS 目录，请稍候...")
    val allSubjectDirs:List<String> = rootPath.toFile().listFiles { f -> f.isDirectory }?.map { it.absolutePath } ?: emptyList()
    val total = allSubjectDirs.size
    LogUtils.info("扫描完成，共发现 $total 个受试者任务。")

    val batchSize = Context.POOL_SIZE * 2
    allSubjectDirs.chunked(batchSize).forEachIndexed { index, subjectsDirStr ->

        // 检查线程池队列积压情况，防止 Future 对象塞爆内存
        // 如果积压超过 200 个任务，主线程就稍微歇一歇
        while (Context.getRunningFutureListSize() > Context.POOL_SIZE * 2) {
            Context.updateRunningFutureList()
            LogUtils.printMainProgress("Copy",
                Context. getRunningFutureListSize(),
                Context.getDoneFutureListSize(),
                Context.getDoneSubjectCount(),
                Context.getIgnoreSubjectCount(),
                Context.getSuccessFileCount(),
                Context.getFailureFileCount(),
                Context.getIgnoreFileCount())
            Thread.sleep(2000)
            LogUtils.infoNoPrint("队列积压中，等待消化... 当前进度: ${index * batchSize}/$total")
        }

        for (subjectDirStr in subjectsDirStr) {
//          if(!entry.value.codeModule.getvSiteCode().equals("XXX")) {
//              continue
//          }
            val subjectPath = Path(subjectDirStr)
            val subjectDirFile = subjectPath.toFile()
            if (!subjectPath.isDirectory()) continue
            if (!subjectDirStr.matches(Settings.SUBJECT_DIR_VALID_REGEX)) {
                LogUtils.err("Fail with $subjectDirStr valid by ${Settings.SUBJECT_DIR_VALID_REGEX}.")
                Context.submitIgnoreSubject()
                continue
            }

            Context.submit {
                Context.init()
                var writer: Writer? = null
                try {
                    val srcSubjectPathStr = subjectDirFile.absolutePath
                    writer = setupOriginDataWriter(originDataCsvStream, subjectPath)
                    fileLoopCore(srcSubjectPathStr, writer, writeIgnore)
                } catch (e: Exception) {
                    LogUtils.err(e)
//                    Context.submitIgnoreSubject()
                }
                LogUtils.info("Site path at %s on thread %s is done.", subjectDirStr, Thread.currentThread().name)
            }
        }
        LogUtils.info("已提交批次 ${index + 1}，当前已派发任务: ${(index + 1) * batchSize}")
    }

    Context.waitAllFutureDone("Copy", false)
    Context.futureCheckClear()
    LogUtils.release()
}

private fun logNoDebugPrint(
    codeModule: CodeModule,
    srcSubjectPath: Path,
    dstSubjectPath: Path,
) {
    LogUtils.debugNoPrint(
        "Success copy %s, from:%n     %s%n  -> %s%n",
        codeModule.originSubjectNumber,
        srcSubjectPath.toFile().absolutePath,
        dstSubjectPath.toFile().absolutePath
    )
}

private fun fileLoopCore(subjectPathStr: String, writer: Writer, writeIgnore: Writer?) {
    WriteDicomProcessor.fileLoop(subjectPathStr, Function<File, DicomFileActionResult?> { srcFile ->
        if (!srcFile.isFile) {
            Context.submitFailureFile()
            return@Function DicomFileActionResult(
                isError = true,
                isSkipAllFileWithTheDirectory = false
            )
        }
        val srcStr = srcFile.absolutePath
        //fixme fixed some err dicoms
        /*        if(!srcStr.contains("\\\\024\\\\024-00448".toRegex())) {
                        return@fileLoop
                    }*/

        val originSubjectCodePath =
            srcStr.replace(
                Settings.ORIGIN_SUBJECT_CODE_PATH_REPLACE_REGEX,
                Settings.ORIGIN_SUBJECT_CODE_PATH_REPLACE_DST
            )
        writer.append("\"$originSubjectCodePath\",")
        val originSubjectCode =
            srcStr.replace(Settings.ORIGIN_SUBJECT_CODE_REPLACE_REGEX, Settings.ORIGIN_SUBJECT_CODE_REPLACE_DST)
        val codeModule = CodeManager.INSTANCE[originSubjectCode]
        if (null == codeModule) {
            LogUtils.infoNoPrint("Convert to CodeN Failure: %s %n", srcStr)
            val emptyDataForFill =
                "\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\""
            if (writeIgnore != null) {
                synchronized(sWriterIgnoreLocker) {
                    writeIgnore.append("\"$originSubjectCodePath\",")
                    writeIgnore.append(emptyDataForFill)
                    writeIgnore.append(String.format("%n"))
                    writeIgnore.flush()
                }
            } else {
                writer.append(emptyDataForFill)
                writer.append(String.format("%n"))
                writer.flush()
            }
            Context.submitFailureFile()
            return@Function DicomFileActionResult(isError = true, isSkipAllFileWithTheDirectory = false)
        }
        var dstStr = ""
        val replaceRegex = Settings.ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_REGEX_STR.replace(
            "@originSubjectCode",
            codeModule.originSubjectNumber
        ).toRegex()
        val replaceDst = Settings.ORIGIN_SUBJECT_DIR_REPLACE_TO_RELEASE_SUBJECT_DIR_DST_STR.replace(
            "@desensitizedSubjectCode",
            codeModule.desensitizedSubjectNumber
        )
        dstStr = srcStr.replace(replaceRegex, replaceDst)
        var dstStr2: String = dstStr
        var dstFile2: File? = null
        dstStr = dstStr.replace(Settings.SRC_DICOM_PATH, Settings.DST_DICOM_PATH)

        val dstFile = File(dstStr)

        val parentFile = dstFile.parentFile
        if (!parentFile.exists()) {
            parentFile.mkdirs()
        }

        try {
            checkIllegalFile(srcStr)
        } catch (e: IllegalArgumentException) {
            LogUtils.err("IllegalArgumentException: %s", e.message)
            Context.submitFailureFile()
            writer.flush()
            return@Function DicomFileActionResult(isError = true, isSkipAllFileWithTheDirectory = false)
        }
        if (dstFile.exists()) {
            LogUtils.infoNoPrint("Skip the file, because the dst file is exists, src = %s, %n   -> dst = %s", srcStr, dstStr)
            Context.submitIgnoreFile()
            writer.flush()
            return@Function DicomFileActionResult(isError = true, isSkipAllFileWithTheDirectory = false)
        }
        if (File("$dstStr.dcm").exists()) {
            LogUtils.infoNoPrint("Skip the file, because the dst file is exists, src = %s, %n   -> dst = %s", srcStr, "$dstStr.dcm")
            Context.submitIgnoreFile()
            writer.flush()
            return@Function DicomFileActionResult(isError = true, isSkipAllFileWithTheDirectory = false)
        }

        if (Settings.DST_DICOM_PATH2.isNotBlank()) {
            dstStr2 = dstStr2.replace(Settings.SRC_DICOM_PATH, Settings.DST_DICOM_PATH2)
            dstFile2 = File(dstStr2)
            val parentFile2 = dstFile2.parentFile
            if (!parentFile2.exists()) {
                parentFile2.mkdirs()
            }
            if (dstFile2.exists()) {
                dstFile2 = null
            }
            if (File("$dstStr2.dcm").exists()) {
                dstFile2 = null
            }
        }

        val originDicomData = WriteDicomProcessor.replaceTagValue(
            srcFile,
            dstFile,
            dstFile2,
            originSubjectCode,
            codeModule.desensitizedSubjectNumber
        )
        if (null == originDicomData) {
            writer.append(String.format("%n"))
            writer.flush()
            return@Function DicomFileActionResult(isError = true, isSkipAllFileWithTheDirectory = false)
        }
        if (originDicomData.isSkipAllDW) {
            return@Function DicomFileActionResult(isError = false , isSkipAllFileWithTheDirectory = true)
        }
        originDicomData.srcPath = srcStr
        originDicomData.writeData(writer)
        writer.append(String.format("%n"))
        writer.flush()
        codeModule.done()
        return@Function DicomFileActionResult()
    })
}

private fun checkIllegalFile(srcStr: String) {
    if (srcStr.matches("^.*\\.nii.gz$".toRegex())) {
        throw IllegalArgumentException("File $srcStr is NIFITI")
    } else if (srcStr.matches("^.*\\.json$".toRegex())) {
        throw IllegalArgumentException("File $srcStr is JSON")

    } else if (srcStr.matches("^.*\\.jpge?$".toRegex())) {
        throw IllegalArgumentException("File $srcStr is Picture")
    } else if (srcStr.matches("^.*\\.bmp$".toRegex())) {
        throw IllegalArgumentException("File $srcStr is Picture")
    }
}

private fun fileCopyCore(
    srcSubjectPath: Path,
    subjectPathStr: String,
    dstSubjectPath: Path,
    codeModule: CodeModule,
) {
    Files.walkFileTree(srcSubjectPath, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
            if (file == null) return FileVisitResult.CONTINUE
            val filePathStr = file.toFile().absolutePath
            val dstStr =
                filePathStr.replace(subjectPathStr, dstSubjectPath.toFile().absolutePath)
            val dst = Paths.get(dstStr)
            if (!dst.parent.exists()) {
                dst.parent.toFile().mkdirs()
            }
            try {
                Files.copy(file, dst, StandardCopyOption.REPLACE_EXISTING)
                Context.submitSuccessFile()
            } catch (e: Exception) {
                logErrorPrint(codeModule, srcSubjectPath, dstSubjectPath)
                Context.submitFailureFile()
                LogUtils.err(e)
            }
            return FileVisitResult.CONTINUE
        }
    })
}

private fun logErrorPrint(
    codeModule: CodeModule,
    srcSubjectPath: Path,
    dstSubjectPath: Path,
) {
    LogUtils.err(
        "Failure copy %s, from:%n     %s%n  -> %s%n",
        codeModule.originSubjectNumber,
        srcSubjectPath.toFile().absolutePath,
        dstSubjectPath.toFile().absolutePath
    )
}

private fun setupOriginDataWriter(
    originDataCsvStream: ThreadLocal<Writer>,
    sitePath: Path,
): Writer {
    var writer = originDataCsvStream.get()
    if (writer == null) {
        val file = File(LogUtils.logCurrentPath, "originData")
        if (!file.exists()) {
            file.mkdirs()
        }
        val originDataCsvPath =
            Paths.get(LogUtils.logCurrentPath.absolutePath, "originData", sitePath.name + ".csv")
        val fos = FileOutputStream(originDataCsvPath.toFile())
        val osw = OutputStreamWriter(fos, StandardCharsets.UTF_8)
        val bw = BufferedWriter(osw)
        originDataCsvStream.set(bw)
        writer = bw
        writer.append("\"SubjectPath\",")
        writer.append("\"patientName\",")
        writer.append("\"patientId\",")
        writer.append("\"siteCode\",")
        writer.append("\"vSubjectCode\",")
        writer.append("\"vSiteCode\",")
        writer.append("\"studyId\",")
        writer.append("\"patientSex\",")
        writer.append("\"patientAge\",")
        writer.append("\"patientWeight\",")
        writer.append("\"patientBirthDate\",")
        writer.append("\"studyDesc\",")
        writer.append("\"deviceSerialNumber\",")
        writer.append("\"studyDate\",")
        writer.append("\"seriesDate\",")
        writer.append("\"acquisitionDate\",")
        writer.append("\"contentDate\",")
        writer.append("\"manufacturer\",")
        writer.append("\"manufacturerModelName\",")
        writer.append("\"srcPath\"")
        writer.append(String.format("%n"))
    }
    return writer
}