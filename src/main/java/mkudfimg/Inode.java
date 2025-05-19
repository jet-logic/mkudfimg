package mkudfimg;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;

public abstract class Inode {
    // permission

    static final short S_IRWXU = 00700;
    static final short S_IRUSR = 00400;
    static final short S_IWUSR = 00200;
    static final short S_IXUSR = 00100;
    static final short S_IRWXG = 00070;
    static final short S_IRGRP = 00040;
    static final short S_IWGRP = 00020;
    static final short S_IXGRP = 00010;
    static final short S_IRWXO = 00007;
    static final short S_IROTH = 00004;
    static final short S_IWOTH = 00002;
    static final short S_IXOTH = 00001;
    // file type
    static final int S_IFDIR = 0040000;
    static final int S_IFCHR = 0020000;
    static final int S_IFBLK = 0060000;
    static final int S_IFREG = 0100000;
    static final int S_IFIFO = 0010000;
    static final int S_IFLNK = 0120000;
    static ZoneOffset tzOffset = null;
    // extra
    static final int X_IS_MANIFEST = 1 << 16;
    static final int X_IS_COMMAND = 1 << 17;
    static String HASH_ALGORITHM = "MD5";
    // Attributes
    public int mode = 0;
    public int uid = 0;
    public int gid = 0;
    public long size = -1;
    // public long mtime = Long.MIN_VALUE;
    // public long ctime = Long.MIN_VALUE;
    // public long atime = Long.MIN_VALUE;
    public Object mtime = null;
    public Object ctime = null;
    public Object atime = null;
    // public short mtimeo = Short.MIN_VALUE;
    // public short ctimeo = Short.MIN_VALUE;
    // public short atimeo = Short.MIN_VALUE;
    // Build Attributes
    public int sort = 0;
    public int nlink = 0;
    public int flag = 0;
    public long auxA = 0;
    public long auxB = 0;
    private byte[] hash = null;
    Inode same = null;
    //

    public byte[] getHash() {
        byte[] _hash = this.hash;
        if (null == _hash) {
            synchronized (this) {
                _hash = this.hash;
                if (_hash == null) {
                    try {
                        this.hash = _hash = this.calcHash();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        }
        return _hash;
    }

    public boolean hasHash() {
        return this.hash != null;
    }

    public void setHash(byte[] hash) {
        this.hash = hash;
    }

    public Inode(int mode) {
        this.mode = mode;
    }

    public Inode(boolean dir) {
        if (dir) {
            this.mode = S_IFDIR;
        } else {
            this.mode = S_IFREG;
        }
    }

    //
    public boolean isDirectory() {
        return (S_IFDIR & mode) == S_IFDIR;
    }

    public boolean isSymLink() {
        return (S_IFLNK & mode) == S_IFLNK;
    }

    public boolean isManifest() {
        return (X_IS_MANIFEST & mode) == X_IS_MANIFEST;
    }

    public boolean isCommand() {
        return (X_IS_COMMAND & mode) == X_IS_COMMAND;
    }

    public int getFileType() {
        return (0xF000 & mode);
    }

    public int getFilePermission() {
        return (0x0FFF & mode);
    }

    static public MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    // abstract public byte[] calcHash() throws IOException;
    abstract public InputStream getInputStream() throws IOException;

    public byte[] calcHash() throws IOException {
        MessageDigest md = getMessageDigest();
        byte[] buf;
        try (InputStream in = getInputStream()) {
            buf = new byte[BUF_SIZE];
            for (int len; (len = in.read(buf)) != -1;) {
                md.update(buf, 0, len);
            }
            return md.digest();
        }
    }

    public long getLength() throws IOException {
        return this.size;
    }

    public void setLength(long n) throws IOException {
        assert (n >= 0);
        this.size = n;
    }

    public void supply(BasicFileAttributes a, boolean noTime) {
        if (a.isDirectory()) {
            if (this.getFileType() == 0) {
                this.mode |= S_IFDIR;
            }
        } else {
            if (this.getFileType() == 0) {
                this.mode |= S_IFREG;
            }
            if (size == -1) {
                this.size = a.size();
            }
        }
        if (!noTime) {
            if (this.atime == null) {
                this.atime = a.lastAccessTime();
            }
            if (this.mtime == null) {
                this.mtime = a.lastModifiedTime();
            }
            if (this.ctime == null) {
                this.ctime = a.creationTime();
            }
        }
    }

    public void supply(PosixFileAttributes a) {
        for (PosixFilePermission b : a.permissions()) {
            switch (b) {
                case GROUP_EXECUTE:
                    this.mode |= Inode.S_IXGRP;
                    break;
                case GROUP_READ:
                    this.mode |= Inode.S_IRGRP;
                    break;
                case GROUP_WRITE:
                    this.mode |= Inode.S_IWGRP;
                    break;
                case OWNER_EXECUTE:
                    this.mode |= Inode.S_IXUSR;
                    break;
                case OWNER_READ:
                    this.mode |= Inode.S_IRUSR;
                    break;
                case OWNER_WRITE:
                    this.mode |= Inode.S_IWUSR;
                    break;
                case OTHERS_EXECUTE:
                    this.mode |= Inode.S_IXOTH;
                    break;
                case OTHERS_READ:
                    this.mode |= Inode.S_IROTH;
                    break;
                case OTHERS_WRITE:
                    this.mode |= Inode.S_IWOTH;
                    break;
                default:
                    assert (false);
            }
        }
    }

    static Inode ofFile(Path path, BasicFileAttributes a, boolean symlink) throws IOException {
        Inode ino;
        if (symlink) {
            ino = new Symlink(Files.readSymbolicLink(path).toString());
        } else if (a.isDirectory()) {
            // System.err.println("DIR");
            ino = new File(S_IFDIR, path.toString());
        } else {
            // System.err.println("REG");
            ino = new File(S_IFREG, path.toString());
            ino.setLength(a.size());
        }
        return ino;
    }

    static int BUF_SIZE = 4 * 1024 * 1024;

    static public class File extends Inode {

        public String path;

        public File(int mode, String path) {
            super(mode);
            this.path = path;
        }

        public File(boolean mode, String path) {
            super(mode);
            this.path = path;
        }

        public File(boolean mode) {
            super(mode);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            // System.err.printf("getInputStream: %s\n", path);
            return new FileInputStream(path);
        }
    }

    static public class Symlink extends Inode {

        public final byte[] data;

        public Symlink(String path) throws IOException {
            super(S_IFLNK);
            data = path.getBytes("UTF-8");
            size = data.length;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(data);
        }

    }

    static public class Manifest extends Inode {

        public Manifest(int mode) {
            super(S_IFREG | X_IS_MANIFEST | mode);
        }

        public Manifest() {
            super(X_IS_MANIFEST | S_IFREG);
        }

        @Override
        public byte[] calcHash() throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                           // choose Tools | Templates.
        }

        @Override
        public InputStream getInputStream() throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                           // choose Tools | Templates.
        }

        @Override
        public long getLength() throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods,
                                                                           // choose Tools | Templates.
        }

    }
}
