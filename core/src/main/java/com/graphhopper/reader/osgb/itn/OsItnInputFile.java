/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper licenses this file to you under the Apache License,
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
package com.graphhopper.reader.osgb.itn;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.osgb.AbstractOsInputFile;

/**
 * A readable OS ITN file.
 * <p/>
 *
 * @author Stuart Adam
 */
public class OsItnInputFile extends AbstractOsInputFile<OSITNElement> {//implements Sink, Closeable {
    //    private boolean eof;
    //    private final InputStream bis;
    //    // for xml parsing
    //    private XMLStreamReader parser;
    //    // for pbf parsing
    //    private boolean binary = false;
    //    private final BlockingQueue<RoutingElement> itemQueue;
    //    private boolean hasIncomingData;
    //    private int workerThreads = -1;
    private static final Logger logger = LoggerFactory.getLogger(OsItnInputFile.class);
    //    private final String name;

    public OsItnInputFile(File file) throws IOException {
        super(file);
    }

    @Override
    protected OSITNElement getNextXML() throws XMLStreamException,
    MismatchedDimensionException, FactoryException, TransformException {

        int event = parser.next();
        while (event != XMLStreamConstants.END_DOCUMENT) {
            if (event == XMLStreamConstants.START_ELEMENT) {
                String idStr = parser.getAttributeValue(null, "fid");
                if (null == idStr) {
                    idStr = parser.getAttributeValue(
                            "http://www.opengis.net/gml/3.2", "id");
                }
                if (idStr != null) {
                    String name = parser.getLocalName();
                    idStr = idStr.substring(4);
                    logger.info(idStr + ":" + name + ":");

                    long id;
                    try {
                        id = Long.parseLong(idStr);
                    } catch (NumberFormatException nfe) {
                        BigDecimal bd = new BigDecimal(idStr);
                        id = bd.longValue();
                    }
                    logger.info(id + ":" + name + ":");
                    switch (name) {
                    case "RoadNode": {
                        return OSITNNode.create(id, parser);
                    }
                    case "RoadLink": {
                        return OSITNWay.create(id, parser);
                    }

                    case "RoadLinkInformation":
                    case "RoadRouteInformation": {
                        return OSITNRelation.create(id, parser);
                    }

                    case "Road": {
                        return OsItnMetaData.create(id, parser);
                    }
                    case "RoadNodeInformation": {
                    }
                    default: {

                    }

                    }
                }
            }
            event = parser.next();
        }
        parser.close();
        return null;
    }
}
