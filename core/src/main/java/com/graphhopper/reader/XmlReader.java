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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

/**
 * @author Nop
 */
public class XmlReader implements Runnable {

    private InputStream inputStream;
    private Sink sink;
    private XMLStreamReader parser;

    public XmlReader(InputStream in, Sink sink) {
        this.inputStream = in;
        this.sink = sink;
    }

    @Override
    public void run() {
        try {

            openXMLStream();

            int event = parser.next();
            while (event != XMLStreamConstants.END_DOCUMENT) {
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = parser.getLocalName();
                    long id = 0;
                    switch (name.charAt(0)) {
                        case 'n':
                            id = Long.parseLong(parser.getAttributeValue(null, "id"));
                            sink.process(new OSMNode(id, parser));
                            break;

                        case 'w':
                            id = Long.parseLong(parser.getAttributeValue(null, "id"));
                            sink.process(new OSMWay(id, parser));
                            break;

                        case 'r':
                            id = Long.parseLong(parser.getAttributeValue(null, "id"));
                            sink.process(new OSMRelation(id, parser));
                            break;
                    }
                }
                event = parser.next();
            }
            parser.close();
            sink.complete();

        } catch (Exception e) {
            throw new RuntimeException("Unable to read XML file.", e);
        } finally {
        }
    }

    private void openXMLStream()
            throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        parser = factory.createXMLStreamReader(inputStream, "UTF-8");

        int event = parser.next();
        if (event != XMLStreamConstants.START_ELEMENT || !parser.getLocalName().equalsIgnoreCase("osm")) {
            throw new IllegalArgumentException("File is not a valid OSM stream");
        }
    }
}
