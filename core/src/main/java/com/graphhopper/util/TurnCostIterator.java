package com.graphhopper.util;

/**
 * @author Karl Hübner
 */
public interface TurnCostIterator
{

    public static int ANY_EDGE = -2;

    public boolean next();

    public int edgeFrom();

    public int edgeTo();

    public int costs();

}
