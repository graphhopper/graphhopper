package com.samsix.graphhopper;

import static com.graphhopper.util.Helper.keepIn;

import com.graphhopper.reader.OSMWay;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodedDoubleValue;
import com.graphhopper.routing.util.EncodedValue;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;

public class TruckFlagEncoder
    extends
        CarFlagEncoder
{
    public static final int K_DESIGNATED = 101;
    public static final int K_DESTINATION = 102;
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

    //
    // TODO: Is this necessary? Is this correct? Do I use zeros here and save zeros?
    //
    @Override
    public long handleWayTags( OSMWay way, long allowed, long relationFlags )
    {
        if (way.hasTag("hgv", "designated")) {
            return setBool(0, K_DESIGNATED, true);
        }
        
        if (way.hasTag("hgv", "destination")) {
            return setBool(0, K_DESTINATION, true);
        }
        
        return 0;
    }
    
    @Override
    public void applyWayTags(final OSMWay way,
                             final EdgeIteratorState edge)
    {
        long flags = edge.getFlags();
        
        if (way.hasTag("hgv", "designated")) {
            flags = setBool(flags, K_DESIGNATED, true);
        }
        
        if (way.hasTag("hgv", "destination")) {
            flags = setBool(flags, K_DESIGNATED, true);
        }

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
