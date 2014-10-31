package uk.co.ordnancesurvey.routing;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import cucumber.api.DataTable;
import cucumber.api.java.After;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;


public class GraphHopperHooks {
	GraphHopperUIUtil graphUiUtil=new GraphHopperUIUtil();
	
	String instruction;
	@Given("^I request a route between \"([^\"]*)\" and \"([^\"]*)\" as a \"([^\"]*)\" from RoutingAPI$")
		public void getRoute(String pointA,String pointB,String routeType)
		{
		graphUiUtil.getRouteFromService(pointA, pointB, routeType);
		graphUiUtil.getRouteFromUI(pointA, pointB, routeType);
		
	}

	
	@Then("^I should see \"([^\"]*)\" the TurnNavigationstep \"([^\"]*)\" for my route on UI$")
	public void I_should_see_the_TurnNavigationstep_for_my_route_on_UI(String routeStepNumber,String Instruction)  {
			
		graphUiUtil.verifyInstructionThroughUI(routeStepNumber,Instruction);
		graphUiUtil.verifyInstructionThroughService(Instruction);
	}
	
	
	@Then("^I shhould be able to verify the \"([^\"]*)\" waypoint \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" \"([^\"]*)\" on the route map$")
	public void I_shhould_be_able_to_verify_the_on_the_route_map(String wayPointIndex,String wayPoint_Coordinates,String wayPointDescription,String azimuth, String direction, String time, String distance) {
	    
		graphUiUtil.verifyWayPointonRouteMap(wayPointIndex,wayPoint_Coordinates,wayPointDescription, azimuth, direction, time, distance);
		
	}


  @After({"@Routing"})
    public void closeBrowser()
    {
    	graphUiUtil.logout();
    	System.out.println("closed");
    }
	

}
