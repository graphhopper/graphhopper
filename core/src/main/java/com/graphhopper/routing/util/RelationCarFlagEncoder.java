package com.graphhopper.routing.util;

import java.util.ArrayList;
import java.util.Arrays;

import com.graphhopper.reader.Relation;
import com.graphhopper.util.Helper;

public class RelationCarFlagEncoder extends CarFlagEncoder {

	private EncodedValue relationCodeEncoder;

	protected RelationCarFlagEncoder()
	    {
	        this(5, 5);
	    }

	
	
	protected RelationCarFlagEncoder(int speedBits, double speedFactor )    {
        super(speedBits, speedFactor);
        restrictions = new ArrayList<String>(Arrays.asList("motorcar", "motor_vehicle", "vehicle", "access"));
        restrictedValues.add("private");
        restrictedValues.add("agricultural");
        restrictedValues.add("forestry");
        restrictedValues.add("no");
        restrictedValues.add("restricted");
        restrictedValues.add("delivery");

        intendedValues.add("yes");
        intendedValues.add("permissive");

        potentialBarriers.add("gate");
        potentialBarriers.add("lift_gate");
        potentialBarriers.add("kissing_gate");
        potentialBarriers.add("swing_gate");

        absoluteBarriers.add("bollard");
        absoluteBarriers.add("stile");
        absoluteBarriers.add("turnstile");
        absoluteBarriers.add("cycle_barrier");
        absoluteBarriers.add("block");

        trackTypeSpeedMap.put("grade1", 20); // paved
        trackTypeSpeedMap.put("grade2", 15); // now unpaved - gravel mixed with ...
        trackTypeSpeedMap.put("grade3", 10); // ... hard and soft materials
        trackTypeSpeedMap.put("grade4", 5); // ... some hard or compressed materials
        trackTypeSpeedMap.put("grade5", 5); // ... no hard materials. soil/sand/grass

        badSurfaceSpeedMap.add("cobblestone");
        badSurfaceSpeedMap.add("grass_paver");
        badSurfaceSpeedMap.add("gravel");
        badSurfaceSpeedMap.add("sand");
        badSurfaceSpeedMap.add("paving_stones");
        badSurfaceSpeedMap.add("dirt");
        badSurfaceSpeedMap.add("ground");
        badSurfaceSpeedMap.add("grass");

        // autobahn
        defaultSpeedMap.put("motorway", 100);
        defaultSpeedMap.put("motorway_link", 70);
        
        defaultSpeedMap.put("Motorway", 35);
        defaultSpeedMap.put("A Road", 55);
        defaultSpeedMap.put("B Road", 35);
        defaultSpeedMap.put("Minor Road", 35);
        defaultSpeedMap.put("Local Street", 35);
        defaultSpeedMap.put("Alley", 35);
        // bundesstra��e
        defaultSpeedMap.put("trunk", 70);
        defaultSpeedMap.put("trunk_link", 65);
        // linking bigger town
        defaultSpeedMap.put("primary", 65);
        defaultSpeedMap.put("primary_link", 60);
        // linking towns + villages
        defaultSpeedMap.put("secondary", 60);
        defaultSpeedMap.put("secondary_link", 50);
        // streets without middle line separation
        defaultSpeedMap.put("tertiary", 50);
        defaultSpeedMap.put("tertiary_link", 40);
        defaultSpeedMap.put("unclassified", 30);
        defaultSpeedMap.put("residential", 30);
        // spielstra��e
        defaultSpeedMap.put("living_street", 5);
        defaultSpeedMap.put("service", 20);
        // unknown road
        defaultSpeedMap.put("road", 20);
        // forestry stuff
        defaultSpeedMap.put("track", 15);
    }
	
	

	@Override
	public long handleRelationTags(Relation relation, long oldRelationFlags) {
		oldRelationFlags = super.handleRelationTags(relation, oldRelationFlags);

//		System.err.println(relation.getTag("oneway"));
		boolean isRoundabout = relation.hasTag("junction", "roundabout");
		long code = 0;
//		if(relation.hasTag("highway")) {
//			String roadTag = relation.getTag("highway");
//			// get assumed speed from highway type
//            double speed = getSpeed(relation);
//            double maxSpeed = getMaxSpeed(relation);
//            if (maxSpeed > 0)
//                // apply maxSpeed which can mean increase or decrease
//                speed = maxSpeed * 0.9;
//
//            // limit speed to max 30 km/h if bad surface
//            if (speed > 30 && relation.hasTag("surface", badSurfaceSpeedMap))
//                speed = 30;
//
//            System.err.println("ROAD REL:" + roadTag + ":" + speed);
//            code = setSpeed(0, speed);
//		}
		
		if (isRoundabout)
			code = setBool(code, K_ROUNDABOUT, true);
		if (relation.hasTag("oneway", oneways) || isRoundabout) {
//			System.err.println("ONE WAY:" + relation.getTag("oneway"));
			if (relation.hasTag("oneway", "-1"))
				code |= backwardBit;
			else
				code |= forwardBit;
		}

		int oldCode = (int) relationCodeEncoder.getValue(oldRelationFlags);
		if (oldCode < code)
			return relationCodeEncoder.setValue(0, code);
		return oldRelationFlags;
	}
	
	protected double getSpeed( Relation way )
    {
        String highwayValue = way.getTag("highway");
        Integer speed = defaultSpeedMap.get(highwayValue);
        if (speed == null)
            throw new IllegalStateException("car, no speed found for:" + highwayValue);

        if (highwayValue.equals("track"))
        {
            String tt = way.getTag("tracktype");
            if (!Helper.isEmpty(tt))
            {
                Integer tInt = trackTypeSpeedMap.get(tt);
                if (tInt != null)
                    speed = tInt;
            }
        }

        return speed;
    }
	
	protected double getMaxSpeed( Relation way )
    {
        double maxSpeed = parseSpeed(way.getTag("maxspeed"));
        double fwdSpeed = parseSpeed(way.getTag("maxspeed:forward"));
        if (fwdSpeed >= 0 && (maxSpeed < 0 || fwdSpeed < maxSpeed))
            maxSpeed = fwdSpeed;

        double backSpeed = parseSpeed(way.getTag("maxspeed:backward"));
        if (backSpeed >= 0 && (maxSpeed < 0 || backSpeed < maxSpeed))
            maxSpeed = backSpeed;

        return maxSpeed;
    }

	@Override
	public int defineRelationBits(int index, int shift) {
		relationCodeEncoder = new EncodedValue("RelationCode", shift, 8, 1, 0,
				160);
		return shift + relationCodeEncoder.getBits();
	}
}
