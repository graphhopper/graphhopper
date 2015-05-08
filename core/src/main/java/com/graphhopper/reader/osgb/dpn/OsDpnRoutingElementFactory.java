package com.graphhopper.reader.osgb.dpn;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.osgb.AbstractRoutingElementFactory;

public class OsDpnRoutingElementFactory extends AbstractRoutingElementFactory<RoutingElement>{

    private static final Logger logger = LoggerFactory.getLogger(OsDpnRoutingElementFactory.class);

    @Override
    public RoutingElement create(String name, String idStr, XMLStreamReader parser) throws MismatchedDimensionException, XMLStreamException, FactoryException, TransformException {
    	idStr = idStr.substring(3);
        logger.info(":" + name + ":");
        switch (name) {
        case "RouteNode": {
            return OsDpnNode.create(idStr, parser);
        }
        case "RouteLink": {
            return OsDpnWay.create(idStr, parser);
        }
        case "Route": {
            // TODO grouped features
        }
        default: {

        }

        }
        return null;
    }
}
