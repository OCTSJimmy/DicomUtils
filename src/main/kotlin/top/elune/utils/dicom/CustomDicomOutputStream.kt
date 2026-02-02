package top.elune.utils.dicom

import org.dcm4che3.io.DicomOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

@Suppress("unused")
class CustomDicomOutputStream : DicomOutputStream {
    var isClosed = false
        private set

    constructor(out: OutputStream?, tsuid: String?) : super(out, tsuid)
    constructor(file: File?) : super(file)

    @Throws(IOException::class)
    override fun close() {
        super.close()
        isClosed = true
    }
}