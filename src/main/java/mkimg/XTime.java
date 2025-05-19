/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mkimg;

import java.nio.ByteBuffer;
import java.time.OffsetDateTime;

/**
 *
 * @author Muning
 */
public class XTime {

    static public long of(int year, int mon, int day, int hour, int min, int sec, int offset) {
        long x = 0
                | ((long) (Short.toUnsignedInt((short) year) & 0x7FFF) << 38)
                | ((long) (Byte.toUnsignedInt((byte) mon) & 0xF)) << 34
                | ((long) (Byte.toUnsignedInt((byte) day) & 0x1F)) << 29
                | ((long) (Byte.toUnsignedInt((byte) hour) & 0x1F)) << 24
                | ((long) (Byte.toUnsignedInt((byte) min) & 0x3F)) << 18
                | ((long) (Byte.toUnsignedInt((byte) sec) & 0x3F)) << 12
                | ((long) (Short.toUnsignedInt((short) offset) & 0x7FFF));
        return 0;
    }

    static public boolean isValid(long x) {
        return (0 == ((x) >> 53));
    }

    /*
    static public OffsetDateTime toOffsetDateTime(long x) {
        long y = ((x) >> 38) & 0x7FFF;
        if (y > ((1 << 14) - 1)) {
            y -= (1 << 15);
        }
        byte l = (byte) (((x) >> 34) & 0xF);
        byte d = (byte) (((x) >> 29) & 0x1F);
        byte h = (byte) (((x) >> 24) & 0x1F);
        byte m = (byte) (((x) >> 18) & 0x3F);
        byte s = (byte) (((x) >> 12) & 0x3F);
        short o = (short) ((x) & 0xfff);
        if (o > ((1 << 11) - 1)) {
            o -= (1 << 12);
        }
        return null;
    }*/

    public static void putTimestamp(long x, ByteBuffer b) {
        long y = ((x) >> 38) & 0x7FFF;
        if (y > ((1 << 14) - 1)) {
            y -= (1 << 15);
        }
        byte l = (byte) (((x) >> 34) & 0xF);
        byte d = (byte) (((x) >> 29) & 0x1F);
        byte h = (byte) (((x) >> 24) & 0x1F);
        byte m = (byte) (((x) >> 18) & 0x3F);
        byte s = (byte) (((x) >> 12) & 0x3F);
        short o = (short) ((x) & 0xfff);
        if (o > ((1 << 11) - 1)) {
            o -= (1 << 12);
        }

        b.putShort((short) ((1 << 12) | o)); // Uint16 TypeAndTimezone
        b.putShort((short) y); // Uint16 TypeAndTimezone
        b.put((byte) l); // Uint8 Month
        b.put((byte) d); // Uint8 Day;
        b.put((byte) h); // Uint8 Hour;
        b.put((byte) m); // Uint8 Minute;
        b.put((byte) s); // Uint8 Second;
        b.put((byte) 0); // Uint8 Centiseconds;
        b.put((byte) 0); // Uint8 HundredsofMicroseconds;
        b.put((byte) 0); // Uint8 Microseconds;
    }
}
