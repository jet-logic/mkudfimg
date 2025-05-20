package mkudfimg;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Base64;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.stream.Stream;

import mkudfimg.Inode.Symlink;

public class FileSystem {

    // sources tree
    private TreeNode root = null;
    boolean cacheInodes = false;
    boolean followLinks = true;
    boolean linkDuplicates = false;
    boolean checkDuplicates = false;
    boolean addManifest = false;
    boolean calcDigest = false;
    Console userConsole = System.console();
    boolean caseSensitive = false;
    boolean carryOn = false;
    boolean zeroSize = false;
    boolean noEmptyDirs = false;
    boolean noEmptyFiles = false;
    boolean compactSpace = false;
    boolean noDirTime = true;
    int sortSize = 2;
    int sortEntries = 2;
    int verbosity = 2;
    int setArchived = 2;
    int fsLayout = 3;
    // Hashtable<Object, Inode> inoCache = null;
    Map<Object, Inode> inoCache = new Hashtable<>();

    void addPath(String target) throws IOException {
        Path path = Paths.get(target);
        BasicFileAttributes a = readAttributes(path);
        TreeNode dir = getRoot();
        if (a.isDirectory()) {
            dir.getData().supply(a, noDirTime);
            walk(dir, path);
        } else {
            putPath(dir, path);
        }
    }

    synchronized TreeNode getRoot() {
        if (root == null) {
            root = new TreeNode.Directory("", null);
            root.setData(new Inode.File(true));
        }
        return root;
    }

    TreeNode putPath(TreeNode parent, Path path) throws IOException {
        String name = path.getFileName().toString();
        BasicFileAttributes a = readAttributes(path);

        if (Files.isSymbolicLink(path)) {
            if (!followLinks) {
                Node<Inode> child = parent.internFile(name);
                child.setData(fetch(path, true));
                System.err.printf("%s -> <%s>\t\n", path, new String(((Symlink) child.getData()).data));
                return (TreeNode) child;
            }
        }
        if (a.isDirectory()) {
            System.err.printf("%s/ +\t\r", path);
            Node<Inode> child = parent.internTree(name);
            child.setData(fetch(path, false));
            // walk((TreeNode) child, path);
            return (TreeNode) child;
        } else {
            System.err.printf("%s +\t\r", path);
            Node<Inode> child = parent.internFile(name);
            child.setData(fetch(path, false));
            return (TreeNode) child;
        }
    }

    Stream<Path> list(final Path dir) throws IOException {
        if (this.userConsole == null) {
            return Files.list(dir);
        }
        RETRY: for (;;) {
            try {
                return Files.list(dir);
            } catch (IOException ex) {
                userConsole.printf("%s: %s\n", ex.getClass().getSimpleName(), ex.getMessage());
                for (;;) {
                    String res = userConsole.readLine("[A]bort [R]etry: ");
                    switch (res == null ? -1 : (res.length() != 1 ? 0 : res.charAt(0))) {
                        case 'A':
                        case 'a':
                        case -1:
                            ex.printStackTrace(userConsole.writer());
                            System.exit(1);
                            return null;
                        case 'R':
                        case 'r':
                            continue RETRY;
                    }
                }
            }
        }
    }

    BasicFileAttributes readAttributes(Path path) throws IOException {
        if (this.userConsole == null) {
            return Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
        }
        RETRY: for (;;) {
            try {
                return Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
            } catch (IOException ex) {
                userConsole.printf("%s: %s\n", ex.getClass().getSimpleName(), ex.getMessage());
                for (;;) {
                    String res = userConsole.readLine("[A]bort [R]etry: ");
                    switch (res == null ? -1 : (res.length() != 1 ? 0 : res.charAt(0))) {
                        case 'A':
                        case 'a':
                        case -1:
                            ex.printStackTrace(userConsole.writer());
                            System.exit(1);
                            return null;
                        case 'R':
                        case 'r':
                            continue RETRY;
                    }
                }
            }
        }
    }

    void walk(TreeNode parent, final Path dir) throws IOException {
        /* */
        Iterator<Path> it = list(dir).iterator();
        while (it.hasNext()) {
            Path path = it.next();
            TreeNode child = putPath(parent, path);
            Inode data = child.getData();
            // System.err.println(data.mode);
            if (data != null && data.isDirectory()) {
                walk(child, path);
            }
        }
        /**/
        /*
         * list(dir).forEach(path -> {
         * TreeNode child;
         * try {
         * child = putPath(parent, path);
         * Inode data = child.getData();
         * // System.err.println(data.mode);
         * if (data != null && data.isDirectory()) {
         * walk(child, path);
         * }
         * } catch (IOException ex) {
         * throw new RuntimeException(ex);
         * }
         * });
         */
    }

