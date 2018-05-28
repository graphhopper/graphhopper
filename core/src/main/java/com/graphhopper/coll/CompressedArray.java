/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
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

import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.storage.VLongStorage;
import com.graphhopper.util.Helper;
import com.graphhopper.util.shapes.GHPoint;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Stores the entries in compressed segments. The methods de(compress) are taken from Lucene
 * CompressionTools. Before accessing the stored values be sure you called flush.
 * <p>
 *
 * @author Peter Karich
 */
public class CompressedArray {
    private int compressionLevel = 5;
    private VLongStorage currentWriter;
    private int currentEntry = 0;
    private List<byte[]> segments;
    private int entriesPerSegment;
    private int approxBytesPerEntry;
    private SpatialKeyAlgo algo;

    public CompressedArray() {
        this(100, 200, 4);
    }

    /**
     * @param _segments           initialize with this number of segments
     * @param entriesPerSeg       a static number which sets the entries per segment
     * @param approxBytesPerEntry an *approximate* number (as entries can have different lengths)
     */
    public CompressedArray(int _segments, int entriesPerSeg, int approxBytesPerEntry) {
        if (entriesPerSeg < 1) {
            throw new IllegalArgumentException("at least one entry should be per segment");
        }
        this.entriesPerSegment = entriesPerSeg;
        this.approxBytesPerEntry = approxBytesPerEntry;
        segments = new ArrayList<>(_segments);
        algo = new SpatialKeyAlgo(63);
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

    public CompressedArray setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
        return this;
    }

    public void write(double lat, double lon) {
        try {
            if (currentWriter == null)
                currentWriter = new VLongStorage(entriesPerSegment * approxBytesPerEntry);

            long latlon = algo.encode(new GHPoint(lat, lon));
            // we cannot use delta encoding as vlong does not support negative numbers
            // but compression of vlong is much more efficient than directly storing the integers
            currentWriter.writeVLong(latlon);
            currentEntry++;
            if (currentEntry >= entriesPerSegment) {
                flush();
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public GHPoint get(long index) {
        int segmentNo = (int) (index / entriesPerSegment);
        int entry = (int) (index % entriesPerSegment);
        try {
            if (segmentNo >= segments.size()) {
                return null;
            }
            byte[] bytes = segments.get(segmentNo);
            VLongStorage store = new VLongStorage(decompress(bytes));
            long len = store.getLength();
            for (int i = 0; store.getPosition() < len; i++) {
                long latlon = store.readVLong();
                if (i == entry) {
                    GHPoint point = new GHPoint();
                    algo.decode(latlon, point);
                    return point;
                }
            }
            return null;
        } catch (ArrayIndexOutOfBoundsException ex) {
            throw new RuntimeException("index " + index + "=> segNo:" + segmentNo + ", entry=" + entry
                    + ", segments:" + segments.size(), ex);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void flush() {
        if (currentWriter == null) {
            return;
        }
        try {
            currentWriter.trimToSize();
            byte[] input = currentWriter.getBytes();
            segments.add(compress(input, 0, input.length, compressionLevel));
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
}
