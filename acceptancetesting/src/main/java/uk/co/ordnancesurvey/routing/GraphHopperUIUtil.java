package uk.co.ordnancesurvey.routing;



import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.FROM_ROUTE;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.INSTRUCTIONS;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.ROUTE_SEARCH;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.ROUTE_TYPE_BIKE;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.ROUTE_TYPE_CAR;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.ROUTE_TYPE_WALK;
import static uk.co.ordnancesurvey.routing.GraphHopperComponentIdentification.TO_ROUTE;

import java.util.HashSet;

import org.alternativevision.gpx.beans.Route;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.ordnancesurvey.gpx.graphhopper.GraphHopperGPXParserRouteTest;
import uk.co.ordnancesurvey.webtests.IntegrationTestProperties;
import uk.co.ordnancesurvey.webtests.multiplatform.MultiplatformTest;
import uk.co.ordnancesurvey.webtests.platforms.BrowserPlatformOptions;

public class GraphHopperUIUtil extends MultiplatformTest{

	private String baseUrl;
	private String stepInstruction;
	private String routeStepNumber;
	GraphHopperGPXParserRouteTest GPHService=new GraphHopperGPXParserRouteTest();
	private String gpxServiceResponse;
	
	private static final Logger LOG = LoggerFactory
			.getLogger(GraphHopperUIUtil.class);

	public GraphHopperUIUtil() {
		super(BrowserPlatformOptions.getEnabledOptionsArrayList().get(0)[0]);
		init();
	}

	private void init() {
		baseUrl = IntegrationTestProperties.getTestProperty("graphHopperUrl");
			if (null == baseUrl) {
			baseUrl = "http://10.160.38.128:8989/";
		}
		initialiseWebDriver();
		
	}
	
	
	@Override
	public void logout() {
		driver.close();
	}
	
	public void getRouteFromUI(String pointA,String pointB,String routeType)
	{
	
		
		switch(routeType)
		
		{
		case "car": clickElement(ROUTE_TYPE_CAR);break;
		case "bike": clickElement(ROUTE_TYPE_BIKE);break;
		case "walk": clickElement(ROUTE_TYPE_WALK);break;
		default : clickElement(ROUTE_TYPE_CAR);break;
		
		}
		
		typeIntoField(FROM_ROUTE, pointA);
		typeIntoField(TO_ROUTE, pointB);
		clickElement(ROUTE_SEARCH);
		waitFor(INSTRUCTIONS);
		
		}
	
	@Override
	public void login() {
		driver.navigate().to(baseUrl);
	}
	

	public   void verifyInstructionThroughUI(String routeStepNumber,String stepInstruction)
	{
		this.routeStepNumber=routeStepNumber;
		checkTableRow(INSTRUCTIONS, Integer.parseInt(this.routeStepNumber), stepInstruction);
	
	}

	public void getRouteFromService(String pointA, String pointB,
			String routeType) {
		gpxServiceResponse=GPHService.parseRoute(pointA+","+pointB, "gpx", routeType);
			
	}

	public void verifyInstructionThroughService(String stepInstruction)
	{
		HashSet<Route> routeInstruction=GPHService.getRoutes();

		Assert.assertTrue("The Route instruction is not found in the gpx response",GPHService.routeContainsTurn(stepInstruction.toUpperCase(),routeInstruction.iterator().next()));
	

	}
	
	

}