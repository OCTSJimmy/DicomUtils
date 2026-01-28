package top.elune.utils.dicom

import org.dcm4che3.io.DicomInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

class CustomDicomInputStream : DicomInputStream {
    var isClosed = false
        private set

    constructor(`in`: InputStream?, tsuid: String?) : super(`in`, tsuid) {}
    constructor(`in`: InputStream?) : super(`in`) {}
    constructor(`in`: InputStream?, preambleLength: Int) : super(`in`, preambleLength) {}
    constructor(file: File?) : super(file) {}

    @Throws(IOException::class)
    override fun close() {
        super.close()
        isClosed = true
    }
}