package mkudfimg;

import java.io.Console;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Random;
import udf.DataDesc;
import udf.UDFWrite;
import udf.UDFWriteFile;
import static udf.UDFWriteFile.fileItem;

public class UDFBuild {

    Console userConsole = System.console();
    private OffsetDateTime logicalVolumeIntegrityDescTime;
    private OffsetDateTime recordingDateandTime;
    private long lbaIntegritySequence;
    private long nLastUniqueID;
    private long partionSize;
    private int nFiles;
    private int nDirectories;
    private byte[] applicationIdentifier;
    public String logicalVolumeIdentifier;
    public String volumeSetIdentifier;
    public String fileSetIdentifier;
    private long lbaMainVolumeDesc;
    private long lbaUDFPartitionStart;
    byte[] ENTITYID_OSTA_COMPLIANT = new byte[] { '\00', '*', 'O', 'S', 'T', 'A', ' ', 'U', 'D', 'F', ' ', 'C', 'o',
            'm', 'p', 'l', 'i', 'a', 'n', 't', '\00', '\00', '\00', '\00', '\02', '\01', '\03', '\00', '\00', '\00',
            '\00', '\00' };
    private long lbaReserveVolumeDesc = -1;
    boolean addManifest = false;

    public UDFBuild() {
    }

    static void volumeRecognitionArea(BlockSink out) throws IOException {
        System.err.printf("@%9d VRA\t\n", out.nExtent);
        assert ((out.blockSize % 512) == 0);
        assert ((32768 / out.blockSize) == out.nExtent);
        ByteBuffer b = out.getBuffer();
        byte[][] vrid = new byte[][] { { 'B', 'E', 'A', '0', '1' }, { 'N', 'S', 'R', '0', '2' },
                { 'T', 'E', 'A', '0', '1' } };
        Arrays.fill(b.array(), 0, 2048, (byte) 0);
        for (int i = 0; i < 3; ++i) {
            b.clear();
            b.put((byte) 0);// StructureType
            b.put(vrid[i]);// StandardIdentifier
            b.put((byte) 1);// StructureVersion
            out.write(b.array(), 0, 2048);
        }
        assert (((32768 / out.blockSize) + ((3 * 2048) / out.blockSize)) == out.nExtent);
    }

    static void terminatingDescriptor(BlockSink out) throws IOException {
        terminatingDescriptor(out, out.nExtent);
    }

    static void terminatingDescriptor(BlockSink out, long lba) throws IOException {
        ByteBuffer b = out.getBuffer();
        UDFWrite.zfill(b, 512);
        out.writep(UDFWrite.descriptorTag(b, (short) 8, lba, 512).array(), 0, b.position());
    }

    static void systemArea(BlockSink out) throws IOException {
        padTo(out, 32768 / out.blockSize, "System Area");
    }

    void logicalVolumeIntegrityDesc(BlockSink out) throws IOException {
        System.err.printf("@%9d LVI\t\n", out.nExtent);
        if (out.nStage > 0) {
            this.lbaIntegritySequence = out.nExtent;
        } else {
            assert (this.lbaIntegritySequence == out.nExtent);
        }
        // struct LogicalVolumeIntegrityDesc { // ISO 13346 3/10.10
        ByteBuffer b = out.getBuffer();
        // struct tag DescriptorTag;
        b.position(16);
        // struct timestamp RecordingDateAndTime;
        UDFWrite.putTimestamp(b, this.logicalVolumeIntegrityDescTime);
        // Uint32 IntegrityType (Close Integrity Descriptor)
        b.putInt(1);
        // extent_ad NextIntegrityExtent;
        b.putInt(0).putInt(0);
        // struct LogicalVolumeIntegrityDescContentsUse LogicalVolumeContentsUse;
        {
            // Uint64 UniqueID;
            b.putLong(this.nLastUniqueID);
            // byte reserved[24];
            UDFWrite.zfill(b, 24);
        }
        // Uint32 NumberOfPartitions;
        b.putInt(1);
        // Uint32 LengthOfImplementationUse;
        b.putInt(46);
        // Uint32 FreeSpaceTable;
        b.putInt(0);
        // Uint32 SizeTable;
        b.putInt((int) this.partionSize);
        // struct LogicalVolumeIntegrityDescImplementationUse ImplementationUse;
        {
            // struct EntityID ImplementationID;
            b.put(this.applicationIdentifier);
            b.putInt(this.nFiles); // Uint32 NumberofFiles;
            b.putInt(this.nDirectories); // Uint32 NumberofDirectories;
            b.putShort((short) 0x102); // Uint16 MinimumUDFReadRevision;
            b.putShort((short) 0x102); // Uint16 MinimumUDFWriteRevision;
            b.putShort((short) 0x102); // Uint16 MaximumUDFWriteRevision;
        }
        // Write
        out.writep(UDFWrite.descriptorTag(b, (short) 9, out.nExtent, b.position()).array(), 0, b.position());
        assert (134 == b.position());
        // Terminate
        UDFBuild.terminatingDescriptor(out);
    }

