package mkudfimg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;

public class Main {
    public static final int BLOCK_SIZE_CD = 2048;
    public static final int BLOCK_SIZE_HD = 512;

    public static void main(String[] args) throws IOException {
        int blockSize = BLOCK_SIZE_CD;
        String out = null;
        UDFBuild udf = new UDFBuild();
        TreeNode root;
        boolean compactImage = false;
        boolean noEmptyFiles = false;
        boolean noEmptyDirs = false;
        {
            String volLabel = null;
            FileSystem fs = new FileSystem();
            Iterator<String> argv = Arrays.stream(args).iterator();
            boolean addManifest = false;
            boolean zeroSize = false;
            for (int dash = 0; argv.hasNext();) {
                String arg = argv.next();
                if (dash > 1 || !arg.startsWith("-")) {
                    fs.addPath(arg);
                } else if ("--".equals(arg)) {
                    dash = 2;
                } else if (("--output".equals(arg) || "-o".equals(arg)) && argv.hasNext()) {
                    arg = argv.next();
                    out = arg;
                } else if ("-V".equals(arg) && argv.hasNext()) {
                    arg = argv.next();
                    volLabel = arg;
                } else if ("--manifest".equals(arg) || "--checksum".equals(arg)) {
                    addManifest = true;
                } else if ("--hd".equals(arg)) {
                    blockSize = BLOCK_SIZE_HD;
                } else if (("--cache-inodes".equals(arg) || "-h".equals(arg))) {
                    fs.cacheInodes = true;
                } else if ("--no-cache-inodes".equals(arg)) {
                    fs.cacheInodes = false;
                } else if (("--link-duplicates".equals(arg) || "-H".equals(arg))) {
                    fs.linkDuplicates = true;
                } else if ("--no-link-duplicates".equals(arg)) {
                    fs.linkDuplicates = false;
                } else if (("--follow-links".equals(arg) || "-f".equals(arg))) {
                    fs.followLinks = true;
                } else if (("--no-follow-links".equals(arg))) {
                    fs.followLinks = false;
                } else if ("--zero-size".equals(arg)) {
                    zeroSize = true;
                } else if ("--compact".equals(arg)) {
                    compactImage = true;
                } else if ("--trim-empty-file".equals(arg)) {
                    noEmptyFiles = true;
                } else if ("--trim-empty-dir".equals(arg)) {
                    noEmptyDirs = true;
                } else if ("--trim-empty".equals(arg)) {
                    noEmptyDirs = noEmptyFiles = true;
                } else if ("--help".equals(arg)) {
                    // Use "/" if the path starts from the root of the resources folder
                    try (InputStream is = Main.class.getResourceAsStream("/usage.txt")) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    }
                    System.exit(0);
                } else if ("--version".equals(arg)) {
                    String version = Main.class.getPackage().getImplementationVersion();
                    System.out.println(version);
                } else {
                    throw new RuntimeException("Unexpected argument : \"" + arg + "\"");
                }
            }
            root = fs.getRoot();
            if (addManifest) {
                udf.addManifest = true;
                boolean useEtc = false;
                for (Node<Inode> cur : root) {
                    if (!cur.isLeaf()) {
                        useEtc = true;
                        break;
                    }
                }
                if (useEtc) {
                    TreeNode man = (TreeNode) ((TreeNode) root.internTree(".etc")).internFile("checksum");
                    man.setData(new Inode.Manifest());
                    for (;;) {
                        ((TreeNode) man).flag |= (TreeNode.HIDE | TreeNode.IGNORE);
                        man = (TreeNode) man.getParent();
                        if (man == null || man.getChildren().size() > 1) {
                            break;
                        }
                    }
                } else {
                    TreeNode man = (TreeNode) root.internFile("checksum");
                    man.setData(new Inode.Manifest());
                    man.flag |= (TreeNode.HIDE | TreeNode.IGNORE);
                }
            }
            if (fs.cacheInodes) {
                fs.inoCache = new Hashtable<>();
            } else {
                fs.inoCache = null;
            }
            if (fs.linkDuplicates) {
                long[] r = fs.mergeDuplicate();
                System.err.printf("SameSize %d; SameHash %d; NodesRemoved %d;\n", r[0], r[1], r[2]);
            }
            if (noEmptyFiles || noEmptyDirs) {
                fs.trimEmpty(noEmptyDirs, noEmptyFiles);
            }
            if (zeroSize) {
                fs.zeroSize();
            }
            if (volLabel != null) {
                udf.logicalVolumeIdentifier = volLabel;
                udf.fileSetIdentifier = volLabel;
            }
        }
        BlockSink sink = new BlockSink(blockSize);
        udf.build(sink, root, out, compactImage);
    }
}
/*
 * }else if(opt_get_bool(&o, ('k'), ("check-duplicates"))){
 * db.checkDuplicates = !!o.bparam;
 * }else if(opt_get_bool(&o, ('i'), ("interactive"))){
 * db.userInteractive = o.bparam ? 2 : 0;
 * }else if(opt_get_bool(&o, ('b'), ("batch"))){
 * db.userInteractive = o.bparam ? 0 : 2;
 * }else if(opt_get_bool(&o, 0, ("calc-digest"))){
 * db.calcDigest = !!o.bparam;
 * }else if(opt_get_bool(&o, 0, ("manifest"))){ // OPT, DOC
 * db.addManifest = !!o.bparam;
 * }else if(opt_get_bool(&o, 0, ("zero-size"))){ // DOC
 * db.zeroSize = o.bparam ? 1 : 0;
 * 
 * }else if(opt_get_bool(&o, 0, ("verbose"))){ // DOC
 * db.verbosity = (o.bparam ? 1 : 0);
 * }else if(opt_get_bool(&o, 0, ("quiet"))){ // DOC
 * db.verbosity = (o.bparam ? 0 : 1);
 * }else if(opt_get_bool(&o, 0, ("sort-size"))){
 * db.sortSize = (o.bparam ? 1 : 0);
 * }else if(opt_get_bool(&o, 0, ("carryon"))){
 * db.carryOn = (o.bparam ? 1 : 0);
 * }else if(opt_get_param(&o, 0, ("system-area"))){
 * db.set("system-area", o.sparam);
 * }else if(opt_get_bool(&o, 0, ("archive"))){ // OPT, DOC
 * db.setArchived = (o.bparam ? 1 : 0);
 * }else if(opt_get_bool(&o, 0, ("compact"))){ // OPT, DOC
 * db.compactSpace = (o.bparam ? 1 : 0);
 */
