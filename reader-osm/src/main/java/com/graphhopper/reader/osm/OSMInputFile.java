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
import com.graphhopper.reader.osm.pbf.Sink;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.lang.reflect.Constructor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * A readable OSM file.
 * <p>
 *
 * @author Nop
 */
public class OSMInputFile implements Sink, OSMInput {
    private final InputStream bis;
    private final BlockingQueue<ReaderElement> itemQueue;
    Thread pbfReaderThread;
    private boolean eof;
    // for xml parsing
    private XMLStreamReader parser;
    // for pbf parsing
    private boolean binary = false;
    private boolean hasIncomingData;
    private int workerThreads = -1;
    private OSMFileHeader fileheader;

    public OSMInputFile(File file) throws IOException {
        bis = decode(file);
        itemQueue = new LinkedBlockingQueue<>(50_000);
    }

    public OSMInputFile open() throws XMLStreamException {
        if (binary) {
            openPBFReader(bis);
        } else {
            openXMLStream(bis);
        }
        return this;
    }

    /**
     * Currently on for pbf format. Default is number of cores.
     */
    public OSMInputFile setWorkerThreads(int num) {
        workerThreads = num;
        return this;
    }

    @SuppressWarnings("unchecked")
    private InputStream decode(File file) throws IOException {
        final String name = file.getName();

        InputStream ips = null;
        try {
            ips = new BufferedInputStream(new FileInputStream(file), 50000);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        ips.mark(10);

        // check file header
        byte header[] = new byte[6];
        if (ips.read(header) < 0)
            throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());

        /*     can parse bz2 directly with additional lib
         if (header[0] == 'B' && header[1] == 'Z')
         {
         return new CBZip2InputStream(ips);
         }
         */
        if (header[0] == 31 && header[1] == -117) {
            ips.reset();
            return new GZIPInputStream(ips, 50000);
        } else if (header[0] == 0 && header[1] == 0 && header[2] == 0
                && header[4] == 10 && header[5] == 9
                && (header[3] == 13 || header[3] == 14)) {
            ips.reset();
            binary = true;
            return ips;
        } else if (header[0] == 'P' && header[1] == 'K') {
            ips.reset();
            ZipInputStream zip = new ZipInputStream(ips);
            zip.getNextEntry();

            return zip;
        } else if (name.endsWith(".osm") || name.endsWith(".xml")) {
            ips.reset();
            return ips;
        } else if (name.endsWith(".bz2") || name.endsWith(".bzip2")) {
            String clName = "org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream";
            try {
                Class clazz = Class.forName(clName);
                ips.reset();
                Constructor<InputStream> ctor = clazz.getConstructor(InputStream.class, boolean.class);
                return ctor.newInstance(ips, true);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot instantiate " + clName, e);
            }
        } else {
            throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());
        }
    }

    private void openXMLStream(InputStream in)
            throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        parser = factory.createXMLStreamReader(in, "UTF-8");

        int event = parser.next();
        if (event != XMLStreamConstants.START_ELEMENT || !parser.getLocalName().equalsIgnoreCase("osm")) {
            throw new IllegalArgumentException("File is not a valid OSM stream");
        }
        // See https://wiki.openstreetmap.org/wiki/PBF_Format#Definition_of_the_OSMHeader_fileblock
        String timestamp = parser.getAttributeValue(null, "osmosis_replication_timestamp");

        if (timestamp == null)
            timestamp = parser.getAttributeValue(null, "timestamp");

        if (timestamp != null) {
            try {
                fileheader = new OSMFileHeader();
                fileheader.setTag("timestamp", timestamp);
            } catch (Exception ex) {
            }
        }

        eof = false;
    }

    @Override
    public ReaderElement getNext() throws XMLStreamException {
        if (eof)
            throw new IllegalStateException("EOF reached");

        ReaderElement item;
        if (binary)
            item = getNextPBF();
        else
            item = getNextXML();

        if (item != null)
            return item;

        eof = true;
        return null;
    }

    private ReaderElement getNextXML() throws XMLStreamException {

        int event = parser.next();
        if (fileheader != null) {
            ReaderElement copyfileheader = fileheader;
            fileheader = null;
            return copyfileheader;
        }

        while (event != XMLStreamConstants.END_DOCUMENT) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                String idStr = parser.getAttributeValue(null, "id");
                if (idStr != null) {
                    String name = parser.getLocalName();
                    long id = 0;
                    switch (name.charAt(0)) {
                        case 'n':
                            // note vs. node
                            if ("node".equals(name)) {
                                id = Long.parseLong(idStr);
                                return OSMXMLHelper.createNode(id, parser);
                            }
                            break;

                        case 'w': {
                            id = Long.parseLong(idStr);
                            return OSMXMLHelper.createWay(id, parser);
                        }
                        case 'r':
                            id = Long.parseLong(idStr);
                            return OSMXMLHelper.createRelation(id, parser);
                    }
                }
            }
            event = parser.next();
        }
        parser.close();
        return null;
    }

    public boolean isEOF() {
        return eof;
    }

    @Override
    public void close() throws IOException {
        try {
            if (!binary)
                parser.close();
        } catch (XMLStreamException ex) {
            throw new IOException(ex);
        } finally {
            eof = true;
            bis.close();
            // if exception happend on OSMInputFile-thread we need to shutdown the pbf handling
            if (pbfReaderThread != null && pbfReaderThread.isAlive())
                pbfReaderThread.interrupt();
        }
    }

    private void openPBFReader(InputStream stream) {
        hasIncomingData = true;
        if (workerThreads <= 0)
            workerThreads = 1;

        PbfReader reader = new PbfReader(stream, this, workerThreads);
        pbfReaderThread = new Thread(reader, "PBF Reader");
        pbfReaderThread.start();
    }

    @Override
    public void process(ReaderElement item) {
        try {
            // blocks if full
            itemQueue.put(item);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    public int getUnprocessedElements() {
        return itemQueue.size();
    }

    @Override
    public void complete() {
        hasIncomingData = false;
    }

    private ReaderElement getNextPBF() {
        ReaderElement next = null;
        while (next == null) {
            if (!hasIncomingData && itemQueue.isEmpty()) {
                // we are done, stop polling
                eof = true;
                break;
            }

            try {
                // we cannot use "itemQueue.take()" as it blocks and hasIncomingData can change
                next = itemQueue.poll(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                eof = true;
                break;
            }
        }
        return next;
    }
}
