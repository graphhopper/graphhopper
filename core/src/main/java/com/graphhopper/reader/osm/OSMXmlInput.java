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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;

/**
 * OSM input implementation for XML format (.osm, .osm.gz, etc.)
 */
class OSMXmlInput implements OSMInput {
    private final InputStream inputStream;
    private XMLStreamReader xmlParser;
    private boolean eof;
    private OSMFileHeader fileheader;

    OSMXmlInput(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    OSMXmlInput open() throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        xmlParser = factory.createXMLStreamReader(inputStream, "UTF-8");

        int event = xmlParser.next();
        if (event != XMLStreamConstants.START_ELEMENT || !xmlParser.getLocalName().equalsIgnoreCase("osm")) {
            throw new IllegalArgumentException("File is not a valid OSM stream");
        }
        // See https://wiki.openstreetmap.org/wiki/PBF_Format#Definition_of_the_OSMHeader_fileblock
        String timestamp = xmlParser.getAttributeValue(null, "osmosis_replication_timestamp");

        if (timestamp == null)
            timestamp = xmlParser.getAttributeValue(null, "timestamp");

        if (timestamp != null) {
            fileheader = new OSMFileHeader();
            fileheader.setTag("timestamp", timestamp);
        }

        eof = false;
        return this;
    }

    @Override
    public ReaderElement getNext() throws XMLStreamException {
        if (eof)
            throw new IllegalStateException("EOF reached");

        int event = xmlParser.next();
        if (fileheader != null) {
            ReaderElement copyfileheader = fileheader;
            fileheader = null;
            return copyfileheader;
        }

        while (event != XMLStreamConstants.END_DOCUMENT) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                String idStr = xmlParser.getAttributeValue(null, "id");
                if (idStr != null) {
                    String name = xmlParser.getLocalName();
                    long id;
                    switch (name.charAt(0)) {
                        case 'n':
                            // note vs. node
                            if ("node".equals(name)) {
                                id = Long.parseLong(idStr);
                                return OSMXMLHelper.createNode(id, xmlParser);
                            }
                            break;

                        case 'w': {
                            id = Long.parseLong(idStr);
                            return OSMXMLHelper.createWay(id, xmlParser);
                        }
                        case 'r':
                            id = Long.parseLong(idStr);
                            return OSMXMLHelper.createRelation(id, xmlParser);
                    }
                }
            }
            event = xmlParser.next();
        }
        xmlParser.close();
        eof = true;
        return null;
    }

    @Override
    public int getUnprocessedElements() {
        return 0;
    }

    @Override
    public void close() throws IOException {
        try {
            xmlParser.close();
        } catch (XMLStreamException ex) {
            throw new IOException(ex);
        } finally {
            eof = true;
            inputStream.close();
        }
    }
}
