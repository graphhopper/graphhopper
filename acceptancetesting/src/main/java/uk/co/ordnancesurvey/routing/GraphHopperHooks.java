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

  @After({"@Routing"})
    public void closeBrowser()
    {
    	graphUiUtil.logout();
    	System.out.println("closed");
    }
	

}
