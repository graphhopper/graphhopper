Feature: Verify a route from A to B
   As a user
   I want to get a route from location A to location B using the routing service
   And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  @Routing
  Scenario Outline: Verify  waypoints on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                 | azimuth | direction | time  | distance |
      | 1             | 51.472387,-0.361788 | Continue onto ELLINGTON ROAD | 286.0   | W         | 10789 | 104.9    |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Hounslow to Reading
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                         | azimuth | direction | time   | distance |
      | 1             | 51.472387,-0.361788 | Continue onto ELLINGTON ROAD         | 286.0   | W         | 10789  | 104.9    |
      | 9             | 51.477555,-0.403923 | At roundabout, take exit 3 onto A312 | 238.0   | SW        | 115905 | 1770.8   |
      | 14            | 51.451397,-0.960099 | Turn right onto WATLINGTON STREET    | 333.0   | NW        | 15401  | 149.7    |

    Examples: 
      | pointA                                 | pointB                                 | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.45914115860512,-0.96679687499999995 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from Southampton to Glasgow
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                        | azimuth | direction | time    | distance |
      | 1             | 50.896796,-1.400544 | Continue onto PLATFORM ROAD (A33)   | 254.0   | W         | 5514    | 84.3     |
      | 16            | 50.951921,-1.404239 | At roundabout, take exit 1 onto A33 | 318.0   | NW        | 12235   | 187.0    |
      | 17            | 50.953446,-1.403571 | Turn slight right onto M3           | 28.0    | NE        | 3006309 | 83510.0  |
      | 18            | 51.399043,-0.547504 | Continue onto M25                   | 64.0    | NE        | 741727  | 20604.1  |
      | 20            | 52.351964,-1.809695 | Turn slight right onto M42          | 10.0    | N         | 614298  | 17064.2  |
      | 24            | 55.835519,-4.099157 | Continue onto M73                   | 27.0    | NE        | 46569   | 1293.6   |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.896617,-1.400465 | 55.861284,-4.24996 | car       |

  @Routing
  Scenario Outline: Verify  waypoints on a Route from London to Birmingham
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                            | azimuth | direction | time    | distance |
      | 1             | 51.507234,-0.127584 | At roundabout, take exit 2 onto CHARING CROSS (A4)      | 253.0   | W         | 7262    | 111.0    |
      | 7             | 51.517207,-0.142804 | Turn slight left onto A4201                             | 307.0   | NW        | 2812    | 43.0     |
      | 21            | 51.853097,-0.424279 | At roundabout, take exit 1 onto M1                      | 311.0   | NW        | 3011846 | 83663.7  |
      | 22            | 52.399959,-1.175042 | Continue onto M6                                        | 343.0   | N         | 1867732 | 51882.5  |
      | 23            | 52.508912,-1.871271 | Continue onto ASTON EXPRESSWAY (ELEVATED ROAD) (A38(M)) | 240.0   | SW        | 25441   | 706.8    |

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
      | 3             | 50.971952,-1.350891 | Turn left onto THE CRESCENT | 294.0   | NW        | 3832 | 37.3     |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.972281,-1.350942 | 50.972212,-1.351183 | car       |

  @Routing
  Scenario Outline: Verify  No Turn   (WSPIP-76:Eastley- Station Hill Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                      | azimuth | direction | time | distance |
      | 2             | 50.969817,-1.350504 | Continue onto STATION HILL (A335) | 179.0   | S         | 2932 | 44.8     |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.970024,-1.350267 | 50.97008,-1.350521 | car       |

  @Routing
  Scenario Outline: Verify  No Turn   (Treaty Center-Hounslow- Fairfields Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                        | azimuth | direction | time  | distance |
      | 2             | 51.468925,-0.359049 | Turn left onto HANWORTH ROAD (A315) | 239.0   | SW        | 14656 | 224.0    |

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
      | 2             | 51.470846,-0.363527 | Turn right onto LANSDOWNE ROAD | 259.0   | W         | 12772 | 124.2    |

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
      | 2             | 50.993815,-1.461397 | Turn slight right onto HIGHWOOD LANE | 349.0   | N         | 53534 | 520.5    |

    Examples: 
      | pointA              | pointB              | routetype |
      | 50.995817,-1.454224 | 50.998501,-1.454504 | car       |

  @Routing
  Scenario Outline: Verify  Ford Gate at CRAMPOOR ROAD(ROMSEY-Southampton)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                               | azimuth | direction | time  | distance |
      | 3             | 50.782654,-1.060556 | Turn sharp left onto A288 (EASTERN PARADE) | 248.0   | W         | 46622 | 712.3    |

    Examples: 
      | pointA             | pointB              | routetype |
      | 50.78222,-1.059975 | 50.779123,-1.080019 | car       |

  @Routing @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Southampton to London)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                                  |
      | 3             | At roundabout, take exit 2 onto BROWNHILL WAY |
      | 18            | Turn slight left onto KENSINGTON ROAD (A315)  |

    Examples: 
      | pointA                                           | pointB                                 | routetype |
      | 4, ADANAC DRIVE, NURSLING, SOUTHAMPTON, SO16 0AS | 1, PICCADILLY ARCADE, LONDON, SW1Y 6NH | car       |

  @Routing @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Hounslow to Slough)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                                   |
      | 9             | At roundabout, take exit 1 onto BATH ROAD (A4) |
      | 10            | Turn right onto HUNTERCOMBE LANE NORTH         |

    Examples: 
      | pointA                              | pointB                                      | routetype |
      | 135, TIVOLI ROAD, HOUNSLOW, TW4 6AS | 40, CHILTERN ROAD, BURNHAM, SLOUGH, SL1 7NH | car       |

  @Routing @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Southampton to London)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                   |
      | 2             | Turn left onto MONTAGUE STREET |
      | 19            | Continue onto M42              |

    Examples: 
      | pointA                                                      | pointB                                                                                | routetype |
      | BIRMINGHAM VOLKSWAGEN, LAWLEY MIDDLEWAY, BIRMINGHAM, B4 7XH | READING ENTERPRISE CENTRE, UNIVERSITY OF READING, WHITEKNIGHTS ROAD, READING, RG6 6BU | car       |

  @Routing @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Birmingham to reading)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                   |
      | 2             | Turn left onto MONTAGUE STREET |
      | 19            | Continue onto M42              |

    Examples: 
      | pointA                                                      | pointB                                                                                | routetype |
      | BIRMINGHAM VOLKSWAGEN, LAWLEY MIDDLEWAY, BIRMINGHAM, B4 7XH | READING ENTERPRISE CENTRE, UNIVERSITY OF READING, WHITEKNIGHTS ROAD, READING, RG6 6BU | car       |

  @Routing @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Southhampton to London)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                                |
      | 3             | Turn right onto ROYAL CRESCENT ROAD (B3039) |
      | 13            | Turn slight left onto CHARLOTTE PLACE (A33) |

    Examples: 
      | pointA                                               | pointB                           | routetype |
      | 6, CHANNEL WAY, OCEAN VILLAGE, SOUTHAMPTON, SO14 3TG | 311, CITY ROAD, LONDON, EC1V 1LA | car       |

  @Routing @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Coventry)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                        |
      | 3             | Turn right onto HOWES LANE (B4115)  |
      | 6             | Turn right onto WARWICK ROAD (A429) |
      | 17            | Continue onto HALFORD LANE          |

    Examples: 
      | pointA                                                         | pointB                              | routetype |
      | 3 BROMLEIGH VILLAS, COVENTRY ROAD, BAGINTON, COVENTRY, CV8 3AS | 2, PAXMEAD CLOSE, COVENTRY, CV6 2NJ | car       |

  @Routing @WebOnly
  Scenario Outline: Verify  Route using Full UK Address (Kington to London )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointdesc                        |
      | 6             | At roundabout, take exit 1 onto A44 |
      | 16            | Turn right onto A456                |

    Examples: 
      | pointA                           | pointB                                | routetype |
      | 5, OXFORD LANE, KINGTON, HR5 3ED | 64, TOWER MILL ROAD, LONDON, SE15 6BZ | car       |

