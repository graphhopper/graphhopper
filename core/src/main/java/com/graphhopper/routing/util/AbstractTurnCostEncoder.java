package com.graphhopper.routing.util;

/**
 * Very simple turn cost encoder, which stores the turn restriction in the first bit, and the turn
 * costs (in seconds) in x additional bits (0..2^x-1)
 * <p>
 * @author karl.huebner
 */
public abstract class AbstractTurnCostEncoder implements TurnCostEncoder
{
    private final int maxCostsBits;
    private final int costsMask;

    protected int restrictionBit;
    protected int costShift;

    public AbstractTurnCostEncoder( int maxCostsBits )
    {
        this.maxCostsBits = maxCostsBits;

        int mask = 0;
        for (int i = 0; i < this.maxCostsBits; i++)
        {
            mask |= (1 << i);
        }
        this.costsMask = mask;

        defineBits(0, 0);
    }

    @Override
    public int defineBits( int index, int shift )
    {
        restrictionBit = 1 << shift;
        costShift = shift + 1;
        return shift + maxCostsBits + 1;
    }

    @Override
    public boolean isRestricted( int flag )
    {
        return (flag & restrictionBit) != 0;
    }

    @Override
    public int getCosts( int flag )
    {
        int result = (flag >> costShift) & costsMask;
        if (result >= Math.pow(2, maxCostsBits) || result < 0)
        {
            throw new IllegalStateException("Wrong encoding of turn costs");
        }
        return result;
    }

    @Override
    public int flags( boolean restricted, int costs )
    {
        costs = Math.min(costs, (int) (Math.pow(2, maxCostsBits) - 1));
        int encode = costs << costShift;
        if (restricted)
        {
            encode |= restrictionBit;
        }
        return encode;
    }

}
