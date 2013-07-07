package com.graphhopper.reader;

import com.graphhopper.reader.RouteRelationHandler;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Nop
 */
public class RelationHandlerTest
{
    @Test
    public void testRouteRelations()
    {
        RouteRelationHandler handler = new RouteRelationHandler(true, true, true);

        Map<String,String> tags1 = new HashMap<String, String>();
        tags1.put( "type", "route");
        tags1.put( "route", "foot");
        OSMRelation rel = new OSMRelation(1, tags1 );
        rel.add(new OSMRelation.Member( OSMRelation.Member.NODE, 101, ""));
        rel.add(new OSMRelation.Member( OSMRelation.Member.WAY, 102, ""));
        rel.add(new OSMRelation.Member( OSMRelation.Member.WAY, 103, ""));
        handler.processRelation( rel );

        Map<String,String> tags2 = new HashMap<String, String>();
        tags2.put( "type", "route");
        tags2.put( "route", "hiking");
        rel = new OSMRelation(2, tags2 );
        rel.add(new OSMRelation.Member(OSMRelation.Member.WAY, 104, ""));
        handler.processRelation( rel );

        Map<String,String> tags3 = new HashMap<String, String>();
        tags3.put( "type", "route");
        tags3.put( "route", "bicycle");
        rel = new OSMRelation(3, tags3 );
        rel.add(new OSMRelation.Member(OSMRelation.Member.WAY, 103, ""));
        rel.add(new OSMRelation.Member(OSMRelation.Member.WAY, 105, ""));
        handler.processRelation( rel );

        Map<String,String> tags4 = new HashMap<String, String>();
        tags4.put( "type", "route");
        tags4.put( "route", "horse");
        rel = new OSMRelation(4, tags4 );
        rel.add(new OSMRelation.Member(OSMRelation.Member.WAY, 103, ""));
        rel.add(new OSMRelation.Member(OSMRelation.Member.WAY, 106, ""));
        handler.processRelation( rel );

        Map<String,String> tags5 = new HashMap<String, String>();
        tags5.put( "type", "multipolygon");
        rel = new OSMRelation(5, tags5 );
        rel.add(new OSMRelation.Member(OSMRelation.Member.WAY, 107, ""));
        handler.processRelation( rel );

        // undefined way
        assertFalse( handler.isOnHikingRoute( 100 ) );
        assertFalse( handler.isOnBicycleRoute( 100 ) );
        assertFalse( handler.isOnHorseRoute( 100 ) );
        // node in route relation
        assertFalse( handler.isOnHikingRoute( 101 ) );
        assertFalse( handler.isOnBicycleRoute( 101 ) );
        assertFalse( handler.isOnHorseRoute( 101 ) );
        // not a route
        assertFalse( handler.isOnHikingRoute( 107 ) );
        assertFalse( handler.isOnBicycleRoute( 107 ) );
        assertFalse( handler.isOnHorseRoute( 107 ) );

        assertTrue( handler.isOnHikingRoute( 102 ) );
        assertFalse( handler.isOnBicycleRoute( 102 ) );
        assertFalse( handler.isOnHorseRoute( 102 ) );

        assertTrue( handler.isOnHikingRoute( 103 ) );
        assertTrue(handler.isOnBicycleRoute(103));
        assertTrue(handler.isOnHorseRoute(103));

        assertTrue(handler.isOnHikingRoute(104));
        assertFalse( handler.isOnBicycleRoute( 104 ) );
        assertFalse(handler.isOnHorseRoute(104));

        assertFalse(handler.isOnHikingRoute(105));
        assertTrue( handler.isOnBicycleRoute( 105 ) );
        assertFalse(handler.isOnHorseRoute(105));

        assertFalse(handler.isOnHikingRoute(106));
        assertFalse(handler.isOnBicycleRoute(106));
        assertTrue(handler.isOnHorseRoute(106));
    }
}
