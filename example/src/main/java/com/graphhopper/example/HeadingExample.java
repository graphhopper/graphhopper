package com.graphhopper.example;

import java.util.Arrays;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.CHProfile;
import com.graphhopper.config.Profile;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

public class HeadingExample
{
  public static void main(String[] args) {
    String relDir = args.length == 1 ? args[0] : "";
    GraphHopper hopper = createGraphHopperInstance(relDir + "core/files/andorra.osm.pbf");
  
    without_heading(hopper);
    with_heading_start(hopper);
    with_heading_start_stop(hopper);
    with_heading_stop(hopper);
    with_heading_start_stop_lower_penalty(hopper);
  }
  
  /**
   * See {@link RoutingExample#createGraphHopperInstance} for more comments on creating the GraphHopper instance.
   */
  static GraphHopper createGraphHopperInstance(String ghLoc) {
      GraphHopper hopper = new GraphHopper();
      hopper.setOSMFile(ghLoc);
      hopper.setGraphHopperLocation("target/heading-graph-cache");
      hopper.setProfiles(new Profile("car").setVehicle("car").setWeighting("fastest").setTurnCosts(false));
      hopper.getCHPreparationHandler().setCHProfiles(new CHProfile("car"));
      hopper.importOrLoad();
      return hopper;
  }
  
  static void without_heading(GraphHopper hopper) {
      GHRequest request = new GHRequest(new GHPoint(42.566757, 1.597751), new GHPoint(42.567396, 1.597807)).
              setProfile("car");
      GHResponse response = hopper.route(request);
      if (response.hasErrors())
          throw new RuntimeException(response.getErrors().toString());
      assert Math.round(response.getBest().getDistance()) == 84;
  }

  static void with_heading_start(GraphHopper hopper) {
      GHRequest request = new GHRequest(new GHPoint(42.566757, 1.597751), new GHPoint(42.567396, 1.597807)).
              setHeadings(Arrays.asList(270d)).
              // important: if CH is enabled on the server-side we need to disable it for each request that uses heading,
              // because heading is not supported by CH
                      putHint(Parameters.CH.DISABLE, true).
              setProfile("car");
      GHResponse response = hopper.route(request);
      if (response.hasErrors())
          throw new RuntimeException(response.getErrors().toString());
      assert Math.round(response.getBest().getDistance()) == 264;
  }

  static void with_heading_start_stop(GraphHopper hopper) {
      GHRequest request = new GHRequest(new GHPoint(42.566757, 1.597751), new GHPoint(42.567396, 1.597807)).
              setHeadings(Arrays.asList(270d, 180d)).
              putHint(Parameters.CH.DISABLE, true).
              setProfile("car");
      GHResponse response = hopper.route(request);
      if (response.hasErrors())
          throw new RuntimeException(response.getErrors().toString());
      assert Math.round(response.getBest().getDistance()) == 434;
  }

  static void with_heading_stop(GraphHopper hopper) {
      GHRequest request = new GHRequest(new GHPoint(42.566757, 1.597751), new GHPoint(42.567396, 1.597807)).
              setHeadings(Arrays.asList(Double.NaN, 180d)).
              putHint(Parameters.CH.DISABLE, true).
              setProfile("car");
      GHResponse response = hopper.route(request);
      if (response.hasErrors())
          throw new RuntimeException(response.getErrors().toString());
      assert Math.round(response.getBest().getDistance()) == 201;
  }

  static void with_heading_start_stop_lower_penalty(GraphHopper hopper) {
      GHRequest request = new GHRequest(new GHPoint(42.566757, 1.597751), new GHPoint(42.567396, 1.597807)).
              setHeadings(Arrays.asList(270d, 180d)).
              putHint(Parameters.Routing.HEADING_PENALTY, 10).
              putHint(Parameters.CH.DISABLE, true).
              setProfile("car");
      GHResponse response = hopper.route(request);
      if (response.hasErrors())
          throw new RuntimeException(response.getErrors().toString());
      // same distance as without_heading
      assert Math.round(response.getBest().getDistance()) == 84;
  }

}
