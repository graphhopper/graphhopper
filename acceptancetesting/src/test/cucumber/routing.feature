Feature: Verify a route from A to B
   As a user
   I want to get a route from location A to location B using the routing service
   And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  @Routing
  Scenario Outline: Verify  waypoints on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                 | azimuth | direction | time | distance |
      | 1             | 51.472387,-0.361788 | Continue onto ELLINGTON ROAD | 286.0   | W         | 8390 | 104.9    |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Hounslow to Reading
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                           | azimuth | direction | time  | distance |
      | 1             | 51.472387,-0.361788 | Continue onto ELLINGTON ROAD           | 286.0   | W         | 8390  | 104.9    |
      | 9             | 51.435626,-0.866024 | Continue onto A329(M)                  | 301.0   | NW        | 72994 | 2027.7   |
      | 16            | 51.453903,-0.961826 | Continue onto WATLINGTON STREET (A329) | 341.0   | N         | 8584  | 132.7    |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Southampton to Glasgow
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                             | azimuth | direction | time    | distance |
      | 1             | 50.896796,-1.400544 | Continue onto PLATFORM ROAD (A33)                        | 254.0   | W         | 3192    | 84.3     |
      | 16            | 50.951921,-1.404239 | At roundabout, take exit 1 onto A33                      | 318.0   | NW        | 7083    | 187.0    |
      | 17            | 50.953446,-1.403571 | Turn slight right onto M3                                | 28.0    | NE        | 566900  | 15747.6  |
      | 18            | 51.07086,-1.292917  | At roundabout, take exit 2 onto A34 (WINCHESTER BY-PASS) | 284.0   | NE        | 55129   | 1454.8   |
      | 20            | 51.868385,-1.199845 | At roundabout, take exit 1 onto M40                      | 357.0   | N         | 2636747 | 73242.2  |
      | 24            | 52.381175,-1.790061 | At roundabout, take exit 1 onto A34 (STRATFORD ROAD)     | 301.0   | NW        | 46514   | 1227.5   |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.896617,-1.400465 | 55.861284,-4.24996 | car       |

  @Routing 
  Scenario Outline: Verify  waypoints on a Route from London to Birmingham
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                       | azimuth | direction | time  | distance |
      | 1             | 51.507234,-0.127584 | At roundabout, take exit 2 onto CHARING CROSS (A4) | 253.0   | W         | 4202  | 111.0    |
      | 7             | 51.517207,-0.142804 | Turn slight left onto A4201                        | 307.0   | NW        | 1628  | 43.0     |
      | 21            | 51.577774,-0.220823 | Continue onto A41 (HENDON WAY)                     | 301.0   | NW        | 28342 | 748.0    |
      | 22            | 51.582726,-0.227154 | Continue onto A41 (WATFORD WAY)            | 340.0   | N        | 88373 | 2332.3    |
      | 23            | 51.601209,-0.234509 | Continue onto A1 (WATFORD WAY (BARNET BY-PASS))                     | 325.0   | NW        | 72410 | 1911.0    |

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
      | 52.52355,-1.902136 |
      | 53.779418,-2.647821 |
      | 54.304996,-2.646641 |
      |55.802602,-4.053713|

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
  Scenario Outline: Verify  No Turn Restrictions  on a Route (Birmingham WSPIP-77)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco        |
      | 52.446779,-1.929385 |

    Examples: 
      | pointA              | pointB              | routetype |
      | 52.446564,-1.930268 | 52.446744,-1.929469 | car       |

  @KnownIssues
  Scenario Outline: Verify  No Turn Restrictions  on a Route (Birmingham Bristol Road WSPIP-83)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco        |
      | 52.446764,-1.929391 |

    Examples: 
      | pointA              | pointB              | routetype |
      | 52.446823,-1.929077 | 52.446672,-1.929691 | car       |

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
      | wayPointIndex | waypointco          | waypointdesc                | azimuth | direction | time | distance |
      | 3             | 50.971952,-1.350891 | Turn left onto THE CRESCENT | 294.0   | NW        | 2981 | 37.3     |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.972281,-1.350942 | 50.972212,-1.351183 | car       |

  @Routing 
  Scenario Outline: Verify  No Turn   (WSPIP-76:Eastley- Station Hill Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                      | azimuth | direction | time | distance |
      | 2             | 50.969817,-1.350504 | Continue onto STATION HILL (A335) | 180.0   | S         | 4583 | 57.3     |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.970024,-1.350267 | 50.97008,-1.350521 | car       |

  @Routing 
  Scenario Outline: Verify  No Turn   (Treaty Center-Hounslow- Fairfields Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                        | azimuth | direction | time  | distance |
      | 2             | 51.468925,-0.359049 | Turn left onto HANWORTH ROAD (A315) | 239.0   | SW        | 17328 | 224.0    |

    Examples: 
      | pointA             | pointB              | routetype |
      | 51.46882,-0.358687 | 51.469454,-0.357831 | car       |

  @KnownIssues
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
      | 2             | 51.470846,-0.363527 | Turn right onto LANSDOWNE ROAD | 259.0   | W         | 9934 | 124.2    |

    Examples: 
      | pointA             | pointB              | routetype |
      | 51.47118,-0.363609 | 51.470254,-0.363412 | car       |

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
      | 2             | 50.993815,-1.461397 | Turn slight right onto HIGHWOOD LANE | 349.0   | N         | 41337 | 520.5    |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.995817,-1.454224 | 50.998501,-1.454504 | car       |

  @Routing
  Scenario Outline: Verify  Ford Gate at CRAMPOOR ROAD(ROMSEY-Southampton)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                               | azimuth | direction | time  | distance |
      | 3             | 50.782654,-1.060556 | Turn sharp left onto A288 (EASTERN PARADE) | 248.0   | W         | 56982 | 712.3    |

    Examples: 
      | pointA             | pointB              | routetype |
      | 50.78222,-1.059975 | 50.779123,-1.080019 | car       |

  @Routing @WebOnly 
  Scenario Outline: Verify  Route using Full UK Address (Southampton to London)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                                  |
      | 3             | At roundabout, take exit 2 onto BROWNHILL WAY |
      | 18            | Continue onto PICCADILLY (A4)  |

    Examples: 
      | pointA                                           | pointB                                 | routetype |
      | ORDNANCE SURVEY, 4, ADANAC DRIVE, NURSLING, SOUTHAMPTON, SO16 0AS | 1, PICCADILLY ARCADE, LONDON, SW1Y 6NH | car       |

  @Routing @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Hounslow to Slough)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                                   |
      | 9             | At roundabout, take exit 1 onto BATH ROAD (A4) |
      | 10            | Turn right onto HUNTERCOMBE LANE NORTH         |

    Examples: 
      | pointA                              | pointB                                      | routetype |
      | 131, TIVOLI ROAD, HOUNSLOW, TW4 6AS | 40, CHILTERN ROAD, BURNHAM, SLOUGH, SL1 7NH | car       |

  @Routing   @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Southampton to London)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                   |
      | 2             | Turn sharp left onto A35 (TEBOURBA WAY) |
      | 20            | Turn slight left onto PALL MALL (A4)              |

    Examples: 
      | pointA                                                      | pointB                                                                                | routetype |
      | SOUTHAMPTON MEGABOWL, AUCKLAND ROAD, SOUTHAMPTON, SO15 0SD | CANARY WHARF LTD, 1, CANADA SQUARE, LONDON, E14 5AB | car       |

  @Routing  @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Birmingham to reading)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                   |
      | 2             | Turn left onto MONTAGUE STREET |
      | 19            | At roundabout, take exit 2 onto A34 (STRATFORD ROAD)             |

    Examples: 
      | pointA                                                      | pointB                                                                                | routetype |
      | BIRMINGHAM VOLKSWAGEN, LAWLEY MIDDLEWAY, BIRMINGHAM, B4 7XH | READING ENTERPRISE CENTRE, UNIVERSITY OF READING, WHITEKNIGHTS ROAD, READING, RG6 6BU | car       |

  @Routing @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Southhampton to London)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                                |
      | 3             | Continue onto ENDLE STREET |
      | 21            | At roundabout, take exit 3 onto A30 |

    Examples: 
      | pointA                                               | pointB                           | routetype |
      | 6, CHANNEL WAY, OCEAN VILLAGE, SOUTHAMPTON, SO14 3TG | 311, CITY ROAD, LONDON, EC1V 1LA | car       |

  @Routing @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Coventry)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                        |
      | 3             | Turn right onto HOWES LANE (B4115)  |
      | 6             | At roundabout, take exit 2 onto A444 |
      | 16            | Turn right onto PENNY PARK LANE          |

    Examples: 
      | pointA                                                         | pointB                              | routetype |
      | 3 BROMLEIGH VILLAS, COVENTRY ROAD, BAGINTON, COVENTRY, CV8 3AS | 2, PAXMEAD CLOSE, COVENTRY, CV6 2NJ | car       |

  @Routing  @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Kington to London )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                        |
      | 6             | Turn slight left onto HEADBROOK |
      | 16            | At roundabout, take exit 3 onto A49 (VICTORIA STREET)                |

    Examples: 
      | pointA                           | pointB                                | routetype |
      | 5, OXFORD LANE, KINGTON, HR5 3ED | 64, TOWER MILL ROAD, LONDON, SE15 6BZ | car       |

  @Routing 
  Scenario Outline: Verify a Roundabout(Charles Watts Way)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                             | azimuth | direction | time  | distance |
      | 3             | 50.920147,-1.310351 | At roundabout, take exit 2 onto CHARLES WATTS WAY (A334) | 0.0     | N         | 17647 | 465.7    |

    Examples: 
      | pointA             | pointB              | routetype |
      | 50.915416,-1.31902 | 50.915551,-1.294049 | car       |

  @Routing 
  Scenario Outline: Verify a Roundabout(A30)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc              | azimuth | direction | time  | distance |
      | 3             | 50.726474,-3.727558 | Turn slight left onto A30 | 4.0     | N         | 11694 | 308.6    |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.729071,-3.732732 | 50.72813,-3.730887 | car       |

  @Routing 
  Scenario Outline: Verify a Roundabout(The City Of Edinburgh By-pass)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                         | azimuth | direction | time  | distance |
      | 3             | 55.913915,-3.065976 | At roundabout, take exit 1 onto A720 | 199.0   | S         | 49235 | 1299.3   |

    Examples: 
      | pointA              | pointB              | routetype |
      | 55.913061,-3.060099 | 55.924345,-3.053462 | car       |

  @Routing
  Scenario Outline: Verify  Route using one intermediate waypoint (Hounslow to Reading via Staines )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                         | azimuth | direction | time   | distance |
      | 1             | 51.472387,-0.361788 | Continue onto ELLINGTON ROAD         | 286.0   | W         | 10789  | 104.9    |
      | 9             | 51.477555,-0.403923 | At roundabout, take exit 3 onto A312 | 238.0   | SW        | 115905 | 1770.8   |
      | 15            | 51.355407,-0.679946 | At roundabout, take exit 3 onto A322 | 184.0   | S         | 224937 | 5936.2   |

    Examples: 
      | pointA                                 | pointB              | pointC                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.433882,-0.537904 | 51.45914115860512,-0.96679687499999995 | car       |

  @Routing 
  Scenario Outline: Verify  Route using one intermediate waypoint (Wentworth to Ascot via Windsor Park )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                          | azimuth | direction | time   | distance |
      | 2             | 51.40643,-0.596399  | Turn right onto BLACKNEST ROAD (A329) | 289.0   | W         | 73038  | 1115.9   |
      | 5             | 51.407984,-0.617235 | Continue onto LONDON ROAD (A329)      | 274.0   | W         | 166263 | 2540.3   |
      | 7             | 51.410306,-0.668737 | Turn right onto WINKFIELD ROAD (A330) | 7.0     | N         | 46532  | 955.5    |

    Examples: 
      | pointA              | pointB              | pointC             | routetype |
      | 51.409426,-0.591727 | 51.407904,-0.617237 | 51.41855,-0.672385 | car       |

  @Routing  
    Scenario Outline: Verify  Route using one intermediate waypoint ( Chelsea to Winchester via Windlesham)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                       | azimuth | direction | time    | distance |
      | 4             | 51.489964,-0.172906 | Turn left onto DOVEHOUSE STREET | 321.0   | NW         | 7600  | 95.0   |
      | 9             | 51.493673,-0.174548  | Turn right onto PELHAM STREET (A3218)                  | 0.0   | N        | 2446 | 55.7  |
      | 13            | 51.486844,-0.252027 | At roundabout, take exit 3 onto A4  | 189.0   | S        | 69313   |1829.3   |

    Examples: 
      | pointA             | pointB             | pointC              | routetype |
      | 51.48676,-0.170426 | 51.36166,-0.645979 | 51.070889,-1.315293 | car       |

  @Routing  @yogi
  Scenario Outline: Verify  Route using 2 intermediate waypoints (Hounslow to Reading via Staines and Bracknell )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                            | azimuth | direction | time   | distance |
      | 1             | 51.472387,-0.361788 | Continue onto ELLINGTON ROAD            | 286.0   | W         | 8390  | 104.9    |
      | 9             | 51.406127,-0.539808 | Continue onto M3 | 162.0    | S         | 445073   | 12363.5    |
      | 16            |51.414151,-0.747502 |Continue onto CHURCH ROAD (A3095)   | 28.0  | NE         | 12891 |340.2   |
      | 27            | 51.451397,-0.960099 | Turn right onto WATLINGTON STREET   | 333.0   | NW        | 11978  | 149.7    |
      | 55            | 51.440767,-0.531845 | Continue onto A30                       | 17.0    | N         | 14025 | 370.1  |


    Examples: 
      | pointA                                 | pointB              | pointC                                 | pointD              | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.414152,-0.747504 | 51.45914115860512,-0.96679687499999995 | 51.433882,-0.537904 | car       |


