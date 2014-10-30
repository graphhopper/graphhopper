Feature: Verify a route from A to B
    As a user
    I want to get a route from location A to location B using the routing service
    And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  @Routing
  Scenario Outline: Verify  waypoints on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    And I should see "<WayPointNumber>" the TurnNavigationstep "<waypointDescription>" for my route on UI

    Examples: 
      | pointA                                 | pointB                                 | routetype | waypoint | waypointDescription          | WayPointNumber |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |          | Continue onto ELLINGTON ROAD | 1              |
