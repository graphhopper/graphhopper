package com.graphhopper.reader.osgb.hn;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OsHnRoadLink {
    private final long id;
    private static final Logger logger = LoggerFactory.getLogger(OsHnRoadLink.class);

    public OsHnRoadLink(long id, XMLStreamReader parser) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
        this.id = id;
        parser.nextTag();
        readTags(parser);
    }
    protected void readTags(XMLStreamReader parser) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
        int event = parser.getEventType();
        while (event != XMLStreamConstants.END_DOCUMENT && (event != XMLStreamConstants.END_ELEMENT || !exitElement(parser))) {
            if (event == XMLStreamConstants.CHARACTERS) {
                event = parser.next();
            } else {

                if (event == XMLStreamConstants.START_ELEMENT) {
                    logger.debug("LOCALNAME:" + parser.getLocalName());
                    switch (parser.getLocalName()) {
                    case "environment": {
                        //                        event = handleCoordinates(parser);
                        String elementText = parser.getElementText();
                        System.out.println("Environment " + elementText);
                        event = parser.getEventType();
                        break;
                    }
                    default: {
                        event = parser.next();
                    }
                    }

                } else {
                    logger.info("EVENT:" + event);
                    event = parser.next();
                }
            }
        }
        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
    }
    private boolean exitElement(XMLStreamReader parser) {
        System.out.println("exitElement  " + parser.getLocalName());
        switch (parser.getLocalName()) {
        case "RoadLink":
        case "RoadNode":
            return true;
        }
        return false;
    }

}
