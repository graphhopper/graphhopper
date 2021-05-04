# How to create new routing profile aka a new FlagEncoder?

Copy e.g. a simple FlagEncoder like CarFlagEncoder or extend from it. Then change the toString method to a 
different desired name.

Make sure that it supports the required speed resolution via calling the appropriate (super) constructor. 
E.g. speedBits means how many bits should reserved for the speed information, 
the speedFactor means by which factor the speed should be divided before storing 
(e.g. 5 for car and 1 for bikes for more precision).

As a third step you need to tune the speeds for the different road types and surfaces. Maybe
now it is time to write a first test for your new FlagEncoder.

Use it e.g. just via `graphHopper.setEncodingManager(new EncodingManager(myEncoder));`

## Different forward and backward weights?

With 0.12 this is now simple. Specify speedTwoDirections = true in the constructor and overwrite handleSpeed:

```java
protected void handleSpeed(IntsRef edgeFlags, ReaderWay way, double speed) {
        speedEncoder.setDecimal(true, edgeFlags, speed);
        super.handleSpeed(edgeFlags, way, speed);
}
```

See Bike2WeightFlagEncoder for an example that uses different weights: slower speeds uphill than downhill.

## Elevation

To incorporate or precalculate values based on the elevation data you can hook into applyWayTags
and call edge.fetchWayGeometry(FetchMode.ALL) or again, see Bike2WeightFlagEncoder.

## Add to the core

If you want to include your FlagEncoder in GraphHopper and e.g. still want to use the config.yml
you can use a subclass of DefaultFlagEncoderFactory, set it to to the GraphHopper instance
and use the configuration object to change different properties.
