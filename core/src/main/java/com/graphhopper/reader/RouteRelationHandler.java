package com.graphhopper.reader;

import com.graphhopper.reader.OSMRelation;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.util.ArrayList;

/**
 * Stores information about the membership of ways in routes.
 * Currently no data about the relations themselves is kept.
 * Author: Nop
 */
public class RouteRelationHandler
{
    private boolean trackHikingRoutes;
    private boolean trackBicylceRoutes;
    private boolean trackHorseRoutes;

    private TLongSet hikingWays;
    private TLongSet cycleWays;
    private TLongSet horseWays;

    public RouteRelationHandler( int routeFlags )
    {
        this.trackHikingRoutes = (routeFlags & AbstractFlagEncoder.NEEDS_HIKING_ROUTES) > 0;
        this.trackBicylceRoutes = (routeFlags & AbstractFlagEncoder.NEEDS_BICYCLE_ROUTES) > 0;;
        this.trackHorseRoutes = (routeFlags & AbstractFlagEncoder.NEEDS_HORSE_ROUTES) > 0;

        if( trackHikingRoutes )
            hikingWays = new TLongHashSet(10000);
        if( trackBicylceRoutes )
            cycleWays = new TLongHashSet(10000);
        if( trackHorseRoutes )
            horseWays = new TLongHashSet(1000);
    }

    public void processRelation( OSMRelation relation )
    {
        if( !relation.hasTag( "type", "route"))
            return;

        if( trackHikingRoutes && (relation.hasTag( "route", "foot") || relation.hasTag( "route", "hiking") ) )
            storeRouteMembers( hikingWays, relation.getMembers() );

        if( trackBicylceRoutes && relation.hasTag( "route", "bicycle") )
            storeRouteMembers( cycleWays, relation.getMembers() );

        if( trackHorseRoutes && relation.hasTag("route", "horse") )
            storeRouteMembers( horseWays, relation.getMembers() );
    }

    private void storeRouteMembers( TLongSet ways, ArrayList<OSMRelation.Member> members )
    {
        for (OSMRelation.Member member : members)
        {
            if( member.type() == OSMRelation.Member.WAY )
            {
                ways.add( member.ref() );
            }
        }
    }

    public boolean isOnHikingRoute( long wayId )
    {
        if( !trackHikingRoutes )
            throw new IllegalStateException( "Hiking route info requested, but tracking is not activated.");

        return hikingWays.contains( wayId );
    }

    public boolean isOnBicycleRoute( long wayId )
    {
        if( !trackBicylceRoutes )
            throw new IllegalStateException( "Bicycle route info requested, but tracking is not activated.");

        return cycleWays.contains( wayId );
    }

    public boolean isOnHorseRoute( long wayId )
    {
        if( !trackHorseRoutes )
            throw new IllegalStateException( "Horse route info requested, but tracking is not activated.");

        return horseWays.contains( wayId );
    }
}
