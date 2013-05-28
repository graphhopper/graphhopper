/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
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

import com.graphhopper.coll.LongIntMap;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * A readable OSM file.
 *
 * @author Nop
 */
public class OSMInputFile {

    private boolean eof;
    private XMLStreamReader parser;
    private InputStream bis;
    private boolean autoClose;
    private boolean parseNodes = true;
    private LongIntMap nodeFilter = null;
    private boolean parseRelations = true;

    public OSMInputFile(File file) throws IOException, XMLStreamException {
        openStream(decode(file));
        autoClose = true;
    }

    public static InputStream decode(File file)
            throws IOException, XMLStreamException {
        final String name = file.getName();

        InputStream ips = null;
        try {
            ips = new BufferedInputStream(new FileInputStream(file), 50000);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        ips.mark(10);

        // check file header
        byte header[] = new byte[2];
        ips.read(header);

        /*     can parse bz2 directly with additional lib
         if (header[0] == 'B' && header[1] == 'Z')
         {
         return new CBZip2InputStream(ips);
         }
         */
        if (header[0] == 31 && header[1] == -117) {
            ips.reset();
            return new GZIPInputStream(ips);
        } else if (header[0] == 'P' && header[1] == 'K') {
            ips.reset();
            ZipInputStream zip = new ZipInputStream(ips);
            zip.getNextEntry();

            return zip;
        } else if (name.endsWith(".osm") || name.endsWith(".xml")) {
            ips.reset();
            return ips;
        } else {
            throw new IllegalArgumentException("Input file is not of valid type " + file.getPath());
        }
    }

    public OSMInputFile(InputStream in) throws XMLStreamException {
        openStream(in);
    }

    private void openStream(InputStream in)
            throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        bis = in;
        parser = factory.createXMLStreamReader(bis, "UTF-8");

        int event = parser.next();
        if (event != XMLStreamConstants.START_ELEMENT || !parser.getLocalName().equalsIgnoreCase("osm")) {
            throw new IllegalArgumentException("File is not a valid OSM stream");
        }

        eof = false;
    }

    public void parseNodes(boolean doNodes) {
        this.parseNodes = doNodes;
    }

    public void nodeFilter(LongIntMap nodeFilter) {
        this.nodeFilter = nodeFilter;
    }

    public void parseRelations(boolean doRelations) {
        this.parseRelations = doRelations;
    }

    public OSMElement getNext() throws XMLStreamException {
        if (eof) {
            throw new IllegalStateException("EOF reached");
        }

        int event = parser.next();
        boolean keepRunning = true;
        while (event != XMLStreamConstants.END_DOCUMENT && keepRunning) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                String name = parser.getLocalName();
                switch (name.charAt(0)) {
                    case 'n':
                        if (parseNodes) {
                            long id = Long.parseLong(parser.getAttributeValue(null, "id"));
                            if (nodeFilter == null || nodeFilter.get(id) != -1)
                                return new OSMNode(id, parser);
                        }
                        break;

                    case 'w': {
                        long id = Long.parseLong(parser.getAttributeValue(null, "id"));
                        return new OSMWay(id, parser);
                    }
                    case 'r':
                        if (parseRelations) {
                            long id = Long.parseLong(parser.getAttributeValue(null, "id"));
                            return new OSMRelation(id, parser);
                        } else
                            keepRunning = false;
                        break;
                }
            }
            event = parser.next();
        }
        parser.close();
        eof = true;
        return null;
    }

    public boolean eof() {
        return eof;
    }

    public void close() throws XMLStreamException, IOException {
        parser.close();
        eof = true;
        if (autoClose) {
            bis.close();
        }
    }
}