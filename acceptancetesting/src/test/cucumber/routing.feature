
@Routing
    Scenario Outline: Verify  waypoints on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    And I should see "<RoutestepNumber>" the TurnNavigationstep "<Instruction>" for my route on UI

    
    Examples:
    |pointA|pointB|routetype|waypoint|Instruction|RoutestepNumber|
    |51.471546541834144,-0.3618621826171875|51.45914115860512,-0.96679687499999995|car||Continue onto ELLINGTON ROAD|1|
