Feature: Verify a map tile is requested and displayed on the UI
    As a user
    I want to get a maptile from geowebcache
    And I want to load the map using the tiles on UI

 
  Scenario Outline: Verify  Panning on a map
    Given I open the mapping appliaction
    When I zoom into the layer "<zoomlayer>"
    And I pan to the "<direction>" "<panningIndex>" times
    Then I should see appropriate map "<ComparisionImage>" loaded "<testID>"

    Examples: 
      | testID     | direction | ComparisionImage | panningIndex | zoomlayer |
      | MAPI_5_001 | Left      | MAPI_5_001_LEFT.png  | 1            | 4         |
      | MAPI_5_002 | Right     | MAPI_5_002_RIGHT.png | 2            | 5         |
      | MAPI_5_003 | Up        | MAPI_5_003_UP.png    | 3            | 6         |
      | MAPI_5_004 | Down      | MAPI_5_004_DOWN.png  | 5            | 7         |

  @Mapping
  Scenario Outline: Verify  ZoomIn on a map
    Given I open the mapping appliaction
    And I pan to the "DOWN" "2" times
    And I pan to the "RIGHT" "1" times
    When I zoom into the layer "5"
    And I pan to the "LEFT" "2" times
    And I pan to the "DOWN" "2" times
    When I zoom into the layer "<zoomlayer>"
    Then I should see appropriate map "<ComparisionImage>" loaded "<testID>"
    
    Examples:
          | testID     | ComparisionImage | zoomlayer | direction |
      | MAPI_6_001 | MAPI_6_001_Layer_7.png | 2         | Left      |


  Scenario Outline: Verify  ZoomOut on a map
    Given I open the mapping appliaction
    When I zoom out the layer "<zoomlayer>"
    And I pan to the "<direction>" "<panningIndex>" times
    Then I should see appropriate map "<ComparisionImage>" loaded "<testID>"

    Examples: 
      | testID     | ComparisionImage | zoomlayer | direction |
      | MAPI_5_001 | Map_panLeft.png  | 5         | Left      |
      | MAPI_5_002 | Map_panRight.png | 8         | Right     |
      | MAPI_5_003 | Map_panUp.png    | 10        | Up        |
      | MAPI_5_004 | Map_panDown.png  | 13        | Down      |
