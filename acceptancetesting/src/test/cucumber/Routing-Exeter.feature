Feature: Verify a route from A to B
    As a user
    I want to get a route from location A to location B using the routing service
    And route should be the fastest route and contain the waypoints,restrictions,time and other instructions

  # One Way Restrictions
  @Routing
  Scenario Outline: Verify  one Way  Restrictions  on a Route (EX-Bridge South - Exteter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                      | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.719156,-3.537811 | Continue onto A3015 (FROG STREET) | 41.0    | NE        | 13259 | 221.8    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.717615,-3.536538 | 50.719106,-3.535359 | car       ||

  # Same route but different waypointdesc
  @Routing
  Scenario Outline: Verify  one Way  Restrictions on a Route (Cleveladn Street-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc               | azimuth | direction | time | distance |avoidance|
      | 2             | 50.717806,-3.54264 | Turn left onto BULLER ROAD | 137.0   | SE        | 4467 | 55.8     ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.717951,-3.542331 | 50.718613,-3.539589 | car       ||

  @Routing
  Scenario Outline: Verify  one Way  Restrictions on a Route (Cleveladn Street-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time  | distance |avoidance|
      | 4             | 50.718462,-3.541302 | Turn left onto CLEVELAND STREET | 232.0   | SW        | 9534 | 119.2    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.718282,-3.538437 | 50.717687,-3.541511 | car       ||

  @Routing
  Scenario Outline: Verify  one Way  Restrictions (Except Buses) on a Route (SIDWELL STREET-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc                    | azimuth | direction | time | distance |avoidance|
      | 4             | 50.726689,-3.52712 | Turn left onto LONGBROOK STREET | 190.0   | S         | 6267 | 78.3     ||

    Examples: 
      | pointA              | pointB               | routeOptions |avoidances|
      | 50.727949,-3.523498 | 50.726428,-3.5251291 | car       ||

  @Routing
  Scenario Outline: Verify  oneway Restrictions on a Route (Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco       |
      | 50.71958,-3.534089 |

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.720492,-3.535221 | 50.718641,-3.53476 | car       ||

  @KnownIssues
  Scenario Outline: Verify  one Way  Restrictions  on a Route (Exeter WSPIP-98)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time | distance |avoidance|
      | 7             | 50.722198,-3.526704 | Turn left onto SOUTHERNHAY EAST | 32      | NE        | 5838 | 56.761   ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.720454,-3.530089 | 50.722657,-3.526321 | car       ||

  # No Entry Restrictions
  @Routing
  Scenario Outline: Verify  No Entry  Restrictions on a Route (High Street(London Inn Square)-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc                                 | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.725549,-3.52693 | Turn slight left onto NEW NORTH ROAD (B3183) | 285.0   | W         | 57545 | 729.1    ||

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.725425,-3.526925 | 50.72442,-3.532756 | car       ||

  @Routing
  Scenario Outline: Verify  No Entry  Restrictions on a Route (CHEEK STREET-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                     | azimuth | direction | time | distance |avoidance|
      | 2             | 50.727244,-3.522476 | Turn left onto SUMMERLAND STREET | 313.0   | NW        | 5223 | 65.3     ||

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.726234,-3.524072 | 50.727186,-3.52392 | car       ||

  @Routing
  Scenario Outline: Verify  No Entry(Except for Buses and Taxis)  Restrictions on a Route (Sidwell Street-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc                           | azimuth | direction | time  | distance |avoidance|
      | 4             | 50.726418,-3.52381 | Turn slight left onto BAMPFYLDE STREET | 45.0    | NE        | 10510 | 131.4    ||

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.726529,-3.524928 | 50.727002,-3.52419 | car       ||

  # No Turns Restrictions and Roundabout
  @Routing
  Scenario Outline: Verify  No Turn  Restrictions on a Route (Western Way-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                   | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.728509,-3.520647 | At roundabout, take exit 1 onto SIDWELL STREET | 282.0   | W         | 16437 | 212.5    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.726735,-3.520955 | 50.726914,-3.522033 | car       ||

  @Routing
  Scenario Outline: Verify  No Turn Restriction (Denmark Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                  | azimuth | direction | time  | distance |avoidance|
      | 3             | 50.725002,-3.520632 | Turn left onto RUSSELL STREET | 303.0   | NW        | 19909 | 248.9    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.724901,-3.521588 | 50.724524,-3.520923 | car       ||

  @Routing
  Scenario Outline: Verify  Turn Restrictions  on a Route (Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco        |
      | 50.721201,-3.532498 |

    Examples: 
      | pointA             | pointB             | routeOptions |avoidances|
      | 50.72148,-3.532485 | 50.721888,-3.53182 | car       ||
      
# The below issue is a data issue and been reported into jira as route-67
  @KnownIssues
  Scenario Outline: Verify No  Turn Restrictions(Except Bus)  on a Route (BELGROVE ROAD -Exeter ROUTE-67)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco         | waypointdesc                 | azimuth | direction | time | distance |avoidance|
      | 2             | 50.725997,-3.52296 | Turn left onto CHEEKE STREET | 135     | SE        | 5639 | 56.915   ||

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.726085,-3.522837 | 50.725076,-3.52442 | car       ||

  # Mandatory Turn Restrictions
  @Routing
  Scenario Outline: Verify  Mandatory Turn(with exceptions) at Exeter area
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                | azimuth | direction | time | distance |avoidance|
      | 2             | 50.726462,-3.523882 | Continue onto CHEEKE STREET | 133.0   | SE        | 564  | 7.1      ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.726823,-3.524432 | 50.725423,-3.526813 | car       ||

  @Routing
  Scenario Outline: Verify  Mandatory Turn at Exeter area(DENMARK ROAD)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                  | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.725002,-3.520632 | Turn left onto RUSSELL STREET | 303.0   | NW        | 19909 | 248.9    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.724777,-3.520811 | 50.724394,-3.520953 | car       ||

  @Routing @KnownIssues
  Scenario Outline: Verify  Mandatory Turn at Exeter area(COLLEGE ROAD)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc                | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.72133,-3.519451 | Turn right onto SPICER ROAD | 278     | W         | 41233 | 400.903  ||

    Examples: 
      | pointA             | pointB              | routeOptions |avoidances|
      | 50.723597,-3.51776 | 50.723773,-3.517251 | car       ||

  @Routing
  Scenario Outline: Verify  Mandatory Turn Restriction (Denmark Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints not on the route map:
      | wayPointIndex | waypointco          | waypointdesc                           | azimuth | direction | time | distance |avoidance|
      | 2             | 50.724703,-3.520835 | Turn right onto HEAVITREE ROAD (B3183) | 293.0   | NW        | 1118 | 10.9     ||

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.724378,-3.520993 | 50.72413,-3.518874 | car       ||

  # Access Limited To
  @Routing
  Scenario Outline: Verify  Access Limited To  Restrictions on a Route (North Street-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco       | waypointdesc               | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.72258,-3.5326 | Continue onto SOUTH STREET | 135.0   | SE        | 15537 | 194.2    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.722996,-3.533354 | 50.726428,-3.525129 | car       ||

  @Routing
  Scenario Outline: Verify  Access Limited To  Restrictions on a Route (Paris Street-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc                           | azimuth | direction | time  | distance |avoidance|
      | 5             | 50.726418,-3.52381 | Turn slight left onto BAMPFYLDE STREET | 45.0    | NE        | 10510 | 131.4    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.724989,-3.526006 | 50.729735,-3.519862 | car       ||

  # Access Prohibited To
  @Routing
  Scenario Outline: Verify  Access Prohibited To  Restrictions on a Route (Iron Bridge Street-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc                   | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.724661,-3.53639 | Turn left onto ST DAVID'S HILL | 310.0   | NW        | 35154 | 439.4    ||

    Examples: 
      | pointA             | pointB              | routeOptions |avoidances|
      | 50.72458,-3.536493 | 50.723442,-3.534131 | car       ||

  @Routing @KnownIssues
  Scenario Outline: Verify  Access Prohibited To  Restrictions on a Route (Upper Paul Street-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.724819,-3.532223 | Turn left onto QUEEN STREET | 324.0   | NW        | 37994 | 369.4    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.724614,-3.532555 | 50.724639,-3.530457 | car       ||

  # Ford
  @Routing
  Scenario Outline: Verify  Ford  Restrictions on a Route (BONHAY Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time  | distance |avoidance|
      | 4             | 50.727823,-3.540036 | Turn slight left onto HELE ROAD | 85.0    | E         | 12281 | 153.5    ||

    Examples: 
      | pointA             | pointB              | routeOptions |avoidances|
      | 50.731111,-3.54277 | 50.719327,-3.538255 | car       ||

  @Routing
  Scenario Outline: Verify  Ford  Restrictions on a Route (Quadrangle Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc               | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.730716,-3.530028 | Turn left onto HORSEGUARDS | 189.0   | S         | 21893 | 273.7    ||

    Examples: 
      | pointA             | pointB              | routeOptions |avoidances|
      | 50.730861,-3.52934 | 50.731808,-3.529829 | car       ||

  # Gate
  @Routing
  Scenario Outline: Verify  Gate  Restrictions on a Route (Cathedral Close Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                     | azimuth | direction | time  | distance |avoidance|
      | 3             | 50.722198,-3.526704 | Turn right onto SOUTHERNHAY EAST | 202.0   | SW        | 20129 | 255.9    ||

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.722333,-3.527488 | 50.72243,-3.532372 | car       ||

  @Routing
  Scenario Outline: Verify  Gate  Restrictions on a Route (Lower Northen Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc        | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.722081,-3.539012 | Turn left onto A377 | 166.0   | S         | 29003 | 395.5    ||

    Examples: 
      | pointA            | pointB              | routeOptions |avoidances|
      | 50.7244,-3.535817 | 50.723705,-3.534493 | car       ||

  #Private Road
  @Routing
  Scenario Outline: Verify  a Private Road (Publicly Accessible) on a Route  (PERRY ROAD)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc                    | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.732011,-3.53798 | Turn right onto STREATHAM DRIVE | 2.0     | N         | 13195 | 166.8    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.732296,-3.535372 | 50.733538,-3.537462 | car       ||

  #Roundabout
  @Routing
  Scenario Outline: Verify a  Private Road (Publicly Accessible) on a Route (QUEEN STREET)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                           | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.727397,-3.535531 | At roundabout, take exit 3 onto NEW NORTH ROAD (B3183) | 295.0   | NW        | 14023 | 181.3    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.727003,-3.535041 | 50.727023,-3.533083 | car       ||

  @Routing
  Scenario Outline: Verify a PrivateRoad -Restricted Access(WESTERN WAY)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco         | waypointdesc                      | azimuth | direction | time | distance |avoidance|
      | 1             | 50.72593,-3.521909 | Continue onto B3212 (WESTERN WAY) | 38.0    | NE        | 3099 | 38.7     ||

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.725876,-3.521801 | 50.72619,-3.521541 | car       ||

  @Routing
  Scenario Outline: Verify a Private Road - Restricted Access (Denmark Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the trackPoints not on the route map:
      | trackPointco      |
      | 50.723966,-3.5198 |

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.724316,-3.521008 | 50.72413,-3.518874 | car       ||

  #Roundabouts
  @Routing
  Scenario Outline: Verify a roundabout(WESTERN WAY)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                   | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.728793,-3.520273 | At roundabout, take exit 2 onto SIDWELL STREET | 178.0   | S         | 19800 | 259.2    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.729277,-3.519078 | 50.728889,-3.522884 | car       ||

  @Routing
  Scenario Outline: Verify a roundabout(WESTERN WAY)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                        | azimuth | direction | time  | distance |avoidance|
      | 2             | 50.725096,-3.522378 | At roundabout, take exit 4 onto B3212 (WESTERN WAY) | 239.0   | SW        | 19384 | 255.8    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.724137,-3.518937 | 50.728366,-3.524132 | car       ||
      
     # Quickest route
  # Motorways (Victoria Street, Union Road ,Blackhall Road ,Well Street ,Devon Shire Place, Culverland Road).These roads are converted into motorways in Exeter
      
      @Routing
  Scenario Outline: Verify  a quickest route  on a Route (Springfield Road-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time | distance |avoidance|
      | 2             | 50.733764,-3.523212 | Turn right onto VICTORIA STREET | 150.0   | SE        | 7689 | 213.6    ||

    Examples: 
      | pointA             | pointB            | routeOptions |avoidances|
      | 50.733719,-3.52332 | 50.732556,-3.5211 | car       ||

  @Routing
  Scenario Outline: Verify  a quickest route  on a Route (DEVON SHIRE PLACE-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                      | azimuth | direction | time | distance |avoidance|
      | 3             | 50.733574,-3.524027 | Turn right onto DEVON SHIRE PLACE | 162.0   | S         | 8490 | 235.9    ||

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.734095,-3.524696 | 50.72809,-3.524451 | car       ||

  @Routing
  Scenario Outline: Verify  a quickest route  on a Route (BLACKALL ROAD-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                                | azimuth | direction | time  | distance |avoidance|
      | 3             | 50.727984,-3.530548 | Turn sharp left onto NEW NORTH ROAD (B3183) | 117.0   | SE        | 22108 | 276.4    ||

    Examples: 
      | pointA              | pointB             | routeOptions |avoidances|
      | 50.729887,-3.526896 | 50.726279,-3.52780 | car       ||

  @Routing
  Scenario Outline: Verify  a quickest route  on a Route (VICTORIA STREET-Exeter)
    Given I request a route between "<pointA>" and "<pointB>" as a "<routeOptions>" from RoutingAPI and avoid "<avoidances>"
    Then I should be able to verify the waypoints on the route map:
      | wayPointIndex | waypointco          | waypointdesc                    | azimuth | direction | time | distance |avoidance|
      | 2             | 50.733764,-3.523212 | Turn right onto VICTORIA STREET | 150.0   | SE        | 7689 | 213.6    ||

    Examples: 
      | pointA              | pointB              | routeOptions |avoidances|
      | 50.733648,-3.523662 | 50.732844,-3.521332 | car       ||
      
