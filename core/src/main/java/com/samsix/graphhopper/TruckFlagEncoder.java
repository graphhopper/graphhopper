package com.samsix.graphhopper;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.util.EdgeIteratorState;

public class TruckFlagEncoder
    extends
        CarFlagEncoder
{
    public static final int K_DESIGNATED = 100;
    public static final int K_DESTINATION = 101;
    private long designatedbit = 0;
    private long destinationbit = 0;

    public TruckFlagEncoder()
    {
        super();

        //
        // Allow our service vehicles to take private roads
        // to get to the equipment they need to get to.
        //
        restrictedValues.remove("private");
    }


    @Override
    public long acceptWay(final OSMWay way)
    {
        String hgv = way.getTag("hgv");

        //
        // hgv=no seems to be the way to say that trucks can't go here.
        // hgv = Heavy Goods Vehicle
        // Other values for hgv I have seen are...
        //     "local", "designated", "destination"
        //
        if ("no".equalsIgnoreCase(hgv))
        {
            return 0;
        }

        return super.acceptWay( way );
    }

    @Override
    public int defineWayBits( int index, int shift )
    {
        // first two bits are reserved for route handling in superclass
        shift = super.defineWayBits(index, shift);

        designatedbit = 1L << shift++;
        destinationbit = 1L << shift++;

        return shift;
    }

    private long setWayTags(long flags, final OSMWay way)
    {
        //
        // It seems that hgv = "yes" *might* be sometimes used instead of "designated".
        // Figure I might as well check for that?
        // Also, it seems that in New York City (all 5 boroughs) they have a designation
        // of local which is intended for trucks to use if the start and end their routes
        // inside of the borough. They use "designated" to mean truck routes that are
        // through routes. I think for routing purposes it feels safe to consider these
        // one and the same. But not sure.
        //
        if (way.hasTag("hgv", "designated") || way.hasTag("hgv", "yes") || way.hasTag("hgv", "local")) {
            flags = setBool(flags, K_DESIGNATED, true);
        }

        //
        // It appears that destination and delivery are used interchangeably
        // Both meaning that the route should be for deliveries only.
        //
        if (way.hasTag("hgv", "destination") || way.hasTag("hgv", "delivery")) {
            flags = setBool(flags, K_DESTINATION, true);
        }

        return flags;
    }

    @Override
    public long handleWayTags( OSMWay way, long allowed, long relationFlags )
    {
        long encoded = super.handleWayTags(way, allowed, relationFlags);

        if (encoded == 0) {
            return 0;
        }

        encoded = setWayTags(encoded, way);

        return encoded;
    }

    @Override
    public void applyWayTags(final OSMWay way,
                             final EdgeIteratorState edge)
    {
        long flags = setWayTags(edge.getFlags(), way);

        edge.setFlags(flags);
    }

    @Override
    public long setBool( long flags, int key, boolean value )
    {
        switch (key)
        {
        case K_DESIGNATED:
            return value ? flags | designatedbit : flags & ~designatedbit;
        case K_DESTINATION:
            return value ? flags | destinationbit : flags & ~destinationbit;
        default:
            return super.setBool(flags, key, value);
        }
    }

    @Override
    public boolean isBool( long flags, int key )
    {
        switch (key)
        {
        case K_DESIGNATED:
            return (flags & designatedbit) != 0;
        case K_DESTINATION:
            return (flags & destinationbit) != 0;
        default:
            return super.isBool(flags, key);
        }
    }

    @Override
    public String toString()
    {
        return "truck";
    }
}
