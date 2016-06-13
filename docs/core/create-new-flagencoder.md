# How to create new routing profile aka a new FlagEncoder?

Copy e.g. a simple FlagEncoder like CarFlagEncoder or extend from it. Then change the toString method to a 
different desired name.

Make sure that it supports the required speed resolution via calling the appropriate (super) constructor. 
E.g. speedBits means how many bits should reserved for the speed information, 
the speedFactor means by which factor the speed should be devided before storing 
(e.g. 5 for car and 1 for bikes for more precision).

As a third step you need to tune the speeds for the different road types and surfaces. Maybe
now it is time to write a first test for your new FlagEncoder.

Use it e.g. just via `graphHopper.setEncodingManager(new EncodingManager(myEncoder));`

## Different forward and backward weights?

If you need to support two different speed values for one street (one edge) you need to create
a separate EncodedDoubleValue instance (reverseSpeedEncoder) managing the reverse speed, 
see Bike2WeightFlagEncoder for an example. You'll have to overwrite the following methods:

 * setReverseSpeed, getReverseSpeed to use the reverseSpeedEncoder
 * handleSpeed, to handle oneway tags correctly
 * flagsDefault 
 * setProperties
 * reverseFlags
 * setLowSpeed
 * always set reverse speed explicitely, see #665

## Elevation

To incorporate or precalculate values based on the elevation data you can hook into applyWayTags
and call edge.fetchWayGeometry(3) or again, see Bike2WeightFlagEncoder.

## Add to the core

If you want to include your FlagEncoder in GraphHopper you have to add the creation in
EncodingManager.parseEncoderString to let the EncodingManager pick the correct class when faced
with the string. The convention is that encoder.toString is identical to the string.
