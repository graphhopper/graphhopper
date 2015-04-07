package uk.co.ordnancesurvey.routing;

import static uk.co.ordnancesurvey.webtests.base.ComponentValueSource.INNER_HTML;
import uk.co.ordnancesurvey.webtests.base.ComponentByXPATH;
import uk.co.ordnancesurvey.webtests.base.ComponentID;
import uk.co.ordnancesurvey.webtests.base.ComponentIdentification;
import uk.co.ordnancesurvey.webtests.base.ComponentIdentifier;
import uk.co.ordnancesurvey.webtests.base.ComponentTableById;
import uk.co.ordnancesurvey.webtests.base.GridFinder;

public class GraphHopperComponentIdentification implements
		ComponentIdentification {

	public static ComponentIdentifier waypoint = new ComponentID("0_searchBox");
	public static final ComponentIdentifier ADD_WAYPOINT= new ComponentByXPATH("//*[@class='pointAdd']");
	public static final  ComponentIdentifier ROUTE_TYPE_CAR = new ComponentID("car");
	public static final  ComponentIdentifier ROUTE_TYPE_BIKE = new ComponentID("bike");
	public static final  ComponentIdentifier ROUTE_TYPE_WALK = new ComponentID("foot");
	public static final  ComponentIdentifier ROUTE_SEARCH = new ComponentID("searchButton");
	public static final  ComponentIdentifier WAYPOINT_ONMAP = new ComponentByXPATH("//*[@class='leaflet-popup-content']", INNER_HTML);
	public static final  GridFinder INSTRUCTIONS=new ComponentTableById("instructions", INNER_HTML);
	public static final  ComponentIdentifier TOTAL_ROUTE_TIME =new ComponentByXPATH("//*[@id='info']/div[1]",INNER_HTML);
	public static final ComponentIdentifier  MAP=new ComponentByXPATH("//*[@id='map']");
	public static final ComponentIdentifier  ZOOM_OUT=new ComponentByXPATH("//*[@title='Zoom out']");
	public static final ComponentIdentifier  ZOOM_IN=new ComponentByXPATH("//*[@title='Zoom in']");
	public static final ComponentIdentifier dropDown =new ComponentByXPATH("//span/div/span/div[*]/p/strong");
	
	
}
