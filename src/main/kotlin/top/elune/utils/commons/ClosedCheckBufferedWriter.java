package top.elune.utils.commons;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;

public class ClosedCheckBufferedWriter extends BufferedWriter {
    private volatile boolean isClosed = false;

    public ClosedCheckBufferedWriter(Writer out) {
        super(out);
    }

    public ClosedCheckBufferedWriter(Writer out, int sz) {
        super(out, sz);
    }

    @Override
    public void close() throws IOException {
        isClosed = true;
        super.close();
    }


    public boolean isClosed() {
        return isClosed;
    }
}