@Routing
  Scenario Outline: Verify a Roundabout(Charles Watts Way)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                             | azimuth | direction | time  | distance |
      | 3             | 50.920147,-1.310351 | At roundabout, take exit 2 onto CHARLES WATTS WAY (A334) | 0.0     | N         | 30483 | 465.7    |

    Examples: 
      | pointA             | pointB              | routetype |
      | 50.915416,-1.31902 | 50.915551,-1.294049 | car       |

  @Routing
  Scenario Outline: Verify a Roundabout(A30)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc              | azimuth | direction | time  | distance |
      | 3             | 50.726474,-3.727558 | Turn slight left onto A30 | 4.0     | N         | 20199 | 308.6    |

    Examples: 
      | pointA              | pointB             | routetype |
      | 50.729071,-3.732732 | 50.72813,-3.730887 | car       |

  @Routing
  Scenario Outline: Verify a Roundabout(The City Of Edinburgh By-pass)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                         | azimuth | direction | time  | distance |
      | 3             | 55.913915,-3.065976 | At roundabout, take exit 1 onto A720 | 199.0   | S         | 85044 | 1299.3   |

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
      | 15            | 51.355407,-0.679946 | At roundabout, take exit 3 onto A322 | 184.0   | S         | 388536 | 5936.2   |

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
      | 7             | 51.410306,-0.668737 | Turn right onto WINKFIELD ROAD (A330) | 7.0     | N         | 62537  | 955.5    |

    Examples: 
      | pointA              | pointB              | pointC             | routetype |
      | 51.409426,-0.591727 | 51.407904,-0.617237 | 51.41855,-0.672385 | car       |

  @Routing
  Scenario Outline: Verify  Route using one intermediate waypoint ( Chelsea to Winchester via Windlesham)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                       | azimuth | direction | time    | distance |
      | 4             | 51.486844,-0.252027 | At roundabout, take exit 3 onto A4 | 189.0   | S         | 119731  | 1829.3   |
      | 9             | 51.36165,-0.645969  | Continue onto M3                   | 238.0   | SW        | 2142697 | 59520.3  |
      | 13            | 51.069901,-1.296261 | Turn slight left onto EASTON LANE  | 232.0   | SW        | 63727   | 619.6    |

    Examples: 
      | pointA             | pointB             | pointC              | routetype |
      | 51.48676,-0.170426 | 51.36166,-0.645979 | 51.070889,-1.315293 | car       |

  @Routing
  Scenario Outline: Verify  Route using 2 intermediate waypoints (Hounslow to Reading via Staines and Bracknell )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                            | azimuth | direction | time   | distance |
      | 1             | 51.472387,-0.361788 | Continue onto ELLINGTON ROAD            | 286.0   | W         | 10789  | 104.9    |
      | 9             | 51.466735,-0.365923 | Turn slight left onto GROVE ROAD (A315) | 74.0    | E         | 3684   | 56.3     |
      | 16            | 51.355407,-0.679946 | At roundabout, take exit 3 onto A322    | 184.0   | S         | 388536 | 5936.2   |
      | 28            | 51.409769,-0.787117 | At roundabout, take exit 2 onto A329    | 144.0   | SE        | 43272  | 661.1    |
      | 54            | 51.494214,-0.505796 | Continue onto M25                       | 74.0    | E         | 302474 | 8402.4   |

    Examples: 
      | pointA                                 | pointB              | pointC                                 | pointD              | routetype |
      | 51.471546541834144,-0.3618621826171875 | 51.414152,-0.747504 | 51.45914115860512,-0.96679687499999995 | 51.433882,-0.537904 | car       |

  @Routing
  Scenario Outline: Verify  Route using 2 intermediate waypoints (Oxford to Eaton via Warwick and Cambridge )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                     | azimuth | direction | time   | distance |
      | 5             | 51.748432,-1.261457 | Turn left onto THAMES STREET (A420)              | 275.0   | W         | 9530   | 145.6    |
      | 21            | 52.290635,-1.606686 | Continue onto HAYWOOD ROAD                       | 209.0   | SW        | 22502  | 218.8    |
      | 32            | 52.298017,-1.535731 | Continue onto LILLINGTON AVENUE (A445)           | 59.0    | NE        | 27364  | 418.1    |
      | 68            | 51.491599,-0.541244 | At roundabout, take exit 4 onto LONDON ROAD (A4) | 184.0   | S         | 188894 | 2886.2   |

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
