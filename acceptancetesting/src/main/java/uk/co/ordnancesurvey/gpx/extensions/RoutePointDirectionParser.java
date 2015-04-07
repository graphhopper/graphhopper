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

public class RoutePointDirectionParser implements IExtensionParser{

	private static final Logger LOG = LoggerFactory
			.getLogger(RoutePointDirectionParser.class);
			
	public RoutePointDirectionParser() {
		// TODO Auto-generated constructor stub
	}
	
	public static final String MYID = ExtensionConstants.DIRECTION;

	@Override
	public String getId() {
		return MYID;
	}

	@Override
	public Object parseWaypointExtension(Node node) {
		String value = "";
		for (int idx = 0; idx < node.getChildNodes().getLength(); idx++) {
			Node currentNode = node.getChildNodes().item(idx);
			LOG.debug("Node Type is " + currentNode.getNodeType() + " And node name is" + currentNode.getNodeName() + " And node value is " + currentNode.getTextContent());
			if ("gh:direction".equals(currentNode.getNodeName())) {
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
