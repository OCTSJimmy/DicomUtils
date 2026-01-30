package top.elune.utils.engine

import kotlinx.coroutines.*
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.io.DicomOutputStream
import top.elune.utils.commons.*
import java.io.ByteArrayOutputStream
import java.io.File

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
                // 此处 send 到 writeChannel 会触发背压
                ctx.writeChannel.send(result)
            } catch (e: Exception) {
                // 读取或脱敏失败的情况，记录错误任务
                ctx.writeChannel.send(ProcessedResult(
                    originFile = task.originFile,
                    targetRelativePath = task.targetRelativePath,
                    codeModule = task.codeModule,
                    data = null,
                    isSuccess = false,
                    errorMsg = e.message
                ))
            }
        }
    }

    private fun doTransform(task: DicomTask): ProcessedResult {
        // 1. 读取文件到内存 (IO 操作)
        val attrs = DicomInputStream(task.originFile).use { dis ->
            dis.readDataset(-1, -1)
        }

        // 2. 执行脱敏替换 (CPU 操作)
        // 直接使用 Task 带来的 codeModule，无需再去查找
        val vCode = task.codeModule.desensitizedSubjectCode

        attrs.apply {
            setString(org.dcm4che3.data.Tag.PatientName, org.dcm4che3.data.VR.PN, vCode)
            setString(org.dcm4che3.data.Tag.PatientID, org.dcm4che3.data.VR.LO, vCode)

            // 隐私清除（优先级 1）
            setNull(org.dcm4che3.data.Tag.InstitutionName, org.dcm4che3.data.VR.LO)
            setNull(org.dcm4che3.data.Tag.PatientBirthDate, org.dcm4che3.data.VR.DA)
            // ... 其他 Tag 的清除逻辑 ...
        }

        // 3. 特殊序列处理：如果是 DW 序列，可能需要额外的合规化处理（如特殊标记）
        if (task.isDW) {
            // 执行针对 DW 序列的额外清洗
        }

        // 4. 序列化为二进制流 (ByteArray)
        val baos = ByteArrayOutputStream()
        DicomOutputStream(baos).use { dos ->
            dos.writeDataset(attrs.metaInfo, attrs)
        }

        ctx.totalProcessed.incrementAndGet()

        return ProcessedResult(
            originFile = task.originFile,
            targetRelativePath = task.targetRelativePath,
            codeModule = task.codeModule,
            data = baos.toByteArray()
        )
    }
}