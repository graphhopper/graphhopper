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
package com.graphhopper.reader.osm;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.pbf.PbfReader;

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.lang.reflect.Constructor;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * Interface for reading OSM data from various file formats.
 */
public interface OSMInput extends AutoCloseable {
    ReaderElement getNext() throws XMLStreamException;

    /**
     * Opens an OSM file, automatically detecting the format (PBF or XML) based on file contents.
     *
     * @param file         the OSM file to open
     * @param workerThreads number of worker threads for PBF parsing (ignored for XML)
     * @param skipOptions  options to skip certain element types during parsing
     * @return an OSMInput instance for the detected format
     */
    static OSMInput open(File file, int workerThreads, SkipOptions skipOptions) throws IOException, XMLStreamException {
        DecodedInput decoded = decode(file);
        if (decoded.isBinary) {
            return new PbfReader(decoded.inputStream, workerThreads, skipOptions).start();
        } else {
            return new OSMXmlInput(decoded.inputStream).open();
        }
    }

    private static DecodedInput decode(File file) throws IOException {
        final String name = file.getName();

        InputStream ips;
        try {
            ips = new BufferedInputStream(new FileInputStream(file), 50000);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        ips.mark(10);

        // check file header
        byte[] header = new byte[6];
        if (ips.read(header) < 0)
            throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());

        if (header[0] == 31 && header[1] == -117) {
            // GZIP
            ips.reset();
            return new DecodedInput(new GZIPInputStream(ips, 50000), false);
        } else if (header[0] == 0 && header[1] == 0 && header[2] == 0
                && header[4] == 10 && header[5] == 9
                && (header[3] == 13 || header[3] == 14)) {
            // PBF
            ips.reset();
            return new DecodedInput(ips, true);
        } else if (header[0] == 'P' && header[1] == 'K') {
            // ZIP
            ips.reset();
            ZipInputStream zip = new ZipInputStream(ips);
            zip.getNextEntry();
            return new DecodedInput(zip, false);
        } else if (name.endsWith(".osm") || name.endsWith(".xml")) {
            // Plain XML
            ips.reset();
            return new DecodedInput(ips, false);
        } else if (name.endsWith(".bz2") || name.endsWith(".bzip2")) {
            // BZIP2 - requires optional dependency
            String clName = "org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream";
            try {
                @SuppressWarnings("unchecked")
                Class<InputStream> clazz = (Class<InputStream>) Class.forName(clName);
                ips.reset();
                Constructor<InputStream> ctor = clazz.getConstructor(InputStream.class, boolean.class);
                return new DecodedInput(ctor.newInstance(ips, true), false);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot instantiate " + clName, e);
            }
        } else {
            throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());
        }
    }

    /**
     * Helper class to return both the decoded input stream and whether it's binary (PBF) format.
     */
    class DecodedInput {
        final InputStream inputStream;
        final boolean isBinary;

        DecodedInput(InputStream inputStream, boolean isBinary) {
            this.inputStream = inputStream;
            this.isBinary = isBinary;
        }
    }
}