    void volumeDescriptorSequence(BlockSink out, boolean main) throws IOException {
        System.err.printf("@%9d %cVDS\t\n", out.nExtent, main ? 'M' : 'R');
        if (main) {
            if (out.nStage > 0) {
                this.lbaMainVolumeDesc = out.nExtent;
            } else {
                assert (this.lbaMainVolumeDesc == out.nExtent);
            }
        } else {
            assert (out.nExtent > 0);
            if (out.nStage > 0) {
                this.lbaReserveVolumeDesc = out.nExtent;
            } else {
                assert (this.lbaReserveVolumeDesc == out.nExtent);
            }
        }
        ByteBuffer b = out.getBuffer();
        // struct PrimaryVolumeDescriptor
        {
            // tag DescriptorTag
            b.clear().limit(512).position(16);
            // Uint32 VolumeDescriptorSequenceNumber;
            b.putInt(0);
            // Uint32 PrimaryVolumeDescriptorNumber;
            b.putInt(0);
            // dstring VolumeIdentifier[32];
            assert (b.position() == 24);
            UDFWrite.putDString(b, "UDF Volume", 32, false);
            // Uint16 VolumeSequenceNumber;
            assert (b.position() == 56);
            b.putShort((short) 1);
            // Uint16 MaximumVolumeSequenceNumber;
            b.putShort((short) 1);
            // Uint16 InterchangeLevel;
            b.putShort((short) 2);
            // Uint16 MaximumInterchangeLevel;
            b.putShort((short) 2);
            // Uint32 CharacterSetList;
            b.putInt(1);
            // Uint32 MaximumCharacterSetList;
            b.putInt(1);
            // dstring VolumeSetIdentifier[128];
            UDFWrite.putDString(b, this.volumeSetIdentifier, 128, false);
            // struct charspec DescriptorCharacterSet;
            assert (b.position() == 200);
            UDFWrite.putCharSpecOSTACompressedUnicode(b);
            // struct charspec ExplanatoryCharacterSet;
            UDFWrite.putCharSpecOSTACompressedUnicode(b);
            // struct extent_ad VolumeAbstract;
            assert (b.position() == 328);
            UDFWrite.extentAd(b, 0, 0);
            // struct extent_ad VolumeCopyrightNotice;
            UDFWrite.extentAd(b, 0, 0);
            // struct EntityID ApplicationIdentifier;
            UDFWrite.zfill(b, 32);
            // struct timestamp RecordingDateandTime;
            UDFWrite.putTimestamp(b, this.recordingDateandTime);
            // struct EntityID ImplementationIdentifier;
            b.put(this.applicationIdentifier);
            // byte ImplementationUse[64];
            UDFWrite.zfill(b, 64);
            // Uint32 PredecessorVolumeDescriptorSequenceLocation;
            b.putInt(0);
            // Uint16 Flags;
            b.putShort((short) 0);
            // byte Reserved[22];
            UDFWrite.zfill(b, 22);
            // Write
            out.writep(UDFWrite.descriptorTag(b, (short) 1, out.nExtent, b.position()).array(), 0, b.position());
            assert (512 == b.position());
        }
        // struct ImpUseVolumeDescriptor
        {
            // struct tag DescriptorTag;
            b.clear().position(16);
            // Uint32 VolumeDescriptorSequenceNumber;
            b.putInt(1);
            // struct EntityID ImplementationIdentifier;
            UDFWrite.putEntityId(b, 0, new byte[] { '*', 'U', 'D', 'F', ' ', 'L', 'V', ' ', 'I', 'n', 'f', 'o' },
                    new byte[] { 2, 1 });
            // struct LVInformation ImplementationUse;
            {
                // LVICharset
                UDFWrite.putCharSpecOSTACompressedUnicode(b);
                // dstring LogicalVolumeIdentifier[128];
                UDFWrite.putDString(b, this.logicalVolumeIdentifier, 128, false);
                // dstring LVInfo1[36];x3
                UDFWrite.zfill(b, 36 * 3);
                // struct EntityID ImplementionID;
                b.put(this.applicationIdentifier);
                // byte ImplementationUse[128];
                UDFWrite.zfill(b, 128);
            }
            // Write
            out.writep(UDFWrite.descriptorTag(b, (short) 4, out.nExtent, b.position()).array(), 0, b.position());
            assert (512 == b.position());
        }
        // struct PartitionDescriptor
        {
            // struct tag DescriptorTag;
            b.clear().position(16);
            // Uint32 VolumeDescriptorSequenceNumber;
            b.putInt(2);
            // Uint16 PartitionFlags;
            b.putShort((short) 1);
            // Uint16 PartitionNumber;
            b.putShort((short) 0);
            // struct EntityID PartitionContents;
            UDFWrite.putEntityId(b, 0, new byte[] { '+', 'N', 'S', 'R', '0', '2' }, new byte[] { 2, 1 });
            // byte PartitionContentsUse[128];
            UDFWrite.zfill(b, 128);
            // Uint32 AccessType;
            b.putInt(1);
            // Uint32 PartitionStartingLocation;
            b.putInt((int) this.lbaUDFPartitionStart);
            // Uint32 PartitionLength;
            b.putInt((int) this.partionSize);
            // struct EntityID ImplementationIdentifier;
            b.put(this.applicationIdentifier);
            // byte ImplementationUse[128];
            UDFWrite.zfill(b, 128);
            // byte Reserved[156];
            UDFWrite.zfill(b, 156);
            // Write
            out.writep(UDFWrite.descriptorTag(b, (short) 5, out.nExtent, b.position()).array(), 0, b.position());
            assert (512 == b.position());
        }
        // struct LogicalVolumeDescriptor
        {
            // struct tag DescriptorTag;
            b.clear().limit(446).position(16);
            // Uint32 VolumeDescriptorSequenceNumber;
            b.putInt(3);
            // struct charspec DescriptorCharacterSet;
            UDFWrite.putCharSpecOSTACompressedUnicode(b);
            // dstring LogicalVolumeIdentifier[128];
            UDFWrite.putDString(b, this.logicalVolumeIdentifier, 128, false);
            // Uint32 LogicalBlockSize;
            b.putInt(out.blockSize);
            // struct EntityID DomainIdentifier;
            assert (b.position() == 216);
            b.put(ENTITYID_OSTA_COMPLIANT);
            // struct long_ad LogicalVolumeContentsUse;
            assert (b.position() == 248);
            b.putInt(out.blockSize * 2);
            UDFWrite.zfill(b, 12);
            // Uint32 MapTableLength;
            b.putInt(6);
            // Uint32 NumberofPartitionMaps;
            b.putInt(1);
            // struct EntityID ImplementationIdentifier;
            b.put(this.applicationIdentifier);
            // byte ImplementationUse[128];
            UDFWrite.zfill(b, 128);
            // struct extent_ad IntegritySequenceExtent;
            b.putInt(out.blockSize * 2).putInt((int) this.lbaIntegritySequence);
            // struct Type1PartitionMap PartitionMaps;
            {
                // Uint8 PartitionMapType;
                b.put((byte) 1);
                // Uint8 PartitionMapLength;
                b.put((byte) 6);
                // Uint16 VolumeSequenceNumber;
                b.putShort((short) 1);
                // Uint16 PartitionNumber;
                b.putShort((short) 0);

            }
            // Write
            out.writep(UDFWrite.descriptorTag(b, (short) 6, out.nExtent, b.position()).array(), 0, b.position());
            assert (446 == b.position());
        }
        // struct UnallocatedSpaceDesc // ISO 13346 3/10.8
        {
            // struct tag DescriptorTag;
            b.clear().position(16);
            // Uint32 VolumeDescriptorSequenceNumber;
            b.putInt(4);
            // Uint32 NumberofAllocationDescriptors;
            b.putInt(0);
            // Write
            out.writep(UDFWrite.descriptorTag(b, (short) 7, out.nExtent, b.position()).array(), 0, b.position());
            assert (24 == b.position());
        }
        UDFBuild.terminatingDescriptor(out);
        out.padBlocks(10);
    }

