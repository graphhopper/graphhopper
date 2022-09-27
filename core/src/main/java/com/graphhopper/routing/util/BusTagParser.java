package com.graphhopper.routing.util;

import com.graphhopper.routing.ev.*;
import com.graphhopper.util.PMap;

public class BusTagParser extends CarTagParser {
    public BusTagParser(EncodedValueLookup lookup, PMap properties) {
        this(
                lookup.getBooleanEncodedValue(VehicleAccess.key("bus")),
                lookup.getDecimalEncodedValue(VehicleSpeed.key("bus")),
                lookup.hasEncodedValue(TurnCost.key("bus")) ? lookup.getDecimalEncodedValue(TurnCost.key("bus")) : null,
                lookup.getBooleanEncodedValue(Roundabout.KEY),
                new PMap(properties).putObject("name", "bus"),
                TransportationMode.BUS
        );
    }

    public BusTagParser(BooleanEncodedValue accessEnc, DecimalEncodedValue speedEnc, DecimalEncodedValue turnCostEnc,
                        BooleanEncodedValue roundaboutEnc, PMap properties, TransportationMode transportationMode) {
        super(accessEnc, speedEnc, turnCostEnc, roundaboutEnc, new PMap(properties).putObject("name", "bus"), transportationMode, speedEnc.getNextStorableValue(60));

        restrictedValues.remove("no");
        restrictedValues.remove("private");

        barriers.remove("bus_trap");
        barriers.remove("sump_buster");
    }
}
