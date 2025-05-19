/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package mkimg;

/**
 * @author SYSTEM
 */
public class BinSize {

    static short[] binsize(long value) {
        byte units[] = { 0, 'k', 'M', 'G', 'T', 'P', 'E', 'Z', 'Y' };
        short frct = 0;
        short unit = 0;
        while (value > 10000) {
            frct = (short) (((value % (1 << 10)) * 100) >> 10); // (value%1024)*(100/1024)
            value = value >> 10; // value/(1**1024)
            ++unit;

        }
        return new short[] { (short) value, frct, units[unit] };
    }

    static String binsizef(long value) {
        short a[] = binsize(value);
        if (a[2] == 0) {
            return String.format("%3d.%02dB", a[0], a[1]);
        } else {
            return String.format("%3d.%02d%ciB", a[0], a[1], (char) a[2]);
        }
    }
}
