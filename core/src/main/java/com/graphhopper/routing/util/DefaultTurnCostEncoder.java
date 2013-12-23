package com.graphhopper.routing.util;

/**
 * Default implementation of {@link AbstractTurnCostEncoder} which does not encode any costs yet,
 * but only restrictions in one bit. Therefore, each turn cost encoder requires only 1 bit storage
 * size at the moment.
 * <p>
 * @author karl.huebner
 */
public class DefaultTurnCostEncoder extends AbstractTurnCostEncoder
{
    /**
     * no costs, but only restrictions will be encoded
     */
    public DefaultTurnCostEncoder()
    {
        this(0); //we don't need costs yet
    }

    /**
     * Next to restrictions, turn costs will be encoded as well
     * <p>
     * @param maxCosts the maximum costs to be encoded by this encoder, everything above this costs
     * will be encoded as maxCosts
     */
    public DefaultTurnCostEncoder( int maxCosts )
    {
        super(determineRequiredBits(maxCosts)); //determine the number of bits required to store maxCosts
    }

    private static int determineRequiredBits( int number )
    {
        int bits = 0;
        while (number > 0)
        {
            number = number >> 1;
            bits++;
        }
        return bits;
    }

}
