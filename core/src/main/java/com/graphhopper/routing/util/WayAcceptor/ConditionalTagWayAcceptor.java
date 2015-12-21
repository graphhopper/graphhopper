package com.graphhopper.routing.util.WayAcceptor;

import com.graphhopper.reader.OSMWay;

import java.util.Calendar;

/**
 *
 * @author Robin Boldt
 */
public class ConditionalTagWayAcceptor implements WayAcceptor
{

    /**
     * Create with todays date
     */
    public ConditionalTagWayAcceptor(){

    }

    /**
     * Create with given date
     */
    public ConditionalTagWayAcceptor( Calendar date){

    }

    @Override
    public boolean accept( OSMWay way )
    {
        return false;
    }
}
