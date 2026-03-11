package top.elune.utils.engine

import top.elune.utils.commons.SedaContext
import top.elune.utils.dicom.CustomDicomInputStream
import top.elune.utils.dicom.OriginDicomData
import top.elune.utils.utils.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.VR
import java.util.*

class SedaProcessor(private val ctx: SedaContext) {
    private val seriesCheck3DSaved = """^.*(3D Saved State).*$""".toRegex(RegexOption.IGNORE_CASE)
    private val seriesCheckBiomind = """^.*biomind.*$""".toRegex(RegexOption.IGNORE_CASE)
    private val seriesCheckBloodFlow =
        """^.*(Processed Images|Time To Peak|Blood Flow|Blood Volume|Mean Transit Time).*$""".toRegex(RegexOption.IGNORE_CASE)
    private val seriesCheckProcessed = """^.*Processed Images.*$""".toRegex()

    fun start() {
        // 根据 CPU 核心数启动多个处理协程
        repeat(ctx.config.cpuParallelism) {
            ctx.engineScope.launch(Dispatchers.Default) {
                processorWorker()
            }
        }
    }

    private suspend fun processorWorker() {
        for (task in ctx.taskChannel) {
            try {
                val result = doTransform(task)
                if(result !+ null ) {
                   ctx.writeChannel.send(result)
                }
            } catch (e: Exception) {
                // 失败分支：确保字段名 isSuccess 与 ProcessedResult.kt 一致
                ctx.writeChannel.send(
                    ProcessedResult(
                        originFile = task.originFile,
                        targetRelativePath = task.targetRelativePath,
                        codeModule = task.codeModule,
                        data = null,
                        isSuccess = false,
                        errorMsg = e.message
                    )
                )
                // 统计失败文件数
                ctx.stats.fileError.incrementAndGet()
            }
        }
    }

    private fun applyDesid(
        attrs: Attributes,
        desensitizedSubjectNumber: String,
        task: DicomTask
    ) {
        try {
            // 确保 MediaStorageSOPClassUID 合规
            val mediaStorageSOPClassUID = attrs.getString(Tag.MediaStorageSOPClassUID)
            if (mediaStorageSOPClassUID.isNullOrBlank()) {
                attrs.setString(Tag.MediaStorageSOPClassUID, VR.UI, attrs.getString(Tag.SOPClassUID))
            }

            // 核心身份替换
            attrs.setString(Tag.PatientName, VR.PN, desensitizedSubjectNumber)
            attrs.setString(Tag.PatientID, VR.LO, desensitizedSubjectNumber)

            // 隐私清空 (严格遵守提供的逻辑)
            attrs.setNull(Tag.InstitutionName, VR.LO)
            attrs.setNull(Tag.PatientSex, VR.CS)
            attrs.setNull(Tag.PatientAge, VR.AS)
            attrs.setNull(Tag.PatientWeight, VR.DS)
            attrs.setNull(Tag.PatientBirthDate, VR.DA)
            attrs.setNull(Tag.StudyDescription, VR.LO)
            attrs.setNull(Tag.DeviceSerialNumber, VR.LO)
            // 时间/日期清空
            val dateTags = intArrayOf(Tag.StudyDate, Tag.SeriesDate, Tag.AcquisitionDate, Tag.ContentDate)
            dateTags.forEach { attrs.setNull(it, VR.DA) }
            val timeTags = intArrayOf(Tag.StudyTime, Tag.SeriesTime, Tag.AcquisitionTime, Tag.ContentTime)
            timeTags.forEach { attrs.setNull(it, VR.TM) }

        } catch (e: Exception) {
            LogUtils.err("Replace ${task.originFile.absolutePath} Tag Error: ${e.message}")
            throw e // 抛出异常由 processorWorker 统一处理成错误任务
        }
    }



