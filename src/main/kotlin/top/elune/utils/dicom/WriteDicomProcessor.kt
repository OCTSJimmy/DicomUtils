package top.elune.utils.dicom

import top.elune.utils.commons.CodeManager
import top.elune.utils.commons.Context
import top.elune.utils.commons.DicomFileActionResult
import top.elune.utils.commons.Settings
import top.elune.utils.utils.LogUtils
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.VR
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Function
import kotlin.io.path.isReadable

class WriteDicomProcessor {
    companion object {
        @JvmStatic
        fun fileLoop(src: String, dicomActionFunction: Function<File, DicomFileActionResult?>?) {
            val srcRootPath = File(src).toPath()
            try {
                Files.walkFileTree(srcRootPath, object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (! dir.isReadable()) return FileVisitResult.CONTINUE
                        return if (Context.isRunning) {
                            val srcStr = dir.toFile().absolutePath
                            //                        chance2_200_6314
                            if (srcStr.matches(Settings.SUBJECT_DIR_VALID_REGEX)) {
                                LogUtils.infoNoPrint("Current: %s", srcStr)
                            } else if (srcStr.matches(Settings.DICOM_DW_DIR_VALID_REGEX)) {
                                LogUtils.debugNoPrint("DICOM DW Dir Path: %s", srcStr)
                            } else if (srcStr.matches(Settings.DICOM_OTHER_DIRS_VALID_REGEX)) {
                                LogUtils.debugNoPrint("DICOM Other Dir Path: %s", srcStr)
                            } else {
                                return FileVisitResult.SKIP_SUBTREE
                            }
                            super.preVisitDirectory(dir, attrs)
                        } else {
                            val srcStr = dir.toFile().absolutePath
                            if (srcStr.matches(Settings.SUBJECT_DIR_VALID_REGEX)) {
                                FileVisitResult.SKIP_SIBLINGS
                            } else {
                                FileVisitResult.CONTINUE
                            }
                        }
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (! file.isReadable()) return FileVisitResult.CONTINUE
                        if (dicomActionFunction != null) {
                            try {
                                val dicomActionResult = dicomActionFunction.apply(file.toFile())
                                if (dicomActionResult?.isError == true) {
                                    return if (dicomActionResult.isSkipAllFileWithTheDirectory) {
                                        val skipSize = file.toFile().parentFile.listFiles()?.size ?: 1
                                        Context.submitIgnoreFile(skipSize.toLong())
                                        FileVisitResult.SKIP_SIBLINGS
                                    } else {
                                        Context.submitIgnoreFile()
                                        FileVisitResult.CONTINUE
                                    }
                                }
                                val srcStr = file.toFile().absolutePath
                                val subjectCode = srcStr.replace(
                                    Settings.ORIGIN_SUBJECT_CODE_REPLACE_REGEX,
                                    Settings.ORIGIN_SUBJECT_CODE_REPLACE_DST
                                )
                                val isDone = CodeManager.INSTANCE[subjectCode]?.isDone() == true
                                if (isDone) {
                                    Context.submitSuccessFile()
                                } else {
                                    Context.submitIgnoreFile()
                                }
                            } catch (e: Exception) {
                                Context.submitFailureFile()
                                LogUtils.err(e)
                            }
                        }
                        return FileVisitResult.CONTINUE
                    }

                    override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                        return super.visitFileFailed(file, exc)
                    }

                    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                        if (dir.isReadable().not() ) return super.postVisitDirectory(dir, exc)
                        val srcStr = dir.toFile().absolutePath
                        if (srcStr.matches(Settings.SUBJECT_DIR_VALID_REGEX)) {
                            val subjectCode = srcStr.replace(
                                Settings.ORIGIN_SUBJECT_CODE_REPLACE_REGEX,
                                Settings.ORIGIN_SUBJECT_CODE_REPLACE_DST
                            )
                            val isDone = CodeManager.INSTANCE[subjectCode]?.isDone() == true
                            if (isDone) {
                                LogUtils.infoNoPrint("Done Subject: %s", srcStr)
                                Context.submitDoneSubject()
                            } else {
                                LogUtils.debugNoPrint("Ignore Subject: %s", srcStr)
                                Context.submitIgnoreSubject()
                            }
                        }
                        return super.postVisitDirectory(dir, exc)
                    }
                })
            } catch (e: IOException) {
                LogUtils.err(e)
            }
        }

        /**
         * 替换DICOM中相关Tag
         *
         * @param src                     原始DICOM文件对象
         * @param dst                     目标DICOM文件对象
         * @param originCode              原始编码
         * @param desensitizedSubjectCode 脱敏编码
         */
        @JvmStatic
        fun replaceTagValue(
            src: File,
            dst: File,
            originCode: String?,
            desensitizedSubjectCode: String?,
        ): OriginDicomData? {
            return replaceTagValue(src, dst, null, originCode, desensitizedSubjectCode)

        }

        @JvmStatic
        fun checkAndSkipSeries(attributes: Attributes, originCode: String?, src: File): OriginDicomData? {
            val seriesDescription = attributes.getString(Tag.SeriesDescription, "")
            // Important: Sikp (3D Saved State|biomind)
            if (null != seriesDescription && seriesDescription.matches(
                    "^.*(3D Saved State).*\$".toRegex(RegexOption.IGNORE_CASE)
                )
            ) {
                printSkipInfoMessage(originCode, src, seriesDescription)
                return OriginDicomData(isSkipAllDW = true)
            }
            if (null != seriesDescription && seriesDescription.matches(
                    "^.*biomind.*\$".toRegex(RegexOption.IGNORE_CASE)
                )
            ) {
                printSkipInfoMessage(originCode, src, seriesDescription)
                return OriginDicomData(isSkipAllDW = true)
            }
            if (null != seriesDescription && seriesDescription.matches(
                    "^.*(Processed Images|Time To Peak|Blood Flow|Blood Volume|Mean Transit Time).*\$".toRegex(
                        RegexOption.IGNORE_CASE
                    )
                )
            ) {
                if (!seriesDescription.matches("^.*Processed Images.*\$".toRegex())) {
                    printSkipInfoMessage(originCode, src, seriesDescription)
                    return OriginDicomData(isSkipAllDW = true)
                }
                if ((src.parentFile.listFiles()?.size ?: 0) < 100) {
                    printSkipInfoMessage(originCode, src, seriesDescription)
                    return OriginDicomData(isSkipAllDW = true)
                }
            }
            return null
        }

        /**
         * 替换DICOM中相关Tag
         *
         * @param src                     原始DICOM文件对象
         * @param dst                     目标DICOM文件对象
         * @param originCode              原始编码
         * @param desensitizedSubjectCode 脱敏编码
         */
        @JvmStatic
        fun replaceTagValue(
            src: File,
            dst: File,
            dst2: File?,
            originCode: String?,
            desensitizedSubjectCode: String?,
        ): OriginDicomData? {
            val displayTag = DisplayTag(src)
            if (null == displayTag.attributes) {
                LogUtils.infoNoPrint("DICOM %s attributes is NULL", src.absolutePath)
                return null
            }
            val attributes: Attributes = displayTag.attributes!!
            if (null == desensitizedSubjectCode || desensitizedSubjectCode.isBlank()) {
                LogUtils.err("Cannot found the vcode by %s", originCode)
                return null
            }

            var originDicomData: OriginDicomData? = null

            originDicomData = checkAndSkipSeries(attributes, originCode, src)
            if (originDicomData?.isSkipAllDW == true) return originDicomData

            try {
                val patientName = attributes.getString(Tag.PatientName)
                val patientId = attributes.getString(Tag.PatientID)
                val siteCode = attributes.getString(Tag.InstitutionName)
                val studyId = attributes.getString(Tag.StudyID)
                val patientSex = attributes.getString(Tag.PatientSex)
                val patientAge = attributes.getString(Tag.PatientAge)
                val patientWeight = attributes.getString(Tag.PatientWeight)
                val patientBirthDate = attributes.getDate(Tag.PatientBirthDate)
                val studyDesc = attributes.getString(Tag.StudyDescription)
                val deviceSerialNumber = attributes.getString(Tag.DeviceSerialNumber)
                val studyDate = attributes.getDate(Tag.StudyDate)
                val seriesDate = attributes.getDate(Tag.SeriesDate)
                val acquisitionDate = attributes.getDate(Tag.AcquisitionDate)
                val contentDate = attributes.getDate(Tag.ContentDate)
                val studyTime = attributes.getDate(Tag.StudyTime)
                val seriesTime = attributes.getDate(Tag.SeriesTime)
                val acquisitionTime = attributes.getDate(Tag.AcquisitionTime)
                val contentTime = attributes.getDate(Tag.ContentTime)
                val manufacturer = attributes.getString(Tag.Manufacturer, "")
                val manufacturerModelName = attributes.getString(Tag.ManufacturerModelName, "")
                originDicomData = OriginDicomData(
                    patientName,
                    patientId,
                    siteCode,
                    desensitizedSubjectCode,
                    studyId,
                    patientSex,
                    patientAge,
                    patientWeight,
                    patientBirthDate,
                    studyDesc,
                    deviceSerialNumber,
                    studyDate,
                    seriesDate,
                    acquisitionDate,
                    contentDate,
                    manufacturer,
                    manufacturerModelName
                )
            } catch (e: Exception) {
                LogUtils.err("Read %s DICOM tag Error, reason is: %s", src.absolutePath, e.message)
            }
            try {
                val mediaStorageSOPClassUID = attributes.getString(Tag.MediaStorageSOPClassUID)
                if (mediaStorageSOPClassUID == null || mediaStorageSOPClassUID.isBlank()) {
                    val sopClassUID = attributes.getString(Tag.SOPClassUID)
                    attributes.setString(Tag.MediaStorageSOPClassUID, VR.UI, sopClassUID)
                }
                attributes.setString(Tag.PatientName, VR.PN, desensitizedSubjectCode)
                attributes.setString(Tag.PatientID, VR.LO, desensitizedSubjectCode)
                attributes.setNull(Tag.InstitutionName, VR.LO)
//                attributes.setNull(Tag.StudyID, VR.SH)
                attributes.setNull(Tag.PatientSex, VR.CS)
                attributes.setNull(Tag.PatientAge, VR.AS)
                attributes.setNull(Tag.PatientWeight, VR.DS)
                attributes.setNull(Tag.PatientBirthDate, VR.DA)
                attributes.setNull(Tag.StudyDescription, VR.LO)
                attributes.setNull(Tag.DeviceSerialNumber, VR.LO)
                attributes.setNull(Tag.StudyDate, VR.DA)
                attributes.setNull(Tag.SeriesDate, VR.DA)
                attributes.setNull(Tag.AcquisitionDate, VR.DA)
                attributes.setNull(Tag.ContentDate, VR.DA)
                attributes.setNull(Tag.StudyTime, VR.DA)
                attributes.setNull(Tag.SeriesTime, VR.DA)
                attributes.setNull(Tag.AcquisitionTime, VR.DA)
                attributes.setNull(Tag.ContentTime, VR.DA)
            } catch (e: Exception) {
                LogUtils.err(
                    "Replace %s DICOM tag Error, reason is: %s",
                    src.absolutePath,
                    e.localizedMessage
                )
                Context.submitFailureFile()
            }
            try {
                displayTag.writeTo(dst, attributes)
                LogUtils.debugNoPrint("Wrote success: %n    %s%n  -> %s%n", src.absolutePath, dst.absolutePath)
            } catch (e: Exception) {
                LogUtils.err(
                    "Write %s DICOM error, reason is: %s",
                    src.absolutePath,
                    e.localizedMessage
                )
                Context.submitFailureFile()
            }
            if (dst2 != null) {
                try {
                    displayTag.writeTo(dst2, attributes)
                    LogUtils.debugNoPrint(
                        "Wrote second path success: %n    %s%n  -> %s%n",
                        src.absolutePath,
                        dst2.absolutePath
                    )
                } catch (e: Exception) {
                    LogUtils.err(
                        "Write %s DICOM error, reason is: %s",
                        src.absolutePath,
                        e.localizedMessage
                    )
                    Context.submitFailureFile()
                }
            }
            return originDicomData
        }

        private fun printSkipInfoMessage(originCode: String?, src: File, seriesDescription: String?) {
            Context.submitIgnoreFile()
            LogUtils.debugNoPrint(
                "Skip %s dicom file at %s, because SeriesDescription is : %s",
                originCode,
                src.absolutePath,
                seriesDescription
            )
        }
    }
}