package com.graphhopper.routing.util.WayAcceptor;

import com.graphhopper.reader.OSMWay;

/**
 * Accepts a way
 *
 * @author Robin Boldt
 */
public interface WayAcceptor
{
    public boolean accept(OSMWay way);
}