    void anchorVolumeDescriptorPointer(BlockSink out) throws IOException {
        System.err.printf("@%9d AVDP\t\n", out.nExtent);
        // struct AnchorVolumeDescriptorPointer // ISO 13346 3/10.2
        ByteBuffer b = out.getBuffer();
        // struct tag DescriptorTag;
        b.limit(512).position(16);
        // struct extent_ad MainVolumeDescriptorSequenceExtent;
        b.putInt(out.blockSize * 16).putInt((int) this.lbaMainVolumeDesc);
        // struct extent_ad ReserveVolumeDescriptorSequenceExtent;
        if (this.lbaReserveVolumeDesc > 0) {
            b.putInt(out.blockSize * 16).putInt((int) this.lbaReserveVolumeDesc);
        } else {
            b.putInt(0).putInt(0);
        }
        // byte Reserved[480];
        UDFWrite.zfill(b, 480);
        // Write
        out.writep(UDFWrite.descriptorTag(b, (short) 2, out.nExtent, b.position()).array(), 0, b.position());
        assert (512 == b.position());
    }

    void filesetDescriptor(BlockSink out, TreeNode root) throws IOException {
        System.err.printf("@%9d FSD\t\n", out.nExtent);
        // struct FileSetDescriptor { /* ISO 13346 4/14.1 */
        ByteBuffer b = out.getBuffer();
        // struct tag DescriptorTag;
        b.limit(512).position(16);
        // struct timestamp RecordingDateandTime;
        UDFWrite.putTimestamp(b, recordingDateandTime);
        // Uint16 InterchangeLevel;
        b.putShort((short) 3);
        // Uint16 MaximumInterchangeLevel;
        b.putShort((short) 3);
        // Uint32 CharacterSetList;
        b.putInt(1);
        // Uint32 MaximumCharacterSetList;
        b.putInt(1);
        // Uint32 FileSetNumber;
        b.putInt(0);
        // Uint32 FileSetDescriptorNumber;
        b.putInt(0);
        // struct charspec LogicalVolumeIdentifierCharacterSet;
        UDFWrite.putCharSpecOSTACompressedUnicode(b);
        // dstring LogicalVolumeIdentifier[128];
        UDFWrite.putDString(b, logicalVolumeIdentifier, 128, false);
        // struct charspec FileSetCharacterSet;
        UDFWrite.putCharSpecOSTACompressedUnicode(b);
        // dstring FileSetIdentifer[32];
        UDFWrite.putDString(b, fileSetIdentifier, 32, false);
        // dstring CopyrightFileIdentifier[32];
        UDFWrite.putDString(b, "copyright", 32, false);
        // dstring AbstractFileIdentifier[32];
        UDFWrite.putDString(b, "abstract", 32, false);
        // struct long_ad RootDirectoryICB;
        b.putInt(out.blockSize);
        b.putInt((int) (root.getData().auxA - this.lbaUDFPartitionStart)).putShort((short) 0);
        b.putShort((short) 0).putInt(0);
        // struct EntityID DomainIdentifier;
        b.put(ENTITYID_OSTA_COMPLIANT);
        // struct long_ad NextExtent;
        b.putInt(0).putInt(0).putInt(0).putInt(0);
        // byte Reserved[48];
        UDFWrite.zfill(b, 48);
        // };
        // Write
        out.writep(
                UDFWrite.descriptorTag(b, (short) 256, out.nExtent - this.lbaUDFPartitionStart, b.position()).array(),
                0, b.position());
        assert (512 == b.position());
        UDFBuild.terminatingDescriptor(out, out.nExtent - this.lbaUDFPartitionStart);
    }

