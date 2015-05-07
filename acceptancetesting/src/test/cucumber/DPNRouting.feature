Feature: Verify a route from A to B
    As a user
    I want to get a route from location A to location B by Foot using the routing service
    And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  (Mill lane-BUXTON)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco        | waypointdesc            | azimuth | direction | time  | distance |
      | 4             | 53.1356,-1.820891 | Continue onto Mill Lane | 69.0    | E         | 23165 | 32.2     |

    Examples: 
      | pointA              | pointB             | routetype |
      | 53.176062,-1.871472 | 53.154773,-1.77272 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  (Chatswoth Park)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                  | azimuth | direction | time   | distance |
      | 2             | 53.221055,-1.623152 | Turn right onto B Road        | 143.0   | SE        | 953593 | 1324.4   |
      | 5             | 53.197269,-1.608797 | Continue onto Chatsworth Road | 180.0   | S         | 670049 | 930.6    |

    Examples: 
      | pointA              | pointB              | routetype |
      | 53.211013,-1.619393 | 53.185757,-1.611969 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Musden Low)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc          | azimuth | direction | time  | distance |
      | 3             | 53.042479,-1.820522 | Turn right onto Route | 237.0   | SW        | 25753 | 35.8     |

    Examples: 
      | pointA              | pointB              | routetype |
      | 53.049589,-1.823866 | 53.076372,-1.853379 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (A54)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc         | azimuth | direction | time   | distance |
      | 4             | 53.176842,-2.069334 | Turn left onto Track | 265.0   | W         | 171340 | 238.0    |

    Examples: 
      | pointA              | pointB              | routetype |
      | 53.173064,-2.060321 | 53.214387,-2.017271 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Townhead )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc                | azimuth | direction | time  | distance |
      | 5             | 53.11862,-1.909506 | Turn slight left onto Route | 152.0   | SE        | 40897 | 56.8     |

    Examples: 
      | pointA              | pointB             | routetype |
      | 53.122676,-1.909914 | 53.088159,-1.87142 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Martin's Low)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc          | azimuth | direction | time  | distance |
      | 3             | 53.066198,-1.905401 | Turn right onto Track | 103.0   | E         | 38673 | 53.7     |

    Examples: 
      | pointA             | pointB              | routetype |
      | 53.06535,-1.906169 | 53.100994,-1.956274 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Castleton Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                  | azimuth | direction | time   | distance |
      | 2             | 53.347406,-1.760973 | Turn left onto Castleton Road | 81.0    | E         | 855317 | 1187.9   |

    Examples: 
      | pointA              | pointB              | routetype |
      | 53.348832,-1.761122 | 53.197338,-1.594157 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Hernstone Lane )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                 | azimuth | direction | time   | distance |
      | 3             | 53.305821,-1.814508 | Continue onto Hernstone Lane | 299.0   | NW        | 298331 | 414.4    |

    Examples: 
      | pointA              | pointB              | routetype |
      | 53.300714,-1.786126 | 53.287803,-1.816746 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Monyash Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc               | azimuth | direction | time   | distance |
      | 5             | 53.20882,-1.688212 | Continue onto Monyash Road | 54.0    | NE        | 445921 | 619.3    |

    Examples: 
      | pointA              | pointB              | routetype |
      | 53.194909,-1.710481 | 53.156696,-1.634947 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Whitfield lane)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc               | azimuth | direction | time   | distance |
      | 4             | 53.143286,-1.647841 | Turn right onto Elton Road | 287.0   | W         | 193296 | 268.5    |

    Examples: 
      | pointA              | pointB              | routetype |
      | 53.142876,-1.642599 | 53.163897,-1.714249 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  (Cardlemere Lane)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                          | azimuth | direction | time   | distance |
      | 4             | 53.129383,-1.754591 | Turn slight left onto Cardlemere Lane | 127.0   | SE        | 581179 | 807.2    |

    Examples: 
      | pointA              | pointB             | routetype |
      | 53.114295,-1.762789 | 53.086961,-1.69626 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route using one intermediate point ( Old Coalpit Lane)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time   | distance |
      | 2             | 53.23952,-1.803512  | Turn left onto Sough Lane       | 225.0   | SW        | 350707 | 487.1    |
      | 29            | 53.140548,-1.810174 | Turn slight left onto Mill Lane | 205.0   | SW        | 48332  | 67.1     |
      | 34            | 53.129146,-1.866738 | Turn left onto Cheadle Road     | 190.0   | S         | 169171 | 235.0    |

    Examples: 
      | pointA              | pointB            | pointC              | routetype |
      | 53.238625,-1.794511 | 53.1651,-1.776435 | 53.125221,-1.871205 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route using one intermediate point ( Newhouses Farm)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                          | azimuth | direction | time    | distance |
      | 7             | 53.305394,-1.819253 | Turn slight left onto Hernstone Lane  | 43.0    | NE        | 298331  | 414.4    |
      | 12            | 53.25475,-1.727239  | Turn slight left onto Castlegate Lane | 179.0   | S         | 1149598 | 1596.7   |

    Examples: 
      | pointA              | pointB             | pointC              | routetype |
      | 53.303058,-1.836061 | 53.28261,-1.761964 | 53.233207,-1.633878 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route using one intermediate point ( Bakewell)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                            | azimuth | direction | time    | distance |
      | 6             | 53.145466,-1.778242 | Turn slight right onto Tissington Trail | 355.0   | N         | 1731124 | 2404.3   |
      | 15            | 53.195536,-1.762602 | Continue onto Church Street             | 74.0    | E         | 906984  | 1259.7   |
      | 19            | 53.20882,-1.688212  | Continue onto Monyash Road              | 54.0    | NE        | 359536  | 499.4    |

    Examples: 
      | pointA              | pointB              | pointC              | routetype |
      | 53.138247,-1.752507 | 53.195653,-1.762655 | 53.211574,-1.682278 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  using 2 intermediate waypoints (Mill Lane)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time   | distance |
      | 5             | 53.140548,-1.810174 | Turn slight left onto Mill Lane | 205.0   | SW        | 48332  | 67.1     |
      | 12            | 53.131356,-1.852045 | Turn slight right onto Path     | 305.0   | NW        | 700132 | 972.4    |
      | 16            | 53.181282,-1.869038 | Turn left onto Market Place     | 315.0   | NW        | 791    | 1.1      |

    Examples: 
      | pointA              | pointB              | pointC             | pointD              | routetype |
      | 53.139805,-1.803217 | 53.133646,-1.826223 | 53.14993,-1.868096 | 53.181298,-1.869034 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  using 2 intermediate waypoints (Tag Lane)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                            | azimuth | direction | time   | distance |
      | 7             | 53.227765,-1.848174 | Turn slight right onto Old Coalpit Lane | 73.0    | E         | 902277 | 1253.2   |
      | 18            | 53.244806,-1.809527 | Turn slight left onto Blackwell Dale    | 25.0    | NE        | 988875 | 1373.4   |
      | 26            | 53.281439,-1.765527 | Continue onto Whitecross Road           | 53.0    | NE        | 218224 | 303.1    |
      | 32            | 53.224822,-1.70717  | Turn left onto Hall End Lane            | 68.0    | E         | 78256  | 108.7    |

    Examples: 
      | pointA              | pointB              | pointC              | pointD              | routetype |
      | 53.190346,-1.802704 | 53.239419,-1.818421 | 53.280601,-1.764495 | 53.233207,-1.633878 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  using 2 intermediate waypoints (Dowlow Farm)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                       | azimuth | direction | time   | distance |
      | 2             | 53.206014,-1.83483  | Turn right onto Midshires Way      | 191.0   | S         | 265173 | 368.3    |
      | 19            | 53.202937,-1.870926 | Continue onto Glutton Dale         | 220.0   | SW        | 267936 | 372.1    |
      | 28            | 53.181324,-1.869107 | Turn slight left onto Market Place | 135.0   | SE        | 4729   | 6.6      |
      | 32            | 53.129146,-1.866738 | Continue onto Cheadle Road         | 190.0   | S         | 169171 | 235.0    |

    Examples: 
      | pointA              | pointB              | pointC              | pointD             | routetype |
      | 53.206965,-1.839021 | 53.203607,-1.857557 | 53.149631,-1.867364 | 53.11417,-1.895082 | foot      |

  # Avoidances : A Road,Boulders,Cliff,Inland Water,Marsh,Quarry Or Pit,Scree,Rock,Mud,Sand,Shingle
  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  (Mill lane-BUXTON)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc       | azimuth | direction | time  | distance |
      | 4             | 53.252061,-1.826618 | continue onto Path | 97.0    | E         | 24386 | 33.87    |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.267104,-1.818304 | 53.131858,-1.661941 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  (Mill lane-BUXTON)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc       | azimuth | direction | time  | distance |
      | 4             | 53.252061,-1.826618 | continue onto Path | 97.0    | E         | 24386 | 33.87    |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.267104,-1.818304 | 53.131858,-1.661941 | foot      |  scree    |
