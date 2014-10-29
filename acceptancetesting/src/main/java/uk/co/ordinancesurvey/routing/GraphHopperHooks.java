package uk.co.ordinancesurvey.routing;


import static uk.co.ordinancesurvey.routing.GraphHopperComponentIdentification.INSTRUCTIONS;
import uk.co.ordnancesurvey.webtests.multiplatform.MultiplatformTest;
import cucumber.api.java.After;
import cucumber.api.java.en.And;
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
		//checkTableRow(INSTRUCTIONS, routeStep, this.instruction);
		graphUiUtil.verifyInstructionThroughUI(routeStepNumber,Instruction);
		graphUiUtil.verifyInstructionThroughService(Instruction);
	}

  @After({"@Routing"})
    public void closeBrowser()
    {
    	graphUiUtil.logout();
    }
	

}
