package top.elune.utils.engine

import kotlinx.coroutines.*
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.VR
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.io.DicomOutputStream
import top.elune.utils.commons.*
import top.elune.utils.dicom.OriginDicomData
import top.elune.utils.utils.LogUtils
import java.io.ByteArrayOutputStream
import java.io.IOException

class SedaProcessor(private val ctx: SedaContext) {

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
                ctx.writeChannel.send(result)
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

    private fun doTransform(task: DicomTask): ProcessedResult {
        var attrs: Attributes
        var fmi: Attributes? = null

        DicomInputStream(task.originFile).use { dis ->
            attrs = dis.readDataset(-1, -1)
            fmi = dis.fileMetaInformation
        }

        val desensitizedSubjectCode = task.codeModule.desensitizedSubjectCode
        val originDicomData: OriginDicomData? = extractOriginData(attrs, desensitizedSubjectCode, task)

        // --- 步骤 2: 执行脱敏替换 ---
        applyDesid(attrs, desensitizedSubjectCode, task)

        // --- 步骤 3: 序列化 ---
        val baos = ByteArrayOutputStream()
        val tsuid = fmi?.getString(Tag.TransferSyntaxUID) ?: "1.2.840.10008.1.2.1"
        DicomOutputStream(baos, tsuid).use { dos ->
            dos.writeDataset(fmi, attrs)
        }

        ctx.stats.fileProcessed.incrementAndGet()
        return ProcessedResult(
            task.originFile,
            task.targetRelativePath,
            task.codeModule,
            baos.toByteArray(),
            originDicomData,
            true
        )
    }

    private fun applyDesid(
        attrs: Attributes,
        desensitizedSubjectCode: String,
        task: DicomTask
    ) {
        try {
            // 确保 MediaStorageSOPClassUID 合规
            val mediaStorageSOPClassUID = attrs.getString(Tag.MediaStorageSOPClassUID)
            if (mediaStorageSOPClassUID.isNullOrBlank()) {
                attrs.setString(Tag.MediaStorageSOPClassUID, VR.UI, attrs.getString(Tag.SOPClassUID))
            }

            // 核心身份替换
            attrs.setString(Tag.PatientName, VR.PN, desensitizedSubjectCode)
            attrs.setString(Tag.PatientID, VR.LO, desensitizedSubjectCode)

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

    private fun extractOriginData(
        attrs: Attributes,
        desensitizedSubjectCode: String,
        task: DicomTask
    ): OriginDicomData? {
        var originDicomData: OriginDicomData? = null

        // --- 步骤 1: 提取原始数据并持久化保存 (留底) ---
        try {
            originDicomData = OriginDicomData(
                attrs.getString(Tag.PatientName),
                attrs.getString(Tag.PatientID),
                attrs.getString(Tag.InstitutionName),
                desensitizedSubjectCode,
                attrs.getString(Tag.StudyID),
                attrs.getString(Tag.PatientSex),
                attrs.getString(Tag.PatientAge),
                attrs.getString(Tag.PatientWeight),
                attrs.getDate(Tag.PatientBirthDate),
                attrs.getString(Tag.StudyDescription),
                attrs.getString(Tag.DeviceSerialNumber),
                attrs.getDate(Tag.StudyDate),
                attrs.getDate(Tag.SeriesDate),
                attrs.getDate(Tag.AcquisitionDate),
                attrs.getDate(Tag.ContentDate),
                attrs.getString(Tag.Manufacturer, ""),
                attrs.getString(Tag.ManufacturerModelName, "")
            )
        } catch (e: Exception) {
            LogUtils.err("Read ${task.originFile.absolutePath} DICOM tag Error: ${e.message}")
        }
        return originDicomData
    }
}