    Inode fetch(Path path, boolean symlink) throws IOException {
        Inode ino;
        BasicFileAttributes a = Files.getFileAttributeView(path, BasicFileAttributeView.class).readAttributes();
        if (inoCache != null) {
            Object id = a.fileKey();
            if (id != null) {
                ino = inoCache.get(id);
                if (ino == null) {
                    ino = Inode.ofFile(path, a, symlink);
                    inoCache.put(id, ino);
                    System.err.print("CACHE:MIS");
                } else {
                    System.err.print("CACHE:HIT");
                }
                System.err.println(path);
                ino.supply(a, ino.isDirectory() ? noDirTime : false);
                return ino;
            }
        }
        ino = Inode.ofFile(path, a, symlink);
        ino.supply(a, ino.isDirectory() ? noDirTime : false);
        return ino;
    }

    void zeroSize() throws IOException {
        Iterator<Node<Inode>> w = getRoot().depthFirstIterator();
        while (w.hasNext()) {
            Node<Inode> cur = w.next();
            Inode ino = cur.getData();
            if (!ino.isDirectory()) {
                ino.size = 0;
            }
        }
    }

    void trimEmpty(boolean dir, boolean file) throws IOException {
        Iterator<Node<Inode>> w = getRoot().depthFirstIterator();
        while (w.hasNext()) {
            Node<Inode> cur = w.next();
            Inode ino = cur.getData();
            if (ino.isDirectory()) {
                if (dir && cur.getChildren().isEmpty()) {
                    cur.getParent().getChildren().remove(cur);
                }
            } else if (file && ino.size == 0) {
                cur.getParent().getChildren().remove(cur);
            }
        }
    }

    long[] mergeDuplicate() throws IOException {
        long sameSize = 0, sameHash = 0, nRemoved = 0;
        // Generate size map
        Iterator<Node<Inode>> w = getRoot().depthFirstIterator();
        HashMap<Long, LinkedHashSet<Inode>> sizeMap = new HashMap<>();
        while (w.hasNext()) {
            Node<Inode> cur = w.next();
            Inode ino = cur.getData();
            if (ino != null && !ino.isDirectory() && !ino.isManifest() && !ino.isCommand() && ino.size > 0) {
                assert (ino.size <= Long.MAX_VALUE);
                assert (ino.same == null);
                LinkedHashSet<Inode> inoSet = sizeMap.get(ino.size);
                if (inoSet == null) {
                    sizeMap.put(ino.size, inoSet = new LinkedHashSet<>());
                }
                inoSet.add(ino);
            }
        }
        // For each distinct size list, ...
        if (sizeMap.size() < 1) {
            return new long[] { sameSize, sameHash, nRemoved };
        }
        // sizeMap.values().
        for (Map.Entry<Long, LinkedHashSet<Inode>> entry : sizeMap.entrySet()) {
            LinkedHashSet<Inode> inoSet = entry.getValue();
            if (inoSet.size() > 1) {
                ++sameSize;
                HashMap<String, LinkedHashSet<Inode>> hashMap = new HashMap<>();
                Base64.Encoder enc = Base64.getEncoder();
                for (Inode ino : inoSet) {
                    byte[] h = ino.getHash();
                    if (h != null) {
                        String k = enc.encodeToString(h);
                        inoSet = hashMap.get(k);
                        if (inoSet == null) {
                            hashMap.put(k, inoSet = new LinkedHashSet<>());
                        }
                        inoSet.add(ino);
                    }
                }
                for (LinkedHashSet<Inode> iSet : hashMap.values()) {
                    if (iSet.size() > 1) {
                        Inode inoA = null;
                        ++sameHash;
                        for (Inode ino : inoSet) {
                            if (inoA == null) {
                                inoA = ino;
                            } else {
                                ino.same = inoA;
                            }
                        }
                    }
                }

            }
        }
        w = getRoot().depthFirstIterator();
        while (w.hasNext()) {
            Node<Inode> cur = w.next();
            final Inode ino = cur.getData();
            if (ino != null) {
                final Inode inoA = ino.same;
                if (inoA != null) {
                    ++nRemoved;
                    inoA.nlink += ino.nlink;
                    cur.setData(inoA);
                }
            }
        }
        return new long[] { sameSize, sameHash, nRemoved };
    }
}
