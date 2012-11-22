/*
 *  Copyright 2012 Peter Karich info@jetsli.de
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.coll;

import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.CoordTrig;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

/**
 * The methods de(compress) are taken from lucene CompressionTools
 *
 * @author Peter Karich
 */
public class CompressedArray {

    private DataOutputStream currentWriter;
    private ByteArrayOutputStream currentBao;
    private int currentEntry = -1;
    private List<byte[]> segments;
    private int entriesPerSegment;
    private int approxBytesPerEntry;

    public CompressedArray() {
        this(100, 200, 4);
    }

    /**
     * @param _segments initialize with this number of segments
     * @param entriesPerSeg a static number which sets the entries per segment
     * @param approxBytesPerEntry an *approximative* number (as entries can have different lengths)
     */
    public CompressedArray(int _segments, int entriesPerSeg, int approxBytesPerEntry) {
        if (entriesPerSeg < 1)
            throw new IllegalArgumentException("at least one entry should be per segment");
        this.entriesPerSegment = entriesPerSeg;
        this.approxBytesPerEntry = approxBytesPerEntry;
        segments = new ArrayList<byte[]>(_segments);
    }

    public void write(double lat, double lon) {
        try {
            if (currentWriter == null) {
                currentWriter = new DataOutputStream(new GZIPOutputStream(
                        currentBao = new ByteArrayOutputStream(entriesPerSegment * approxBytesPerEntry)));
            }

            currentEntry++;
            // last entry?
//            if (currentEntry + 1 == entriesPerSegment)
//                compressor.finish();
//            compressor.setInput(data, 0, data.length);

            currentWriter.writeFloat((float) lat);
            currentWriter.writeFloat((float) lon);

            if (currentEntry >= entriesPerSegment)
                flush();

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public CoordTrig getEntry(long index) {
        int segmentNo = (int) (index / entriesPerSegment);
        int entry = (int) (index % entriesPerSegment);
        try {
            byte[] bytes = segments.get(segmentNo);
            DataInputStream stream = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(bytes)));
            for (int i = 0; i <= entry; i++) {
                float lat = stream.readFloat();
                float lon = stream.readFloat();
                if (i == entry) {
                    CoordTrig coord = new CoordTrig();
                    coord.lat = lat;
                    coord.lon = lon;
                    return coord;
                }
            }

            return null;
        } catch (EOFException ex) {
            return null;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void flush() {
        if (currentWriter == null)
            return;
        try {
            currentWriter.flush();
            currentWriter.close();
//                compressor.end();
            segments.add(currentBao.toByteArray());
            currentWriter = null;
            currentEntry = 0;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public float calcMemInMB() {
        long bytes = 0;
        for (int i = 0; i < segments.size(); i++) {
            bytes += segments.get(i).length;
        }
        return (float) (segments.size() * 4 + bytes) / Helper.MB;
    }

    /**
     * Compresses the specified byte range using the specified compressionLevel (constants are
     * defined in java.util.zip.Deflater).
     */
    public static byte[] compress(byte[] value, int offset, int length, int compressionLevel) {
        /* Create an expandable byte array to hold the compressed data.
         * You cannot use an array that's the same size as the orginal because
         * there is no guarantee that the compressed data will be smaller than
         * the uncompressed data. */
        ByteArrayOutputStream bos = new ByteArrayOutputStream(length);
        Deflater compressor = new Deflater();
        try {
            compressor.setLevel(compressionLevel);
            compressor.setInput(value, offset, length);
            compressor.finish();
            final byte[] buf = new byte[1024];
            while (!compressor.finished()) {
                int count = compressor.deflate(buf);
                bos.write(buf, 0, count);
            }
        } finally {
            compressor.end();
        }
        return bos.toByteArray();
    }

    /**
     * Decompress the byte array previously returned by compress
     */
    public static byte[] decompress(byte[] value) throws DataFormatException {
        // Create an expandable byte array to hold the decompressed data
        ByteArrayOutputStream bos = new ByteArrayOutputStream(value.length);
        Inflater decompressor = new Inflater();
        try {
            decompressor.setInput(value);
            final byte[] buf = new byte[1024];
            while (!decompressor.finished()) {
                int count = decompressor.inflate(buf);
                bos.write(buf, 0, count);
            }
        } finally {
            decompressor.end();
        }

        return bos.toByteArray();
    }
}