@Routing
  Scenario Outline: Verify  Route using 2 intermediate waypoints (Oxford to Eaton via Warwick and Cambridge )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                         | azimuth | direction | time   | distance |
      | 5             | 51.748432,-1.261457 | Turn left onto THAMES STREET (A420)                  | 275.0   | W         | 5517   | 145.6    |
      | 21            | 52.289769,-1.60905  | At roundabout, take exit 3 onto A46                  | 249.0   | W         | 481412 | 12704.3  |
      | 32            | 52.256925,-0.123683 | At roundabout, take exit 3 onto ST IVES ROAD (A1198) | 95.0    | E         | 57226  | 1510.2   |
      | 68            | 51.560087,-0.496049 | At roundabout, take exit 2 onto A412 (DENHAM ROAD)   | 98.0    | E         | 31561  | 832.9    |

    Examples: 
      | pointA              | pointB              | pointC             | pointD              | routetype |
      | 51.746075,-1.263972 | 52.289962,-1.604752 | 52.202814,0.051429 | 51.491412,-0.610276 | car       |

  @Routing
  Scenario Outline: Verify  Route using 2 intermediate waypoints (Perth to Edinburgh via Stirling and Glasgow )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                         | azimuth | direction | time   | distance |
      | 5             | 56.170837,-3.970499 | At roundabout, take exit 3 onto M9   | 91.0    | E         | 148299 | 3961.4   |
      | 11            | 55.874151,-4.184032 | Continue onto GARTLOCH ROAD (B765)   | 98.0    | E         | 1145   | 11.1     |
      | 20            | 55.924663,-3.31288  | At roundabout, take exit 3 onto A720 | 358.0   | N         | 58713  | 897.1    |
      | 30            | 55.948658,-3.212478 | Turn left onto WALKER STREET         | 321.0   | NW        | 16074  | 156.3    |

    Examples: 
      | pointA             | pointB              | pointC              | pointD              | routetype |
      | 56.38721,-3.466273 | 56.136656,-3.970408 | 55.871665,-4.195067 | 55.950467,-3.208924 | car       |

