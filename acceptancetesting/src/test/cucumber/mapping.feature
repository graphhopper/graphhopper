Feature: Verify a map tile is requested and displayed on the UI
    As a user
    I want to get a maptile from geowebcache
    And I want to load the map using the tiles on UI

  @Mapping
  Scenario Outline: Verify  Panning on a map
    Given I open the mapping appliaction
    When I pan to the "<direction>" "<panningIndex>" times
    Then I should see appropriate map "<ComparisionImage>" loaded "<testID>"

    Examples: 
      | testID     | direction | ComparisionImage | panningIndex |
      | MAPI_5_001 | Left      | Map_panLeft.png  | 1            |
      | MAPI_5_002 | Right     | Map_panRight.png | 1            |
      | MAPI_5_003 | Up        | Map_panUp.png    | 1            |
      | MAPI_5_004 | Down      | Map_panDown.png  | 5            |

  @Mapping
  Scenario Outline: Verify  ZoomIn on a map
    Given I open the mapping appliaction
    When I zoom into the layer "<zoomlayer>"
    Then I should see appropriate map "<ComparisionImage>" loaded "<testID>"

    Examples: 
      | testID     | ComparisionImage | zoomlayer |
      | MAPI_5_001 | Map_panLeft.png  | 5         |
      | MAPI_5_002 | Map_panRight.png | 8         |
      | MAPI_5_003 | Map_panUp.png    | 10        |
      | MAPI_5_004 | Map_panDown.png  | 13        |

  @Mapping
  Scenario Outline: Verify  ZoomOut on a map
    Given I open the mapping appliaction
    When I zoom out the layer "<zoomlayer>"
    When I pan to the "<direction>" "<panningIndex>" times
    Then I should see appropriate map "<ComparisionImage>" loaded "<testID>"

    Examples: 
      | testID     | ComparisionImage | zoomlayer |
      | MAPI_5_001 | Map_panLeft.png  | 5         |
      | MAPI_5_002 | Map_panRight.png | 8         |
      | MAPI_5_003 | Map_panUp.png    | 10        |
      | MAPI_5_004 | Map_panDown.png  | 13        |