    void pushEntry(LinkedList<DataDesc> extents, Node<Inode> node) {
        final Inode ino = node.getData();
        if ((ino.flag & 0x1) == 0) {
            ino.flag |= 0x1;
            extents.add(DataDesc.newEntry(node, extents.size()));
        }
    }

    void pushData(LinkedList<DataDesc> extents, Node<Inode> node) {
        final Inode ino = node.getData();
        if ((ino.flag & 0x2) == 0) {
            ino.flag |= 0x2;
            extents.add(DataDesc.newData(node, extents.size()));
        }
    }

    void pushExtents_ED(LinkedList<DataDesc> extents, Node<Inode> parent) {
        for (Node<Inode> child : parent) {
            pushEntry(extents, child); // put children's inode
        }
        for (Node<Inode> child : parent) {
            pushData(extents, child); // put children's data
        }
        for (Node<Inode> child : parent) {
            if (child.getData().isDirectory()) {
                pushExtents_ED(extents, child);
            }
        }
    }

    void writeExtent(BlockSink out, DataDesc ext) throws IOException {
        Node<Inode> cur = ext.node;
        Inode ino = cur.getData();
        String path = cur.getPath('/');
        assert (out.nLeft == 0);
        if (ext.isEntry()) {
            System.err.printf("@%9d e %16d %s\t\n", out.nExtent, ino.auxB, path);
            if (out.nStage > 0) {
                ino.auxA = out.nExtent++; // 1 block
                assert (ino.nlink > 0);
            } else {
                assert (ino.auxA == out.nExtent);
                assert ((ino.auxB == 0) || (ino.auxB > this.lbaUDFPartitionStart));
                UDFWriteFile.fileEntry(out, ino, (cur.getParent() == null) ? 0 : 16 + ext.seq,
                        this.applicationIdentifier, ino.auxA - this.lbaUDFPartitionStart,
                        ino.auxB - this.lbaUDFPartitionStart);
            }
            // !ext.isEntry()
        } else if (ino.isDirectory()) {
            System.err.printf("@%9d D %16d %s\t\n", out.nExtent, ino.size, path);
            long lbaBase = out.nExtent;
            long rbaBase = lbaBase - lbaUDFPartitionStart;
            long _size = 0;
            Node<Inode> parent = cur.getParent();
            if (parent == null) {// root
                parent = cur;
            }
            _size += fileItem(out, parent, null, rbaBase, parent.getData().auxA - lbaUDFPartitionStart);
            for (Node<Inode> child : cur) {
                _size += fileItem(out, child, child.getName(), rbaBase + (_size / out.blockSize),
                        child.getData().auxA - lbaUDFPartitionStart);
            }
            out.writep();
            if (out.nStage > 0) {
                assert (rbaBase > 0);
                assert (_size > 0);
                assert (0 == ino.auxB);
                ino.auxB = lbaBase;
                ino.size = _size;
            } else {
                assert (ino.auxB > this.lbaUDFPartitionStart);
                assert (ino.auxB == lbaBase);
                assert (_size == ino.size);
                assert (ino.size >= 0);
                assert (ino.size < (this.partionSize * out.blockSize));
            }
        } else {
            System.err.printf("@%9d F %16d %s\t\n", out.nExtent, ino.size, path);
            if (out.nStage > 0) {
                assert (ino.auxB == 0);
                assert (ino.size >= -1);
                ino.auxB = out.nExtent;
                if (ino.isManifest()) {
                    assert (addManifest);
                    if (true) {
                        Manifest.SinkCounter o = new Manifest.SinkCounter();
                        Manifest.write(o, cur.getRoot(), true);
                        ino.size = o.getCount();
                        // } else {
                        // Manifest.write(out, cur.getRoot());
                        // out.writep();
                    }
                }
                long nSizePadded = out.calcBlocks(ino.size);
                assert (nSizePadded >= 0);
                out.nExtent += nSizePadded;
            } else {
                assert (out.nExtent == ino.auxB);
                if (ino.isManifest()) {
                    Manifest.write(out, cur.getRoot(), false);
                    assert ((((out.nExtent - ino.auxB) * out.blockSize) + out.nLeft) == ino.size);
                    out.writep();
                    // }else if(ino.isSymLink()){

                } else {
                    MessageDigest md = addManifest ? Inode.getMessageDigest() : null;
                    UDFWriteFile.fileData(out, ino, out.getBuffer().array(), ino.size, md, userConsole);
                }
                assert ((out.nExtent - out.calcBlocks(ino.size)) == ino.auxB);
            }
        }
    }

