Feature: Verify a route from A to B
    As a user
    I want to get a route from location A to location B using the routing service
    And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  
  Scenario Outline: Verify  waypoints on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                 | azimuth | direction | time  | distance |
      | 1             | 51.472112,-0.361993 | Continue onto ELLINGTON ROAD | 278     | W         | 10696 | 130.188  |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |


  Scenario Outline: Verify  waypoints on a Route from Hounslow to Reading
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                        | azimuth | direction | time    | distance  |
      | 1             | 51.472112,-0.361993 | Continue onto ELLINGTON ROAD        | 278     | W         | 10696   | 130.188   |
      | 9             | 51.491777,-0.411    | Turn slight left onto M4            | 303     | NW        | 1298148 | 36068.275 |
      | 14            | 51.437599,-0.900107 | Continue onto WOKINGHAM ROAD (A329) | 293     | NW        | 24980   | 381.74    |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Southampton to Glasgow
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco           | waypointdesc                       | azimuth | direction | time   | distance  |
      | 1             | 50.902833,-1.40436  | continue onto WEST BARGATE         | 244      | SW        | 190   | 1.852    |
      | 13            | 50.953612,-1.403243  | Continue onto M3                   | 27      | NE         | 389425 | 10820.062  |
      | 62            | 51.558554,-1.149732  | Turn left onto READING ROAD (A329) | 357     | N         | 247561 | 3782.941 |
      | 90            | 52.471676,-1.711101    | Continue onto M6                   | 1      | N         | 49751 | 1382.53 |
     

    Examples: 
      | pointA                                | pointB                               | routetype |
      | 50.902674,-1.404169 | 55.861284,-4.24996 | car       |
