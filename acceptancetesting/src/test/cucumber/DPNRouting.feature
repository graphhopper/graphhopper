Feature: Verify a route from A to B
    As a user
    I want to get a route from location A to location B by Foot using the routing service
    And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  (Mill lane-BUXTON)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco        | waypointdesc            | azimuth | direction | time  | distance |
      | 4             | 53.1356,-1.820891 | continue onto Mill Lane | 78      | E         | 23165 | 32.175   |

    Examples: 
      | pointA              | pointB             | routetype |
      | 53.176062,-1.871472 | 53.154773,-1.77272 | foot      |

  @Routing
  Scenario Outline: Verify  Road Names on a Walking Route  (Mill lane-BUXTON)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                          | azimuth | direction | time   | distance |
      | 5             | 53.197269,-1.608797 | continue onto Chatsworth Road         | 181     | S         | 670049 | 930.629  |
      | 6             | 53.189535,-1.613492 | turn slight left onto Dale Road North | 141     | SE        | 984236 | 1366.997 |

    Examples: 
      | pointA              | pointB             | routetype |
      |53.211013,-1.619393|53.185757,-1.611969 | foot      |

  @Routing
  Scenario Outline: Verify  Gate  Restrictions on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc          | azimuth | direction | time  | distance |
      | 3             | 53.042479,-1.820522 | Turn right onto Route | 249     | W         | 25753 | 35.769   |

    Examples: 
      | pointA              | pointB              | routetype |
      | 53.049589,-1.823866 | 53.076372,-1.853379 | foot      |

  @Routing
  Scenario Outline: Verify  Gate  Restrictions on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                | azimuth | direction | time   | distance |
      | 4             | 53.176842,-2.069334 | Turn slight left onto Track | 268     | W         | 171340 | 237.973  |

    Examples: 
      | pointA              | pointB              | routetype |
      | 53.173064,-2.060321 | 53.214387,-2.017271 | foot      |

  @Routing
  Scenario Outline: Verify  Gate  Restrictions on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc         | azimuth | direction | time  | distance |
      | 5             | 53.11862,-1.909506 | Turn left onto Route | 139     | SE        | 40897 | 56.802   |

    Examples: 
      | pointA              | pointB             | routetype |
      | 53.122676,-1.909914 | 53.088159,-1.87142 | foot      |

  @Routing
  Scenario Outline: Verify  Gate  Restrictions on a Route
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc          | azimuth | direction | time  | distance |
      | 3             | 53.066198,-1.905401 | Turn right onto Track | 98      | E         | 38673 | 53.713   |

    Examples: 
      | pointA             | pointB              | routetype |
      | 53.06535,-1.906169 | 53.100994,-1.956274 | foot      |

  @Routing
  Scenario Outline: 
    Given I request a route between "<pointA>" and "<pointB>" as a "<routetype>" from RoutingAPI
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                  | azimuth | direction | time   | distance |
      | 2             | 53.347406,-1.760973 | Turn left onto Castleton Road | 86      | E         | 855317 | 1187.949 |

    Examples: 
      | pointA              | pointB              | routetype |
      | 53.348832,-1.761122 | 53.197338,-1.594157 | foot      |