    void partitionStart(BlockSink out) {
        System.err.printf("@%9d Partition Start\t\n", out.nExtent);
        if (out.nStage > 0) {
            this.lbaUDFPartitionStart = out.nExtent;
        } else {
            assert (this.lbaUDFPartitionStart == out.nExtent);
        }
    }

    void partitionEnd(BlockSink out) {
        System.err.printf("@%9d Partition End\t\n", out.nExtent);
        assert (out.nExtent > this.lbaUDFPartitionStart);
        if (out.nStage > 0) {
            this.partionSize = out.nExtent - this.lbaUDFPartitionStart;
        } else {
            assert ((out.nExtent - this.lbaUDFPartitionStart) == this.partionSize);
        }
    }

    static void padTo(BlockSink out, int n, String name) throws IOException {
        System.err.printf("@%9d %s %d blocks\t\n", out.nExtent, name == null ? "Pad" : name, n - out.nExtent);
        out.padUpTo(n);
    }

    void supplyTree(TreeNode root) throws IOException {
        final OffsetDateTime defaultTime = recordingDateandTime;
        Iterator<Node<Inode>> w = root.depthFirstIterator();
        while (w.hasNext()) {
            Node<Inode> cur = w.next();
            Inode ino = cur.getData();
            if (ino == null) {
                cur.setData(ino = new Inode.File(!cur.isLeaf()));
            }
            assert (ino.getFileType() != 0);
            if (cur.isLeaf()) {
                assert (!ino.isDirectory());
            } else {
                assert (ino.isDirectory());
            }
            // cur.descend(n -> System.err.print("/" + n.getName()));
            // System.err.println();
            // Supply time
            if (ino.mtime == null || ino.atime == null || ino.ctime == null) {
                if (ino.isDirectory()) {
                    Instant mtime = null, ctime = null, atime = null;
                    for (Node<Inode> child : cur) {
                        Inode cIno = child.getData();
                        if (cIno.mtime != null && defaultTime != cIno.mtime) {
                            Instant ts;
                            if (cIno.mtime instanceof OffsetDateTime) {
                                ts = ((OffsetDateTime) (cIno.mtime)).toInstant();
                            } else if (cIno.mtime instanceof FileTime) {
                                ts = ((FileTime) (cIno.mtime)).toInstant();
                            } else {
                                ts = (Instant) cIno.mtime;
                            }
                            if ((ts != null) && (mtime == null || ts.compareTo(mtime) > 0)) {
                                mtime = ts;
                            }
                        }
                        if (cIno.ctime != null && defaultTime != cIno.ctime) {
                            Instant ts;
                            if (cIno.ctime instanceof OffsetDateTime) {
                                ts = ((OffsetDateTime) (cIno.ctime)).toInstant();
                            } else if (cIno.ctime instanceof FileTime) {
                                ts = ((FileTime) (cIno.ctime)).toInstant();
                            } else {
                                ts = (Instant) cIno.ctime;
                            }
                            if ((ts != null) && (ctime == null || ts.compareTo(ctime) > 0)) {
                                ctime = ts;
                            }
                        }
                        if (cIno.atime != null && defaultTime != cIno.atime) {
                            Instant ts;
                            if (cIno.atime instanceof OffsetDateTime) {
                                ts = ((OffsetDateTime) (cIno.atime)).toInstant();
                            } else if (cIno.atime instanceof FileTime) {
                                ts = ((FileTime) (cIno.atime)).toInstant();
                            } else {
                                ts = (Instant) cIno.atime;
                            }
                            if ((ts != null) && (atime == null || ts.compareTo(atime) > 0)) {
                                atime = ts;
                            }
                        }
                    }
                    if (mtime != null && ino.mtime == null) {
                        ino.mtime = mtime;
                    }
                    if (atime != null && ino.atime == null) {
                        ino.atime = atime;
                    }
                    if (ctime != null && ino.ctime == null) {
                        ino.ctime = ctime;
                    }
                }
                if (ino.mtime == null) {
                    ino.mtime = defaultTime;
                }
                if (ino.atime == null) {
                    ino.atime = defaultTime;
                }
                if (ino.ctime == null) {
                    ino.ctime = defaultTime;
                }
            }
            // System.err.printf("%s %s %s\n", ino.mtime, ino.ctime, ino.atime);
            if (ino.isDirectory()) {
                Node<Inode> parent = cur.getParent();
                if (parent != null) {
                    parent.getData().nlink++;
                }
                this.nDirectories++;
            } else {
                this.nFiles++;
                if (ino.isManifest()) {

                }
            }
            ino.nlink++;
        }
    }

