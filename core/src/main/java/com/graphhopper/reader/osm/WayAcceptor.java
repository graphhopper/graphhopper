package com.graphhopper.reader.osm;

import com.graphhopper.reader.OSMWay;

/**
 * Accepts a way
 *
 * @author Robin Boldt
 */
public interface WayAcceptor
{
    public boolean accept( OSMWay way );
}
