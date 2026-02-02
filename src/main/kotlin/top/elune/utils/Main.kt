package top.elune.utils

import top.elune.utils.commons.*
import top.elune.utils.dicom.WriteDicomProcessor
import top.elune.utils.engine.SedaEngine
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
    val config = SedaConfig(
        inputPath = Settings.SRC_DICOM_PATH,
        ntfsOutputPath = Settings.DST_DICOM_PATH,
        nfsOutputPath = Settings.DST_DICOM_PATH2,
        logPath = Settings.LOG_PATH
    )
    SedaContext(config).use { ctx ->
        val engine = SedaEngine(ctx)
        engine.run()
    }
    LogUtils.release()
}
