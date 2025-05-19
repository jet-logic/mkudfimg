package mkudfimg;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class BlockSink extends OutputStream {

    ByteBuffer buf = ByteBuffer.allocate(1024 * 1024);
    public long nStage = 0;
    public long nExtent = 0;
    long nLeft = 0;
    long nWasted = 0;
    final public int blockSize;
    private OutputStream out = null;
    private long tick = 0;

    public BlockSink(int blockSize) {
        this.blockSize = blockSize;
    }

    public ByteBuffer getBuffer() {
        buf.clear();
        return buf;
    }

    public long getPosition() {
        return blockSize * nExtent;
    }

    public void setOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (out != null) {
            out.write(b, off, len);
        }
        final long x = this.nLeft + len;
        this.nExtent += (x / this.blockSize);
        this.nLeft = (x % this.blockSize);
        if (this.nLeft == 0 && (this.nExtent > tick)) {
            // if (this.nExtent > 0) {
            System.err.printf("@%9d %7.2fMiB Written... \r", this.nExtent,
                    (this.nExtent / (1024.0 * 1024.0)) * this.blockSize);
            // }
            tick = this.nExtent + ((32 * 1024 * 1024) / this.blockSize);
        }
    }

    @Override
    public void write(int b) throws IOException {
        if (out != null) {
            out.write(b);
        }
        final long x = this.nLeft + 1;
        this.nExtent += (x / this.blockSize);
        this.nLeft = (x % this.blockSize);
    }

    @Override
    public void write(byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    public void writep(byte[] b, int off, int len) throws IOException {
        if (len > 0) {
            assert (this.nLeft == 0);
            this.write(b, off, len);
        }
        writep();
    }

    public void writep() throws IOException {
        if (this.nLeft > 0) {
            this.nWasted += this.nLeft;
            byte[] bf = new byte[this.blockSize - (int) this.nLeft];
            Arrays.fill(bf, (byte) 0);
            this.write(bf);
            assert (this.nLeft == 0);
        }
    }

    public void padBlocks(long n) throws IOException {
        assert (this.nLeft == 0);
        assert (n > 0);
        byte[] b = new byte[this.blockSize];
        Arrays.fill(b, (byte) 0);
        while (n-- > 0) {
            this.write(b, 0, b.length);
            this.nWasted += this.blockSize;
        }
    }

    public void padUpTo(long lba) throws IOException {
        assert (this.nLeft == 0);
        assert (this.nExtent <= lba);
        byte[] b = new byte[this.blockSize];
        Arrays.fill(b, (byte) 0);
        while (this.nExtent < lba) {
            this.write(b, 0, b.length);
            this.nWasted += this.blockSize;
        }
    }

    public long calcBlocks(long size) {
        assert (size >= 0);
        return (((size) / this.blockSize) + ((size % this.blockSize) == 0 ? 0 : 1));
    }

    public void reset() {
        this.nExtent = 0;
        this.nLeft = 0;
        this.nWasted = 0;
        this.tick = 0;
    }
}
