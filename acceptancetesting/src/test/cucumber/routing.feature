Feature: Verify a route from A to B
    As a user
    I want to get a route from location A to location B using the routing service
    And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  @Routing
  Scenario Outline: Verify  waypoints on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                 | azimuth | direction | time  | distance |
      | 1             | 51.472387,-0.361788 | Continue onto ELLINGTON ROAD | 280     | W         | 10789 | 104.896  |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Hounslow to Reading
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                      | azimuth | direction | time    | distance  |
      | 1             | 51.472387,-0.361788 | Continue onto ELLINGTON ROAD      | 280     | W         | 10789   | 104.896   |
      | 9             | 51.491777,-0.41102  | Turn slight left onto M4          | 303     | NW        | 1298429 | 36068.472 |
      | 13            | 51.451397,-0.960099 | Turn right onto WATLINGTON STREET | 321     | NW        | 15401   | 149.744   |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Southampton to Glasgow
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                      | azimuth | direction | time     | distance   |
      | 1             | 50.896796,-1.400544 | Continue onto PLATFORM ROAD (A33) | 261     | W         | 5514     | 84.266     |
      | 16            | 50.953446,-1.403571 | Turn slight right onto M3         | 41      | NE        | 3006306  | 83510.255  |
      | 17            | 51.399043,-0.547504 | Continue onto M25                 | 74      | E         | 741727   | 20604.228  |
      | 18            | 51.561606,-0.539424 | Continue onto M40                 | 277     | W         | 4953466  | 137598.554 |
      | 20            | 52.480513,-1.719489 | Continue onto M6                  | 301     | NW        | 12028330 | 334127.547 |
      | 24            | 55.846513,-4.092642 | Turn slight left onto M8          | 360     | N         | 395981   | 11000.027  |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.896617,-1.400465 | 55.861284,-4.24996 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from London to Birmingham
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                            | azimuth | direction | time    | distance   |
      | 1             | 51.507234,-0.127584 | Continue onto CHARING CROSS (A4)                        | 260     | W         | 7262    | 110.965    |
      | 7             | 51.517207,-0.142804 | Turn slight left onto A4201                             | 295     | NW        | 2812    | 42.971     |
      | 21            | 51.571905,-0.230521 | Turn slight left onto M1                                | 33      | NE        | 4377586 | 121602.814 |
      | 22            | 52.399959,-1.175042 | Continue onto M6                                        | 334     | NW        | 1867733 | 51882.763  |
      | 23            | 52.508912,-1.871271 | Continue onto ASTON EXPRESSWAY (ELEVATED ROAD) (A38(M)) | 251     | W         | 25441   | 706.768    |

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
      | trackPointco        |
      | 53.014721,-2.327641 |
      | 54.402164,-2.604933 |
      | 55.411387,-3.575691 |

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

  @Routing @KnownIssues
  Scenario Outline: Verify  No Turn Restrictions  on a Route (Birmingham Bristol Road WSPIP-83)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco        |
      | 52.446764,-1.929391 |

    Examples: 
      | pointA              | pointB              | routetype |
      | 52.446823,-1.929077 | 52.446672,-1.929691 | car       |

  @Routing @KnownIssues
  Scenario Outline: Verify  one Way  Restrictions  on a Route (Exeter WSPIP-83)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time  | distance |
      | 7             | 50.722198,-3.526704 | Turn left onto SOUTHERNHAY EAST | 32      | NE        | 11069 | 107.648  |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.720454,-3.530089 | 50.722657,-3.526321 | car       |

  @Routing
  Scenario Outline: Verify  One-Way(No Entry)Restriction   (SIVELL PLACE-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc               | azimuth | direction | time | distance |
      | 2             | 50.720531,-3.504654 | Turn left onto SIVELL MEWS | 24      | NE        | 4603 | 44.754   |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.720561,-3.504848 | 50.720608,-3.505677 | car       |

  @Routing
  Scenario Outline: Verify  under pass still finds route  from top road (Southampton- Charle WattsWay)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints on the route map:
      | trackPointco        |
      | 50.917268,-1.316368 |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.917598,-1.317992 | 50.919748,-1.310342 | car       |

  @Routing
  Scenario Outline: Verify  under pass still finds route from bottom road  (Southampton- Charle WattsWay)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints on the route map:
      | trackPointco        |
      | 50.919194,-1.316553 |

    Examples: 
      | pointA             | pointB             | routetype |
      | 50.91525,-1.318761 | 50.92045,-1.316021 | car       |

  @Routing
  Scenario Outline: Verify  No Turn   (WSPIP-76:Eastley- TWYFORD ROAD )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                       | azimuth | direction | time | distance |
      | 3             | 50.971186,-1.350769 | Turn left onto TWYFORD ROAD (A335) | 353     | N         | 5625 | 85.961   |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.972281,-1.350942 | 50.972212,-1.351183 | car       |

  @Routing
  Scenario Outline: Verify  No Turn   (WSPIP-76:Eastley- Station Hill Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                              | azimuth | direction | time | distance |
      | 2             | 50.969817,-1.350504 | Turn slight left onto STATION HILL (A335) | 180     | S         | 2932 | 44.811   |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.970024,-1.350267 | 50.97008,-1.350521 | car       |

  @Routing
  Scenario Outline: Verify  No Turn   (Treaty Center-Hounslow- Fairfields Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                               | azimuth | direction | time  | distance |
      | 2             | 51.468925,-0.359049 | Turn slight left onto A315 (HANWORTH ROAD) | 250     | W         | 14656 | 223.972  |

    Examples: 
      | pointA             | pointB              | routetype |
      | 51.46882,-0.358687 | 51.469454,-0.357831 | car       |

  @Routing @KnownIssues
  Scenario Outline: Verify  No Turns with Exceptions(Vehicle Type:Bus)   (High Street-Hounslow)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco        |
      | 51.470009,-0.357019 |

    Examples: 
      | pointA              | pointB              | routetype |
      | 51.470198,-0.356036 | 51.470352,-0.357388 | car       |

  @Routing
  Scenario Outline: Verify  Mandatory Turn   (Alexandra Road-Hounslow- Fairfields Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                   | azimuth | direction | time  | distance |
      | 2             | 51.470846,-0.363527 | Turn right onto LANSDOWNE ROAD | 264     | W         | 12772 | 124.177  |

    Examples: 
      | pointA             | pointB              | routetype |
      | 51.47118,-0.363609 | 51.470254,-0.363412 | car       |

  @Routing @KnownIssues
  Scenario Outline: Verify  No Turn Restriction (Denmark Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc                 | azimuth | direction | time | distance |
      | 2             | 50.724703,-3.520835 | turn right onto DENMARK ROAD | 208     | SW        | 2141 | 20.822   |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.724901,-3.521588 | 50.724524,-3.520923 | car       |

  @Routing @KnownIssues
  Scenario Outline: Verify  Mandatory Turn Restriction (Denmark Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc                           | azimuth | direction | time  | distance |
      | 2             | 50.724703,-3.520835 | Turn right onto HEAVITREE ROAD (B3183) | 105     | E         | 15636 | 152.027  |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.724378,-3.520993 | 50.72413,-3.518874 | car       |

  @Routing @KnownIssues
  Scenario Outline: Verify  Private Road Restricted Access (Denmark Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco      |
      | 50.723966,-3.5198 |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.724316,-3.521008 | 50.72413,-3.518874 | car       |

  @Routing
  Scenario Outline: Verify  Private Road Restricted Access (Warwick Road-Carlisle)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco        |
      | 54.894721,-2.921665 |

    Examples: 
      | pointA              | pointB            | routetype |
      | 54.894427,-2.921111 | 54.8922,-2.928296 | car       |

  @Routing
  Scenario Outline: Verify  Ford Gate at CRAMPOOR ROAD(ROMSEY-Southampton)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                         | azimuth | direction | time  | distance |
      | 2             | 50.993815,-1.461397 | Turn slight right onto HIGHWOOD LANE | 344     | N         | 53534 | 520.487  |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.995817,-1.454224 | 50.998501,-1.454504 | car       |

  @Routing
  Scenario Outline: Verify  Route using Full UK Address (Southampton to London)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                          |
      | 3             | Turn left onto BROWNHILL WAY          |
      | 18            | Continue onto A219 (HAMMERSMITH ROAD) |

    Examples: 
      | pointA                                           | pointB                                 | routetype |
      | 4, ADANAC DRIVE, NURSLING, SOUTHAMPTON, SO16 0AS | 1, PICCADILLY ARCADE, LONDON, SW1Y 6NH | car       |

  @Routing
  Scenario Outline: Verify  Route using Full UK Address (Hounslow to Slough)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                           |
      | 9             | Turn right onto HUNTERCOMBE LANE NORTH |
      | 10            | Turn left onto WENDOVER ROAD           |

    Examples: 
      | pointA                              | pointB                                      | routetype |
      | 135, TIVOLI ROAD, HOUNSLOW, TW4 6AS | 40, CHILTERN ROAD, BURNHAM, SLOUGH, SL1 7NH | car       |

  @Routing 
  Scenario Outline: Verify  Route using Full UK Address (Southampton to London)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                          |
      | 2             | Turn left onto MONTAGUE STREET        |
      | 19            | Turn right onto WHITEKNIGHTS ROAD |

    Examples: 
      | pointA                                           | pointB                                 | routetype |
      | BIRMINGHAM VOLKSWAGEN, LAWLEY MIDDLEWAY, BIRMINGHAM, B4 7XH | READING ENTERPRISE CENTRE, UNIVERSITY OF READING, WHITEKNIGHTS ROAD, READING, RG6 6BU | car       |
