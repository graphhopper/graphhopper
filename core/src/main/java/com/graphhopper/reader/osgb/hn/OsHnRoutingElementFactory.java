package com.graphhopper.reader.osgb.hn;

import java.math.BigDecimal;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.osgb.AbstractRoutingElementFactory;

public class OsHnRoutingElementFactory extends AbstractRoutingElementFactory<RoutingElement>{

    private static final Logger logger = LoggerFactory.getLogger(OsHnRoutingElementFactory.class);

    @Override
    public RoutingElement create(String name, String idStr, XMLStreamReader parser) throws MismatchedDimensionException, XMLStreamException, FactoryException, TransformException {
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
            System.out.println(">>>>>>>>>>>> RoadNode " + id);
            //            return OSITNNode.create(id, parser);
        }
        case "RoadLink": {
            System.out.println(">>>>>>>>>>>> RoadLink " + id);
            //            return OSITNWay.create(id, parser);
        }

        }
        return null;

    }

}
