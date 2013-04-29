package com.graphhopper.bzip2plugin;

import com.graphhopper.util.Helper;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;

/**
 * Simple bzip2 uncompression. TODO integrate with OSMReader!
 */
public class Bzip2 {

    public static void main(String[] args) throws IOException {
        if (args.length == 0)
            throw new IllegalArgumentException("You need to specify the bz2 file!");

        String fromFile = args[0];
        if (!fromFile.endsWith(".bz2"))
            throw new IllegalArgumentException("You need to specify a bz2 file! But was:" + fromFile);
        String toFile = Helper.pruneFileEnd(fromFile);

        FileInputStream in = new FileInputStream(fromFile);
        FileOutputStream out = new FileOutputStream(toFile);
        BZip2CompressorInputStream bzIn = new BZip2CompressorInputStream(in);
        try {
            final byte[] buffer = new byte[1024 * 8];
            int n = 0;
            while (-1 != (n = bzIn.read(buffer))) {
                out.write(buffer, 0, n);
            }
        } finally {
            out.close();
            bzIn.close();
        }
    }
}
