package com.graphhopper.reader.osgb.hn;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.OSMElement;

public class OsHnRoadLink extends OSMElement {
    private static final Logger logger = LoggerFactory.getLogger(OsHnRoadLink.class);

    private String environment;

    public OsHnRoadLink(long id, XMLStreamReader parser) throws XMLStreamException, MismatchedDimensionException, FactoryException, TransformException {
        super(id, WAY);
        parser.nextTag();
        readTags(parser);
    }
    @Override
    protected void readTags(XMLStreamReader parser) throws XMLStreamException {
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
                        environment = elementText;
                        //                        System.out.println("Environment " + environment);

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
        //        System.out.println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
    }
    private boolean exitElement(XMLStreamReader parser) {
        //        System.out.println("exitElement  " + parser.getLocalName());
        switch (parser.getLocalName()) {
        case "RoadLink":
        case "RoadNode":
            return true;
        }
        return false;
    }
    public String getEnvironment() {
        return environment;
    }
    public void setEnvironment(String environment) {
        this.environment = environment;
    }
}
