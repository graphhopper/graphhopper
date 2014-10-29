package uk.co.ordinancesurvey.routing;


import static uk.co.ordnancesurvey.webtests.base.ComponentValueSource.INNER_HTML;

import uk.co.ordnancesurvey.webtests.base.ComponentID;
import uk.co.ordnancesurvey.webtests.base.ComponentIdentification;
import uk.co.ordnancesurvey.webtests.base.ComponentIdentifier;
import uk.co.ordnancesurvey.webtests.base.ComponentTableById;
import uk.co.ordnancesurvey.webtests.base.GridFinder;

public class GraphHopperComponentIdentification implements
		ComponentIdentification {

	public static final ComponentIdentifier FROM_ROUTE = new ComponentID("0_Input");
	public static final  ComponentIdentifier TO_ROUTE = new ComponentID("1_Input");
	public static final  ComponentIdentifier ROUTE_TYPE_CAR = new ComponentID("car");
	public static final  ComponentIdentifier ROUTE_TYPE_BIKE = new ComponentID("bike");
	public static final  ComponentIdentifier ROUTE_TYPE_WALK = new ComponentID("foot");
	public static final  ComponentIdentifier ROUTE_SEARCH = new ComponentID("searchButton");
	//ComponentIdentifier firstRouteStep = new ComponentByXPATH(
		//	"//*[@id='instructions']/tbody/tr[1]/td[2]", INNER_HTML);
	public static final  GridFinder INSTRUCTIONS=new ComponentTableById("instructions", INNER_HTML);
}
