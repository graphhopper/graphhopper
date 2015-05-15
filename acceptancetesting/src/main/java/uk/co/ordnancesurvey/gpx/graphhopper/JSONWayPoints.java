package uk.co.ordnancesurvey.gpx.graphhopper;

import java.util.ArrayList;
import java.util.HashSet;

import org.alternativevision.gpx.beans.Waypoint;

public class JSONWayPoints {
	
	
	String JSONString;
	HashSet<Waypoint> instructions= new HashSet<Waypoint>();

	
	/**
	 * Adds a WayPoint to the Instructions ArrayList(WayPoint List)
	 * @param waypoint
	 * 
	 */
	public void addWayPoint(Waypoint w) {
		
		instructions.add(w);
	}


/**
 * @return List of WayPoints in a JSON Route String
 */
public   HashSet<Waypoint> getInstructions()
{
	return instructions;
}
	

	
	
	
	
	
	

}