    // SedaProcessor.kt doTransform 逻辑
    private fun doTransform(task: DicomTask): ProcessedResult {
        // 领用任务，减少积压计数
        ctx.scanQueuePending.decrementAndGet() //
        val parentPath = task.originFile.parentFile.absolutePath
        if (ctx.blacklistedDirs.contains(parentPath)) {
            ctx.stats.fileIgnored.incrementAndGet()
            return null
        }
        CustomDicomInputStream(task.originFile).use { dis ->
            // === 修改点 1: 仅读取元数据，遇到 PixelData (7FE0,0010) 即停止 ===
            // 这样 attrs 里只有几十 KB 的文本数据，没有几百 MB 的图像
            val attrs : Attributes = dis.readDatasetUntilPixelData()

            // 3. 【内容审计】判断是否命中黑名单规则
            if (checkAndSkipSeries(attrs, codeModule, file, parentFileCount)) {
                LogUtils.infoNoPrint("触发目录屏蔽规则，拉黑目录: $parentPath (触发文件: ${file.name})")
                blacklistedDirs.add(parentPath) // ⚡ 拉黑整个目录
                ctx.stats.fileIgnored.incrementAndGet()
                return
            }

            // --- 多帧影像检测 ---
            val frames = attrs.getInt(Tag.NumberOfFrames, 1)
            if (frames > 1) {
                LogUtils.debugNoPrint("检测到多帧影像 ($frames 帧): ${task.originFile.name}")
            }

            val desensitizedSubjectNumber = task.codeModule.desensitizedSubjectNumber

            val extractOriginData = extractOriginData(attrs, task)

            applyDesid(attrs, desensitizedSubjectNumber, task)

            // 处理完成，投递到写入管道前
            ctx.writeQueuePending.incrementAndGet() //
            ctx.stats.fileProcessed.incrementAndGet()

            return ProcessedResult(
                task.originFile, task.targetRelativePath, task.codeModule, null, attrs,
                extractOriginData, true
            )
        }
    }

    private fun printSkipInfoMessage(originCode: String?, src: File, seriesDescription: String?) {
        ctx.stats.fileIgnored.incrementAndGet()
        LogUtils.debugNoPrint(
            "Skip %s dicom file at %s, because SeriesDescription is : %s",
            originCode,
            src.absolutePath,
            seriesDescription
        )
    }

    fun checkAndSkipSeries(attributes: Attributes, codeModule: CodeModule, src: File, parentFileCount:Int): Boolean {

        val seriesDescription = attributes.getString(Tag.SeriesDescription, "")
        // Important: Skip (3D Saved State|biomind)
        if (null != seriesDescription && seriesDescription.matches(seriesCheck3DSaved)) {
            printSkipInfoMessage(codeModule.originSubjectCode, src, seriesDescription)
            return true
        }
        if (null != seriesDescription && seriesDescription.matches(seriesCheckBiomind)) {
            printSkipInfoMessage(codeModule.originSubjectCode, src, seriesDescription)
            return true
        }

        if (null != seriesDescription && seriesDescription.matches(seriesCheckBloodFlow)) {

            if (!seriesDescription.matches(seriesCheckProcessed)) {
                printSkipInfoMessage(codeModule.originSubjectCode, src, seriesDescription)
                return true
            }
            if (parentFileCount < 100) {
                printSkipInfoMessage(codeModule.originSubjectCode, src, seriesDescription)
                return true
            }
        }
        return false
    }
    
    private fun extractOriginData(
        attrs: Attributes,
        task: DicomTask
    ): OriginDicomData? {
        var originDicomData: OriginDicomData? = null

        // --- 步骤 1: 提取原始数据并持久化保存 (留底) ---
        try {
            originDicomData = OriginDicomData(
                attrs.getString(Tag.PatientName),
                attrs.getString(Tag.PatientID),
                task.codeModule.originSubjectNumber,
                task.codeModule.desensitizedSubjectNumber,
                attrs.getString(Tag.StudyID),
                attrs.getString(Tag.PatientSex),
                attrs.getString(Tag.PatientAge),
                attrs.getString(Tag.PatientWeight),
                attrs.getDate(Tag.PatientBirthDate)?.let { Date(it.time) },
                attrs.getString(Tag.StudyDescription),
                attrs.getString(Tag.DeviceSerialNumber),
                attrs.getDate(Tag.StudyDate)?.let { Date(it.time) },
                attrs.getDate(Tag.SeriesDate)?.let { Date(it.time) },
                attrs.getDate(Tag.AcquisitionDate)?.let { Date(it.time) },
                attrs.getDate(Tag.ContentDate)?.let { Date(it.time) },
                attrs.getString(Tag.Manufacturer, ""),
                attrs.getString(Tag.ManufacturerModelName, ""),
                task.originFile.absolutePath
            )
        } catch (e: Exception) {
            LogUtils.err("Read ${task.originFile.absolutePath} DICOM tag Error: ${e.message}")
        }
        return originDicomData
    }
}