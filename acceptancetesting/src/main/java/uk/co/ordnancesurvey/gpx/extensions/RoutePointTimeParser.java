package uk.co.ordnancesurvey.gpx.extensions;

import org.alternativevision.gpx.beans.GPX;
import org.alternativevision.gpx.beans.Route;
import org.alternativevision.gpx.beans.Track;
import org.alternativevision.gpx.beans.Waypoint;
import org.alternativevision.gpx.extensions.IExtensionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class RoutePointTimeParser implements IExtensionParser {

	private static final Logger LOG = LoggerFactory
			.getLogger(RoutePointTimeParser.class);
	
	public static final String MYID = ExtensionConstants.TIME;
	  
	public RoutePointTimeParser() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getId() {
		return MYID;
	}

	@Override
	public Object parseWaypointExtension(Node node) {
        Object value = "";
        for(int idx = 0; idx < node.getChildNodes().getLength(); idx++) {
            Node currentNode = node.getChildNodes().item(idx);
            if("time".equals(currentNode.getNodeName())) {
            	value = currentNode.getTextContent();
            	break;
            }
        }
        return value;
    }

	@Override
	public Object parseTrackExtension(Node node) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object parseGPXExtension(Node node) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object parseRouteExtension(Node node) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void writeGPXExtensionData(Node node, GPX wpt, Document doc) {
		// TODO Auto-generated method stub
	}

	@Override
	public void writeWaypointExtensionData(Node node, Waypoint wpt, Document doc) {
		// TODO Auto-generated method stub
	}

	@Override
	public void writeTrackExtensionData(Node node, Track wpt, Document doc) {
		// TODO Auto-generated method stub
	}

	@Override
	public void writeRouteExtensionData(Node node, Route wpt, Document doc) {
		// TODO Auto-generated method stub
	}

}
