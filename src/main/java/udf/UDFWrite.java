package udf;

import java.nio.ByteBuffer;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;

public class UDFWrite {

    /* ECMA-167 4/14.6.6 */
    static final byte UDF_ICBTAG_FILETYPE_DIRECTORY = 4;
    static final byte UDF_ICBTAG_FILETYPE_BYTESEQ = 5;
    static final byte UDF_ICBTAG_FILETYPE_SYMLINK = 12;
    static final byte UDF_ICBTAG_FLAG_ARCHIVE = 32;
    /* TR/71 3.5.4 */
    static final short UDF_FILEENTRY_PERMISSION_OX = 1;
    static final short UDF_FILEENTRY_PERMISSION_OR = 4;
    static final short UDF_FILEENTRY_PERMISSION_GX = 32;
    static final short UDF_FILEENTRY_PERMISSION_GR = 128;
    static final short UDF_FILEENTRY_PERMISSION_UX = 1024;
    static final short UDF_FILEENTRY_PERMISSION_UR = 4096;
    /* ECMA-167 4/14.4.3 */
    static final byte UDF_FILE_CHARACTERISTIC_HIDDEN = 1;
    static final byte UDF_FILE_CHARACTERISTIC_DIRECTORY = 2;
    static final byte UDF_FILE_CHARACTERISTIC_DELETED = 4;
    static final byte UDF_FILE_CHARACTERISTIC_PARENT = 8;
//

    public static ByteBuffer descriptorTag(ByteBuffer b, short tid, long lba, int len) {
        // Uint16 TagIdentifier;
        b.putShort(0, tid);
        b.putShort(2, (short) 2); //  Uint16 DescriptorVersion;
        b.put(4, (byte) 0); //   Uint8 TagChecksum;
        b.put(5, (byte) 0); //  byte Reserved;
        b.putShort(6, (short) 0); //  Uint16 TagSerialNumber;
        b.putShort(8, (short) CrcCCITT.calculate(b.array(), 16, b.position() - 16)); // Uint16 DescriptorCRC;
        b.putShort(10, (short) (b.position() - 16)); //  Uint16 DescriptorCRCLength;
        b.putInt(12, (int) lba); //  Uint32 TagLocation;
        int chksum = 0;
        byte[] buf = b.array();
        for (int i = 0; i < 4; i++) {
            chksum += buf[i] & 255;
        }
        for (int i = 5; i < 16; i++) {
            chksum += buf[i] & 255;
        }
        buf[4] = (byte) chksum;
//        System.err.printf("Descriptor %d @%d #%d\n", tid, lba, len);
        return b;
    }
    final static byte[] OCU = new byte[]{'O', 'S', 'T', 'A', ' ', 'C', 'o', 'm', 'p', 'r', 'e', 's', 's', 'e', 'd', ' ', 'U', 'n', 'i', 'c', 'o', 'd', 'e'};

    public static void putCharSpecOSTACompressedUnicode(ByteBuffer b) {
        int n = 64 - (OCU.length + 1);
        b.put((byte) 0);
        b.put(OCU);
        while (n-- > 0) {
            b.put((byte) 0);
        }
    }

    public static void putDString(ByteBuffer b, String s, final int len, boolean compact) {
        if (s.isEmpty()) {
            zfill(b, len);
            return;
        }
        int n, j;
        final int l = s.length(), m = b.position();
        b.mark();
        n = len - 2;
        j = 0;
        b.put((byte) 8);
        while ((j < l) && (n > 0)) {
            char c = s.charAt(j++);
            if (c > 255) {
                b.reset();
                n = len - 2;
                j = 0;
                b.put((byte) 16);
                while ((j < l) && (n > 0)) {
                    c = s.charAt(j++);
                    if (c < 0x10000) {
                        b.put((byte) ((c & 0xff00) >> 8));
                        n--;
                        if (n > 0) {
                            b.put((byte) ((c & 0x00FF)));
                            n--;
                        }
                    } else {
                        int L = ((c) >> 10) + 0xd7c0;
                        b.put((byte) ((L & 0xff00) >> 8));
                        n--;
                        if (n > 0) {
                            b.put((byte) ((L & 0x00FF)));
                            n--;
                            if (n > 0) {
                                int T = ((c) & 0x3ff) | 0xdc00;
                                b.put((byte) ((T & 0xff00) >> 8));
                                n--;
                                if (n > 0) {
                                    b.put((byte) ((T & 0x00FF)));
                                    n--;
                                }
                            }
                        }
                    }
                }
                break;
            }
            b.put((byte) c);
            n--;
        }
        j = (b.position() - m);
        assert (b.get(m) == 16 || b.get(m) == 8);
        if (!compact) {
            while (n-- > 0) {
                b.put((byte) 0);
            }
//        System.err.printf("DString %d, %d\n", (b.position() - m) , (len - 1));
            assert ((b.position() - m) == (len - 1));
            b.put((byte) j);
            assert ((b.position() - m) == len);
        }
    }

    public static void extentAd(ByteBuffer b, long pos, long len) {
        b.putInt((int) len);
        b.putInt((int) pos);
    }

    public static void putTimestamp(ByteBuffer b, OffsetDateTime time) {
        b.putShort((short) ((1 << 12) | (time.getOffset().getTotalSeconds() / 60))); // Uint16 TypeAndTimezone
        b.putShort((short) time.getYear()); // Uint16 TypeAndTimezone
        b.put((byte) time.getMonthValue()); // Uint8 Month
        b.put((byte) time.getDayOfMonth()); // Uint8 Day;
        b.put((byte) time.getHour()); // Uint8 Hour;
        b.put((byte) time.getMinute()); // Uint8 Minute;
        b.put((byte) time.getSecond()); // Uint8 Second;
        b.put((byte) 0); // Uint8 Centiseconds;
        b.put((byte) 0); // Uint8 HundredsofMicroseconds;
        b.put((byte) 0); // Uint8 Microseconds;
    }

    public static void putTimestamp(ByteBuffer b, Object o) {
        if (o instanceof OffsetDateTime) {
            putTimestamp(b, (OffsetDateTime) o);
        } else if (o instanceof FileTime) {
            putTimestamp(b, OffsetDateTime.ofInstant(((FileTime) o).toInstant(), ZoneId.systemDefault()));
        } else {
            putTimestamp(b, OffsetDateTime.ofInstant((Instant) o, ZoneId.systemDefault()));
        }
    }

    public static void entityId(ByteBuffer b, String id) {
        putEntityId(b, 0, id.getBytes(), new byte[]{});
    }

    public static void putEntityId(ByteBuffer b, int flag, byte[] id, byte[] suffix) {
        b.put((byte) flag);
        int n = id.length;
        if (n < 23) {
            if (n > 0) {
                b.put(id, 0, n);
            }
            while (n++ < 23) {
                b.put((byte) 0);
            }
        } else {
            b.put(id, 0, 23);
        }
        n = suffix.length;
        if (n < 8) {
            if (n > 0) {
                b.put(suffix, 0, n);
            }
            while (n++ < 8) {
                b.put((byte) 0);
            }
        } else {
            b.put(suffix, 0, 8);
        }
    }

    public static void zfill(ByteBuffer b, int len) {
        final int p = b.position();
        final int n = p + len;
        Arrays.fill(b.array(), p, n, (byte) 0);
        b.position(n);
    }
}
