/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader;

import com.graphhopper.reader.pbf.Sink;
import com.graphhopper.reader.pbf.PbfReader;
import com.graphhopper.routing.util.AbstractFlagEncoder;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.lang.reflect.Constructor;
import java.util.LinkedList;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * A readable OSM file.
 *
 * @author Nop
 */
public class OSMInputFile implements Sink {

    private boolean eof;
    private InputStream bis;
    private boolean autoClose;
    // for xml parsing
    private XMLStreamReader parser;
    // for pbf parsing
    private boolean binary = false;
    private final LinkedList<OSMElement> itemQueue = new LinkedList<OSMElement>();
    private boolean incomingData;
    private int workerThreads = -1;

    public OSMInputFile(File file) throws IOException {
        bis = decode(file);
        autoClose = true;
    }

    public OSMInputFile open() throws XMLStreamException {
        if (binary)
            openPBFReader(bis);
        else
            openXMLStream(bis);
        return this;
    }

    /**
     * Currently on for pbf format. Default is number of cores.
     */
    public OSMInputFile workerThreads(int num) {
        workerThreads = num;
        return this;
    }

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
        ips.read(header);

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
                && header[3] == 13 && header[4] == 10 && header[5] == 9) {
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
                Constructor ctor = clazz.getConstructor(InputStream.class, boolean.class);
                return (InputStream) ctor.newInstance(ips, true);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot instantiate " + clName, e);
            }
        } else {
            throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());
        }
    }

    public OSMInputFile(InputStream in) throws XMLStreamException {
        openXMLStream(in);
    }

    private void openXMLStream(InputStream in)
            throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        parser = factory.createXMLStreamReader(bis, "UTF-8");

        int event = parser.next();
        if (event != XMLStreamConstants.START_ELEMENT || !parser.getLocalName().equalsIgnoreCase("osm")) {
            throw new IllegalArgumentException("File is not a valid OSM stream");
        }

        eof = false;
    }

    public OSMElement getNext() throws XMLStreamException {
        if (eof) {
            throw new IllegalStateException("EOF reached");
        }

        OSMElement item;
        if (binary) {
            item = getNextPBF();
        } else {
            item = getNextXML();
        }
        if (item != null) {
            return item;
        }

        eof = true;
        return null;
    }

    private OSMElement getNextXML() throws XMLStreamException {

        int event = parser.next();
        while (event != XMLStreamConstants.END_DOCUMENT) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = parser.getLocalName();
                long id = 0;
                switch (name.charAt(0)) {
                    case 'n':
                        id = Long.parseLong(parser.getAttributeValue(null, "id"));
                        return new OSMNode(id, parser);

                    case 'w': {
                        id = Long.parseLong(parser.getAttributeValue(null, "id"));
                        return new OSMWay(id, parser);
                    }
                    case 'r':
                        id = Long.parseLong(parser.getAttributeValue(null, "id"));
                        return new OSMRelation(id, parser);
                }
            }
            event = parser.next();
        }
        parser.close();
        return null;
    }

    public boolean eof() {
        return eof;
    }

    public void close() throws XMLStreamException, IOException {
        if (!binary)
            parser.close();
        eof = true;
        if (autoClose) {
            bis.close();
        }
    }

    private void openPBFReader(InputStream stream) {
        incomingData = true;

        if (workerThreads <= 0)
            workerThreads = 2;
        PbfReader reader = new PbfReader(stream, this, workerThreads);
        new Thread(reader, "PBF Reader").start();
    }

    @Override
    public void process(OSMElement item) {
        synchronized (itemQueue) {
            itemQueue.addLast(item);
            itemQueue.notifyAll();
            // keep queue from overrunning
            if (itemQueue.size() > 50000) {
                try {
                    itemQueue.wait();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
    }

    @Override
    public void complete() {
        synchronized (itemQueue) {
            incomingData = false;
            itemQueue.notifyAll();
        }
    }

    private OSMElement getNextPBF() {
        OSMElement next = null;
        do {
            synchronized (itemQueue) {
                // try to read next object
                next = itemQueue.pollFirst();

                if (next == null) {
                    // if we have no items to process but parser is still working: wait
                    if (incomingData)
                        try {
                            itemQueue.wait();
                        } catch (InterruptedException e) {
                            // ignored
                        }
                    // we are done, stop waiting
                    else
                        break;
                } else
                    itemQueue.notifyAll();
            }
        } while (next == null);

        return next;
    }
}