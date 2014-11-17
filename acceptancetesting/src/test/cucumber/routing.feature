Feature: Verify a route from A to B
    As a user
    I want to get a route from location A to location B using the routing service
    And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  @Routing
  Scenario Outline: Verify  waypoints on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                 | azimuth | direction | time  | distance |
      | 1             | 51.472112,-0.361993 | Continue onto ELLINGTON ROAD | 278     | W         | 10696 | 130.188  |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Hounslow to Reading
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                        | azimuth | direction | time    | distance  |
      | 1             | 51.472112,-0.361993 | Continue onto ELLINGTON ROAD        | 278     | W         | 10696   | 130.188   |
      | 10            | 51.488668,-0.394307 | Continue onto M4                    | 284     | W         | 1319825 | 36669.681 |
      | 14            | 51.437599,-0.900107 | Continue onto WOKINGHAM ROAD (A329) | 293     | NW        | 24980   | 381.74    |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Southampton to Glasgow
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                       | azimuth | direction | time   | distance  |
      | 1             | 50.902833,-1.40436  | continue onto WEST BARGATE         | 244     | SW        | 190    | 1.852     |
      | 13            | 50.953612,-1.403243 | Continue onto M3                   | 27      | NE        | 389425 | 10820.062 |
      | 62            | 51.558554,-1.149732 | Turn left onto READING ROAD (A329) | 357     | N         | 247561 | 3782.941  |
      | 90            | 52.471676,-1.711101 | Continue onto M6                   | 1       | N         | 49751  | 1382.53   |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.902674,-1.404169 | 55.861284,-4.24996 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from London to Birmingham
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                       | azimuth | direction | time    | distance  |
      | 1             | 51.507229,-0.127581 | Continue onto CHARING CROSS (A4)   | 261     | W         | 4167    | 63.781    |
      | 8             | 51.515281,-0.141988 | turn left onto A40 (OXFORD STREET) | 275     | W         | 77416   | 1183.058  |
      | 25            | 51.560754,-0.488952 | continue onto M40                  | 274     | W         | 568909  | 15806.233 |
      | 52            | 52.073616,-1.3127   | continue onto M40                  | 285     | W         | 1712591 | 47581.879 |

    Examples: 
      | pointA              | pointB              | routetype |
      | 51.507229,-0.127581 | 52.481875,-1.898743 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from London to Birmingham and the total route time estimate
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then The total route time should be not more than "<totalRouteTime>"

    Examples: 
      | pointA              | pointB              | routetype | totalRouteTime |
      | 51.507229,-0.127581 | 52.481875,-1.898743 | car       | 03h00min       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Hounslow to Burnham and the total route time estimate
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then The total route time should be not more than "<totalRouteTime>"

    Examples: 
      | pointA             | pointB              | routetype | totalRouteTime |
      | 51.475161,-0.39591 | 51.536292,-0.656802 | car       | 0h30min        |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Southampton to Glasgow
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints on the route map:
      | trackPointco       | time                      |
      | 50.911647,-1.41134 | 2014-10-31T19:17:22+00:00 |
      | 50.910471,-1.41042 | 2014-10-31T19:17:38+00:00 |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.911645,-1.411389 | 50.913965,-1.401229 | car       |

  @Routing
  Scenario Outline: Verify  oneway Restrictions on a Route (Burmingham Route with one way restriction)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco        | time                      |
      | 52.446899,-1.929721 | 2014-10-31T19:17:22+00:00 |

    Examples: 
      | pointA              | pointB              | routetype |
      | 52.446823,-1.929077 | 52.446604,-1.930043 | car       |

  @Routing
  Scenario Outline: Verify  oneway Restrictions on a Route (Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco       |
      | 50.71958,-3.534089 |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.720492,-3.535221 | 50.718641,-3.53476 | car       |

  @Routing
  Scenario Outline: Verify  Turn Restrictions  on a Route (Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco        |
      | 50.721201,-3.532498 |

    Examples: 
      | pointA             | pointB             | routetype |
      | 50.72148,-3.532485 | 50.721888,-3.53182 | car       |
      
       @Routing
  Scenario Outline: Verify  No Turn Restrictions  on a Route (Birmingham WSPIP-77)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco        |
      | 52.446779,-1.929385 |

    Examples: 
      | pointA             | pointB             | routetype |
      | 52.446564,-1.930268  | 52.446744,-1.929469| car       |