    void build(BlockSink out, TreeNode root, String outfile, boolean compactImage) throws IOException {
        out.getBuffer().order(ByteOrder.LITTLE_ENDIAN);

        OffsetDateTime now = OffsetDateTime.now();
        if (logicalVolumeIntegrityDescTime == null) {
            logicalVolumeIntegrityDescTime = now;
        }
        if (recordingDateandTime == null) {
            recordingDateandTime = now;
        }

        supplyTree(root);
        // prep vars
        {
            // Set VolumeSetIdentifier
            // - CS0 representation of unique hex number in first 8 character positions, UDF
            // 2.2.2.5
            if (volumeSetIdentifier == null || volumeSetIdentifier.isEmpty()) {
                Random rnd = new Random();
                volumeSetIdentifier = String.format("%08X UDF Volume Set", rnd.nextInt());
            }
            // Set LogicalVolumeIdentifier
            if (logicalVolumeIdentifier == null || logicalVolumeIdentifier.isEmpty()) {
                String name = root.getName();
                if (name != null && !name.isEmpty()) {
                    logicalVolumeIdentifier = name;
                }
            }
            if (logicalVolumeIdentifier == null || logicalVolumeIdentifier.isEmpty()) {
                logicalVolumeIdentifier = String.format("UDF%S",
                        Long.toString(now.toEpochSecond(), Character.MAX_RADIX));
            }
            // Set FileSetIdentifier
            if (fileSetIdentifier == null || fileSetIdentifier.isEmpty()) {
                fileSetIdentifier = "UDF Volume Set";
            }
            // Set ApplicationIdentifier
            String appId = "*Microsoft CDIMAGE UDF";
            if (applicationIdentifier == null) {
                ByteBuffer b = ByteBuffer.allocate(32);
                UDFWrite.entityId(b, appId);
                applicationIdentifier = b.array();
            }

            System.err.printf("%24s: %s\n", "LogicalVolumeIdentifier", logicalVolumeIdentifier);
            System.err.printf("%24s: %s\n", "VolumeSetIdentifier", volumeSetIdentifier);
            System.err.printf("%24s: %s\n", "FileSetIdentifier", fileSetIdentifier);
            System.err.printf("%24s: %s\n", "ApplicationIdentifier", appId);
            System.err.printf("%24s: %s\n", "RecordTime", recordingDateandTime);
        }
        // prep tree
        LinkedList<DataDesc> extents = new LinkedList<>();
        {
            pushEntry(extents, root);
            pushData(extents, root);
            pushExtents_ED(extents, root);

            if (addManifest) {
                Optional<DataDesc> od = extents.stream().filter((DataDesc x) -> {
                    return !x.isEntry() && x.node.getData().isManifest();
                }).findAny();
                if (od.isPresent()) {
                    DataDesc d = od.get();
                    if (extents.remove(d)) {
                        extents.offerLast(d);
                    }
                }
            }
        }
        // build image
        out.nStage = 1;
        // -1 Write (One pass)
        // 0 Write
        // 1 Prepare
        long volumeSize = 0;
        do {
            if (out.nStage > 0) {
                System.err.print("\nCalculating ...\n\n");
            } else if (outfile == null || outfile.isEmpty()) {
                System.out.println(out.nExtent);
                break;
            } else if (outfile.equals("NUL")) {
                System.err.print("\nWriting to nowhere ...\n\n");
            } else if (outfile.equals("-")) {
                System.err.print("\nWriting to STDOUT...\n\n");
                out.setOutputStream(System.out);
                // } else if (outfile.charAt(0) == '|') {
                // String cmd = outfile.substring(1);
                // System.err.printf("\nPiping to: %s\n\n", cmd);
            } else {
                System.err.printf("\nWriting to: %s\n\n", outfile);
                out.setOutputStream(new FileOutputStream(outfile));
            }

            out.reset();
            if (out.nStage <= 0) {
                out.nExtent = 0;
            }
            UDFBuild.systemArea(out);
            UDFBuild.volumeRecognitionArea(out);
            logicalVolumeIntegrityDesc(out);

            // if (true) {
            if (!compactImage) {
                padTo(out, 256 - 16, null);
                volumeDescriptorSequence(out, true);
                // padTo(out, 256);
                anchorVolumeDescriptorPointer(out);
                partitionStart(out);
                filesetDescriptor(out, root);
                for (DataDesc desc : extents) {
                    writeExtent(out, desc);
                }
                partitionEnd(out);
                volumeDescriptorSequence(out, false);
                anchorVolumeDescriptorPointer(out);
            } else {
                partitionStart(out);
                filesetDescriptor(out, root);
                long padBlocks = 256 - 16 - out.nExtent;
                // compact
                LinkedList<DataDesc> extents2 = new LinkedList<>();
                Iterator<DataDesc> li = extents.iterator();
                while ((padBlocks > 0) && li.hasNext()) {
                    DataDesc cur = li.next();
                    if (cur.isEntry()) {
                        writeExtent(out, cur);
                        padBlocks--;
                        continue;
                    } else {
                        // long size = cur.node.getData().size;
                        // if (size > 0) {
                        // long blocks = out.calcBlocks(size);
                        // if (blocks <= padBlocks) {
                        // writeExtent(out, cur);
                        // padBlocks -= blocks;
                        // continue;
                        // }
                        // }
                    }
                    extents2.add(cur);
                }
                padTo(out, 256 - 16, null);

                volumeDescriptorSequence(out, true);
                anchorVolumeDescriptorPointer(out);
                if (!extents2.isEmpty()) {
                    Iterator<DataDesc> li2 = extents2.iterator();
                    while (li2.hasNext()) {
                        writeExtent(out, li2.next());
                    }
                }

                while (li.hasNext()) {
                    writeExtent(out, li.next());
                }

                partitionEnd(out);
                volumeDescriptorSequence(out, false);
                anchorVolumeDescriptorPointer(out);
            }
            // } else {
            // partitionStart(out);
            // filesetDescriptor(out, root);
            // for (DataDesc desc : extents) {
            // writeExtent(out, desc);
            // }
            // partitionEnd(out);
            // // volumeDescriptorSequence(out, true);
            // // anchorVolumeDescriptorPointer(out);
            // anchorVolumeDescriptorPointer(out);
            // volumeDescriptorSequence(out, true);
            // out.padBlocks((256 - 1) - 16 - 16);
            // volumeDescriptorSequence(out, false);
            // anchorVolumeDescriptorPointer(out);
            // }
            assert (out.nExtent <= 2147483647);
            assert (out.nExtent > (256 + 1));
            if (out.nStage <= 0) {
                System.err.print("\nWriting done\n\n");
                assert ((out.nExtent * out.blockSize) == volumeSize);
                assert ((16 + extents.size()) == this.nLastUniqueID);
            } else {
                this.nLastUniqueID = 16 + extents.size();
                volumeSize = out.nExtent * out.blockSize;
                System.err.printf("%20s: %s\n", "MainVolumeDescLBA", lbaMainVolumeDesc);
                System.err.printf("%20s: %s\n", "ReserveVolumeDescLBA", lbaReserveVolumeDesc);
                System.err.printf("%20s: %s\n", "IntegritySequenceLBA", lbaIntegritySequence);
                System.err.printf("%20s: %s\n", "PartitionStartLBA", lbaUDFPartitionStart);
                System.err.printf("%20s: %s\n", "RootDirectoryLBA", root.getData().auxA);
                System.err.printf("%20s: %s\n", "PartionSize", partionSize);
                System.err.printf("%20s: %s\n", "BlockSize", out.blockSize);
                System.err.printf("%20s: %s\n", "VolumeSize", volumeSize);
            }
            System.err.printf("Entries: Files %d, Directories %d\n", this.nFiles, this.nDirectories);
            System.err.printf("Image Size %d sectors, %s, %s wasted\n", out.nExtent, BinSize.binsizef(volumeSize),
                    BinSize.binsizef(out.nWasted));
        } while (out.nStage-- > 0);
    }
}
