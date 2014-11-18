Feature: Verify a route from A to B
    As a user
    I want to get a route from location A to location B using the routing service
    And route should be the fastest route and contain the waypoints,restrictions,time and other instructions
@Routing
  Scenario Outline: Verify  waypoints on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                 | azimuth | direction | time  | distance |
      | 1             | 51.472112,-0.361993 | Continue onto ELLINGTON ROAD | 278     | W         | 13384 | 130.188  |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |
@Routing
  Scenario Outline: Verify  waypoints on a Route from Hounslow to Reading
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                      | azimuth | direction | time    | distance  |
      | 1             | 51.472112,-0.361993 | Continue onto ELLINGTON ROAD      | 278     | W         | 13384   | 130.188   |
      | 9             | 51.491777,-0.411    | Turn slight left onto M4          | 303     | NW        | 1298148 | 36068.275 |
      | 13            | 51.451398,-0.960078 | Turn right onto WATLINGTON STREET | 321     | NW        | 15401   | 149.745   |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |
@Routing
  Scenario Outline: Verify  waypoints on a Route from Southampton to Glasgow
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                      | azimuth | direction | time     | distance   |
      | 1             | 50.896792,-1.400529 | Continue onto PLATFORM ROAD (A33) | 260     | W         | 5550     | 84.848     |
      | 16            | 50.953438,-1.403562 | Turn slight right onto M3         | 41      | NE        | 3005816  | 83510.517  |
      | 17            | 51.399043,-0.547485 | Continue onto M25                 | 74      | E         | 741536   | 20604.192  |
      | 18            | 51.561608,-0.539404 | Continue onto M40                 | 277     | W         | 4952556  | 137599.0   |
      | 20            | 52.480527,-1.719482 | Continue onto M6                  | 301     | NW        | 12025767 | 334123.439 |
      | 24            | 55.846509,-4.092629 | Turn slight left onto M8          | 360     | N         | 395850   | 10999.698  |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.896617,-1.400465 | 55.861284,-4.24996 | car       |
      
@Routing
  Scenario Outline: Verify  waypoints on a Route from London to Birmingham
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                            | azimuth | direction | time    | distance   |
      | 1             | 51.507229,-0.127581 | Continue onto CHARING CROSS (A4)                        | 261     | W         | 7148    | 109.317    |
      | 7             | 51.515281,-0.141988 | turn sharp left onto A40 (OXFORD STREET)                | 237     | SW        | 64368   | 983.632    |
      | 19            | 51.571908,-0.230495 | Turn slight left onto M1                                | 33      | NE        | 4376926 | 121602.853 |
      | 20            | 52.399976,-1.175029 | Continue onto M6                                        | 334     | NW        | 1867430 | 51882.697  |
      | 21            | 52.508927,-1.871267 | Continue onto ASTON EXPRESSWAY (ELEVATED ROAD) (A38(M)) | 251     | W         | 25435   | 706.77     |

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
      | trackPointco       |
      | 53.014738,-2.327641 |
      | 53.96169,-2.759669 |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.896617,-1.400465 | 55.861284,-4.24996 | car       |

 @Routing
  Scenario Outline: Verify  oneway Restrictions on a Route (Burmingham Route with one way restriction-WSPIP-74)
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
      | pointA              | pointB              | routetype |
      | 52.446564,-1.930268 | 52.446744,-1.929469 | car       |
