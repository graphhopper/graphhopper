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
        //      <highway:ResponsibleAuthority gml:id="LOCAL_ID_29378">
        //        <highway:identifier>114</highway:identifier>
        //        <highway:authorityName>Bath And North East Somerset</highway:authorityName>
        //      </highway:ResponsibleAuthority>
        // ResponsibleAuthority has a different id format so we need to ignore this
        if (name.equals("ResponsibleAuthority")||name.equals("LineString")||name.equals("Point")||name.equals("Street")||name.equals("TimePeriod")) {
            return null;
        }
        // Strip of the osgb prefix
        idStr = idStr.substring(4);
        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException nfe) {
            BigDecimal bd = new BigDecimal(idStr);
            id = bd.longValue();
        }
        switch (name) {
        case "RoadNode": {
            //            logger.error(">>>>>>>>>>>> RoadNode " + id);
            //            return OSITNNode.create(id, parser);
            return new OsHnRoadLink(id, parser);
        }
        case "RoadLink": {
            //            logger.error(">>>>>>>>>>>> RoadLink " + id);
            //            return OSITNWay.create(id, parser);
            return new OsHnRoadLink(id, parser);
        }

        }
        return null;

    }

}
