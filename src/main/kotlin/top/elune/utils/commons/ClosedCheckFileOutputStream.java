package top.elune.utils.commons;

import java.io.*;

public class ClosedCheckFileOutputStream extends FileOutputStream {
    private volatile boolean isClosed = false;

    public ClosedCheckFileOutputStream(String name) throws FileNotFoundException {
        super(name);
    }

    public ClosedCheckFileOutputStream(String name, boolean append) throws FileNotFoundException {
        super(name, append);
    }

    public ClosedCheckFileOutputStream(File file) throws FileNotFoundException {
        super(file);
    }

    public ClosedCheckFileOutputStream(File file, boolean append) throws FileNotFoundException {
        super(file, append);
    }

    public ClosedCheckFileOutputStream(FileDescriptor fdObj) {
        super(fdObj);
    }

    public boolean isClosed() {
        return isClosed;
    }

    @Override
    public void close() throws IOException {
        isClosed = true;
        super.close();
    }
}
