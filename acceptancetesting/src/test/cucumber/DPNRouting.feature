Feature: Verify a route from A to B
    As a user
    I want to get a route from location A to location B by Foot using the routing service
    And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  (Mill lane-BUXTON)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco        | waypointdesc            | azimuth | direction | time  | distance | avoidance |
      | 4             | 53.1356,-1.820891 | Continue onto Mill Lane | 70.0    | E         | 23171 | 32.2     |           |

    Examples: 
      | pointA              | pointB             | routetype | avoidance |
      | 53.176062,-1.871472 | 53.154773,-1.77272 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  (Chatswoth Park)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                  | azimuth | direction | time   | distance | avoidance |
      | 2             | 53.221055,-1.623152 | Turn right onto B Road        | 157.0   | SE        | 984273 | 1367.1   |           |
      | 5             | 53.197269,-1.608797 | Continue onto Chatsworth Road | 181.0   | S         | 678871 | 942.9    |           |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.211013,-1.619393 | 53.185757,-1.611969 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Musden Low)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc          | azimuth | direction | time  | distance | avoidance |
      | 3             | 53.042479,-1.820522 | Turn right onto Route | 297.0   | NW        | 35181 | 48.9     |           |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.049589,-1.823866 | 53.076372,-1.853379 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (A54)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc         | azimuth | direction | time   | distance | avoidance |
      | 4             | 53.176842,-2.069334 | Turn left onto Track | 255.0   | W         | 187602 | 260.6    |           |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.173064,-2.060321 | 53.214387,-2.017271 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Townhead )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc                | azimuth | direction | time  | distance | avoidance |
      | 5             | 53.11862,-1.909506 | Turn slight left onto Route | 169.0   | S         | 51007 | 70.8     |           |

    Examples: 
      | pointA              | pointB             | routetype | avoidance |
      | 53.122676,-1.909914 | 53.088159,-1.87142 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Martin's Low)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc          | azimuth | direction | time  | distance | avoidance |
      | 3             | 53.066198,-1.905401 | Turn right onto Track | 105.0   | E         | 38678 | 53.7     |           |

    Examples: 
      | pointA             | pointB              | routetype | avoidance |
      | 53.06535,-1.906169 | 53.100994,-1.956274 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Castleton Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                  | azimuth | direction | time   | distance | avoidance |
      | 2             | 53.347406,-1.760973 | Turn left onto Castleton Road | 109.0   | E         | 878424 | 1220.0   | ARoad     |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.348832,-1.761122 | 53.197338,-1.594157 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Hernstone Lane )
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                 | azimuth | direction | time   | distance | avoidance |
      | 3             | 53.305821,-1.814508 | Continue onto Hernstone Lane | 299.0   | NW        | 304923 | 423.5    | ARoad     |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.300714,-1.786126 | 53.287803,-1.816746 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Monyash Road)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc               | azimuth | direction | time   | distance | avoidance |
      | 5             | 53.20882,-1.688212 | Continue onto Monyash Road | 55.0    | NE        | 482979 | 670.8    |           |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.194909,-1.710481 | 53.156696,-1.634947 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route (Whitfield lane)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc               | azimuth | direction | time   | distance | avoidance |
      | 4             | 53.143286,-1.647841 | Turn right onto Elton Road | 282.0   | W         | 195384 | 271.4    |           |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.142876,-1.642599 | 53.163897,-1.714249 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  (Cardlemere Lane)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                   | azimuth | direction | time   | distance | avoidance |
      | 4             | 53.129383,-1.754591 | Turn left onto Cardlemere Lane | 121.0   | SE        | 594909 | 826.3    |           |

    Examples: 
      | pointA              | pointB             | routetype | avoidance |
      | 53.114295,-1.762789 | 53.086961,-1.69626 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route using one intermediate point ( Old Coalpit Lane)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>" via "<pointC>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time   | distance | avoidance |
      | 2             | 53.23952,-1.803512  | Turn left onto Sough Lane       | 189.0   | S         | 452948 | 629.1    |           |
      | 29            | 53.140548,-1.810174 | Turn slight left onto Mill Lane | 216.0   | SW        | 49046  | 68.1     |           |
      | 34            | 53.129146,-1.866738 | Turn left onto Cheadle Road     | 179.0   | S         | 171195 | 237.8    |           |

    Examples: 
      | pointA              | pointB            | pointC              | routetype | avoidance |
      | 53.238625,-1.794511 | 53.1651,-1.776435 | 53.125221,-1.871205 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route using one intermediate point ( Newhouses Farm)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>" via "<pointC>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                          | azimuth | direction | time    | distance | avoidance |
      | 7             | 53.305394,-1.819253 | Turn slight right onto Hernstone Lane | 66.0    | NE        | 304923  | 423.5    | ARoad     |
      | 14            | 53.25475,-1.727239  | Continue onto Castlegate Lane         | 183.0   | S         | 1156073 | 1605.7   |           |

    Examples: 
      | pointA              | pointB             | pointC              | routetype | avoidance |
      | 53.303058,-1.836061 | 53.28261,-1.761964 | 53.233207,-1.633878 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route using one intermediate point ( Bakewell)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>" via "<pointC>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                   | azimuth | direction | time    | distance | avoidance |
      | 6             | 53.145466,-1.778242 | Continue onto Tissington Trail | 17.0    | N         | 2006616 | 2787.0   |           |
      | 15            | 53.195118,-1.761669 | Continue onto Church Street    | 38.0    | NE        | 897815  | 1247.0   |           |
      | 19            | 53.20882,-1.688212  | Continue onto Monyash Road     | 55.0    | NE        | 369935  | 513.8    |           |

    Examples: 
      | pointA              | pointB              | pointC              | routetype | avoidance |
      | 53.138247,-1.752507 | 53.195653,-1.762655 | 53.211574,-1.682278 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  using 2 intermediate waypoints (Mill Lane)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>" via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time   | distance | avoidance |
      | 5             | 53.140548,-1.810174 | Turn slight left onto Mill Lane | 216.0   | SW        | 49046  | 68.1     |           |
      | 12            | 53.131356,-1.852045 | Turn slight right onto Path     | 317.0   | NW        | 721024 | 1001.4   |           |
      | 16            | 53.181282,-1.869038 | Turn left onto Market Place     | 315.0   | NW        | 791    | 1.1      |           |

    Examples: 
      | pointA              | pointB              | pointC             | pointD              | routetype | avoidance |
      | 53.139805,-1.803217 | 53.133646,-1.826223 | 53.14993,-1.868096 | 53.181298,-1.869034 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  using 2 intermediate waypoints (Tag Lane)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>" via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time    | distance | avoidance |
      | 7             | 53.227765,-1.848174 | Turn left onto Old Coalpit Lane | 58.0    | NE        | 908755  | 1262.2   | ARoad     |
      | 18            | 53.244806,-1.809527 | Continue onto Blackwell Dale    | 48.0    | NE        | 1027560 | 1427.2   |           |
      | 26            | 53.281579,-1.765467 | Continue onto Whitecross Road   | 59.0    | NE        | 210216  | 292.0    |           |
      | 32            | 53.224822,-1.70717  | Turn left onto Hall End Lane    | 59.0    | NE        | 80272   | 111.5    |           |

    Examples: 
      | pointA              | pointB              | pointC              | pointD              | routetype | avoidance |
      | 53.190346,-1.802704 | 53.239419,-1.818421 | 53.280601,-1.764495 | 53.233207,-1.633878 | foot      |           |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  using 2 intermediate waypoints (Dowlow Farm)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>" via "<pointC>" and "<pointD>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                  | azimuth | direction | time   | distance | avoidance |
      | 2             | 53.206014,-1.83483  | Turn right onto Midshires Way | 191.0   | S         | 265210 | 368.3    |           |
      | 19            | 53.202937,-1.870926 | Continue onto Glutton Dale    | 254.0   | W         | 279743 | 388.5    |           |
      | 28            | 53.124725,-1.870683 | Turn right onto Cheadle Road  | 243.0   | SW        | 121072 | 168.2    |           |

    Examples: 
      | pointA              | pointB              | pointC              | pointD             | routetype | avoidance |
      | 53.206965,-1.839021 | 53.203607,-1.857557 | 53.149631,-1.867364 | 53.11417,-1.895082 | foot      |           |

  # Avoidances : A Road,Boulders,Cliff,Inland Water,Marsh,Quarry Or Pit,Scree,Rock,Mud,Sand,Shingle
  #scree
  @Routing
  Scenario Outline: Verify DPN Route without Scree avoidance -(scree)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc       | azimuth | direction | time  | distance | avoidance |
      | 15            | 53.252061,-1.826618 | Continue onto Path | 97.0    | E         | 24386 | 33.9     | Scree     |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.267104,-1.818304 | 53.131858,-1.661941 | foot      |           |

  @Routing
  Scenario Outline: Verify DPN Route with Scree avoidance -(scree)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc       | azimuth | direction | time  | distance | avoidance |
      | 15            | 53.252061,-1.826618 | Continue onto Path | 97.0    | E         | 24386 | 33.9     | Scree     |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.267104,-1.818304 | 53.131858,-1.661941 | foot      | Scree     |

  #cliff
  @Routing
  Scenario Outline: Verify DPN Route without cliff avoidance -(cliff)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc        | azimuth | direction | time   | distance | avoidance |
      | 4             | 53.312731,-1.627617 | Continue onto Route | 164.0   | S         | 264332 | 367.1    | Cliff     |

    Examples: 
      | pointA             | pointB              | routetype | avoidance |
      | 53.31676,-1.631903 | 53.156465,-1.908797 | foot      |           |

  @Routing
  Scenario Outline: Verify DPN Route with cliff avoidance -(cliff)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc        | azimuth | direction | time   | distance | avoidance |
      | 4             | 53.312731,-1.627617 | Continue onto Route | 164.0   | S         | 264332 | 367.1    | Cliff     |

    Examples: 
      | pointA             | pointB              | routetype | avoidance |
      | 53.31676,-1.631903 | 53.156465,-1.908797 | foot      | Cliff     |

  @Routing
  Scenario Outline: Verify DPN Route with cliff avoidance -(cliff)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc        | azimuth | direction | time | distance | avoidance |
      | 3             | 53.547029,-1.979038 | Continue onto Route | 191.0   | S         | 3920 | 5.4      | Cliff     |
      | 5             | 53.546893,-1.979082 | Continue onto Route | 180.0   | S         | 5085 | 7.1      | Cliff     |
      | 7             | 53.542735,-1.981237 | Continue onto Route | 185.0   | S         | 9321 | 12.9     | Cliff     |

    Examples: 
      | pointA            | pointB              | routetype | avoidance |
      | 53.5534,-1.983177 | 53.540061,-1.978324 | foot      |           |

  @Routing
  Scenario Outline: Verify DPN Route with cliff avoidance -(cliff)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc        | azimuth | direction | time | distance | avoidance |
      | 3             | 53.547029,-1.979038 | Continue onto Route | 191.0   | S         | 3920 | 5.4      | Cliff     |
      | 5             | 53.546893,-1.979082 | Continue onto Route | 180.0   | S         | 5085 | 7.1      | Cliff     |
      | 7             | 53.542735,-1.981237 | Continue onto Route | 185.0   | S         | 9321 | 12.9     | Cliff     |

    Examples: 
      | pointA            | pointB              | routetype | avoidance |
      | 53.5534,-1.983177 | 53.540061,-1.978324 | foot      | Cliff     |

  #boulders
  @Routing
  Scenario Outline: Verify DPN Route without boulders avoidance -(boulders)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc        | azimuth | direction | time  | distance | avoidance |
      | 3             | 53.309004,-1.627564 | Turn left onto Path | 98.0    | E         | 99563 | 138.3    | Boulders  |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.311217,-1.629849 | 53.156465,-1.908797 | foot      |           |

  @Routing
  Scenario Outline: Verify DPN Route with boulders avoidance -(boulders)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc        | azimuth | direction | time  | distance | avoidance |
      | 3             | 53.309004,-1.627564 | Turn left onto Path | 98.0    | E         | 99563 | 138.3    | Boulders  |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.311217,-1.629849 | 53.156465,-1.908797 | foot      | Boulders  |
      
      
      
      
  #Multiple Avoidance
  @Routing @Avoidance
  Scenario Outline: Verify DPN Route without boulders avoidance -(boulders)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc        | azimuth | direction | time  | distance | avoidance |
      | 6             | 53.545217,-1.986871 | Continue onto Route | 106.0    | E         | 1660 | 2.3    | Cliff  |
      | 9             | 53.545038,-1.986338 | Continue onto Route | 130.0    | SE         | 178328 |247.7    | Boulders  |
 
 Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.5534,-1.983177|53.490733,-1.977715 | foot      |           |

  @Routing @Avoidance
  Scenario Outline: Verify DPN Route with boulders avoidance -(boulders)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI and avoid "<avoidance>"
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc        | azimuth | direction | time  | distance | avoidance |
      | 6             | 53.545217,-1.986871 | Continue onto Route | 106.0    | E         | 1660 | 2.3    | Cliff  |
      | 9             | 53.545038,-1.986338 | Continue onto Route | 130.0    | SE         | 178328 |247.7    | Boulders  |

    Examples: 
      | pointA              | pointB              | routetype | avoidance |
      | 53.5534,-1.983177|53.490733,-1.977715 | foot      | Boulders,Cliff  |
