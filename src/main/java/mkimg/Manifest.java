package mkimg;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Iterator;

public class Manifest {

    final protected static char[] B16RADIX = "0123456789abcdef".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = B16RADIX[v >>> 4];
            hexChars[j * 2 + 1] = B16RADIX[v & 0x0F];
        }
        return new String(hexChars);
    }

    static public void write(OutputStream o, Node<Inode> root, boolean dummy) {
        try (PrintWriter p = new PrintWriter(o)) {
            Iterator<Node<Inode>> w = root.depthFirstIterator();
            while (w.hasNext()) {
                Node<Inode> item = w.next();
                Inode ino = item.getData();
                if (!ino.isDirectory() && !ino.isManifest()) {
                    // hash *path
                    if (dummy) {
                        p.print("d41d8cd98f00b204e9800998ecf8427e *");
                    } else {
                        byte[] hash = ino.getHash();
                        for (int j = 0; j < hash.length; j++) {
                            int v = hash[j] & 0xFF;
                            p.print(B16RADIX[v >>> 4]);
                            p.print(B16RADIX[v & 0x0F]);
                        }
                        p.print(' ');
                        p.print('*');
                    }
                    item.descend(x -> {
                        Node<Inode> parent = x.getParent();
                        if (null != parent) {
                            if (parent.getParent() != null) {
                                p.print('/');
                            }
                            p.print(x.getName());
                        }
                    });
                    p.print('\n');
                }
            }
//            p.flush();
        }
    }

    static class SinkCounter extends OutputStream {

        protected long count = 0;

        public long getCount() {
            return count;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            this.count += len;
        }

        @Override
        public void write(byte[] b) throws IOException {
            this.count += b.length;
        }

        @Override
        public void write(int b) throws IOException {
            this.count++;
        }
    }
}
