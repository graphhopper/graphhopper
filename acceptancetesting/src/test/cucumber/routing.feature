Feature: Verify a route from A to B
    As a user
    I want to get a route from location A to location B using the routing service
    And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  @Routing
  Scenario Outline: Verify  waypoints on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    And I should see "<wayPointIndex>" the TurnNavigationstep "<waypointDescription>" for my route on UI
    And I shhould be able to verify the "<wayPointIndex>" waypoint "<Waypoint-coordinates>" "<waypointDescription>" "<azimuth>" "<direction>" "<time>" "<distance>" on the route map

    Examples: 
      | pointA                                 | pointB                                 | routetype | waypoint | waypointDescription          | wayPointIndex |Waypoint-coordinates|azimuth|direction|time|distance|
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |          | Continue onto ELLINGTON ROAD | 1              |51.472112,-0.361993|278|W|10696|130.188|
																												 