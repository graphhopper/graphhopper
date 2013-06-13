package com.graphhopper.reader.dem;

import com.graphhopper.reader.GeometryAccess;
import com.graphhopper.reader.OSMWay;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.Helper;
import gnu.trove.list.TIntList;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TIntArrayList;

/**
 * Analyzes the elevation differences along a way.
 * Author: Nop
 */
public class ElevationAnalyzer
{
    public static final int LAT = 0;
    public static final int LON = 1;
    public static final int ELEVATION = 2;
    public static final int DISTANCE = 3;

    public static final int RECORD_SIZE = 4;

    private static DistanceCalc distCalc = new DistanceCalc();


    private GeometryAccess geometryAccess;

    private int count;
    // list contains lat, lon, elevation and distance
    private TIntList nodes;

    private int ascend;
    private int descend;
    private int ascendDistance;
    private int descendDistance;
    private int totalDistance;

    private int totalIncline;
    private int avgIncline;
    private int avgDecline;
    private int maxIncline;
    private int maxDecline;


    /**
     * Initialize the Analyzer with the way data.
     * Calculate total distance.
     * @param way
     * @param geometryAccess
     */
    public void initialize( OSMWay way, GeometryAccess geometryAccess ) {
        this.geometryAccess = geometryAccess;

        TLongList nodeIds = way.nodes();
        count = nodeIds.size();
        nodes = new TIntArrayList( 2 * RECORD_SIZE * count );

        totalDistance = 0;

        double lastLat = 0;
        double lastLon = 0;
        double lat = 0;
        double lon = 0;
        int node[] = new int[3];
        for( int i = 0; i < count; i++ ) {
            long osmId = nodeIds.get( i );

            // get node coordinates and elevation from graph
            geometryAccess.getNode( osmId, node );
            lat = Helper.intToDegree(node[0]);
            lon = Helper.intToDegree( node[1] );

            nodes.add( node );

            // calculate distances to previous node
            if( i == 0 )
                nodes.add( 0 );
            else {
                double distance = distCalc.calcDist( lastLat, lastLon, lat, lon );
                nodes.add( (int) distance );
                totalDistance += distance;
            }
            lastLat = lat;
            lastLon = lon;
        }
    }

    public void analyzeElevations()
    {
        ascend=0;
        descend=0;
        ascendDistance=0;
        descendDistance=0;

        maxIncline=0;
        maxDecline=0;
        avgIncline=0;
        avgDecline=0;

        int lastEle = nodes.get( ELEVATION );
        int index;
        int ele;
        int distance;
        int delta;
        int incline;
        for( int i = 1; i < count; i++ ) {
            index = i* RECORD_SIZE;
            ele = nodes.get( index + ELEVATION );
            distance = nodes.get( index + DISTANCE );
            if( distance > 0 ) {
                delta = ele - lastEle;
                incline = 100*delta/distance;

                if( delta > 0 ) {
                    ascend += delta;
                    ascendDistance += distance;
                }
                if( delta < 0 ) {
                    descend += delta;
                    descendDistance += distance;
                }

                if( maxIncline < incline )
                    maxIncline=incline;
                if( maxDecline > incline )
                    maxDecline = incline;
            }
            lastEle = ele;
        }
        if( totalDistance > 0)
            totalIncline = 100*(ascend+descend)/totalDistance;
        if( ascendDistance > 0 )
            avgIncline = 100*ascend / ascendDistance;
        if( descendDistance > 0 )
            avgDecline = 100*descend / descendDistance;
    }

    public int getAscend() {
        return ascend;
    }

    public int getDescend() {
        return descend;
    }

    public int getTotalDistance() {
        return totalDistance;
    }

    public int getAverageIncline() {
        return avgIncline;
    }

    public int getMaxIncline() {
        return maxIncline;
    }

    public int getMaxDecline() {
        return maxDecline;
    }

    public int getAscendDistance() {
        return ascendDistance;
    }

    public int getDescendDistance() {
        return descendDistance;
    }

    public int getTotalIncline() {
        return totalIncline;
    }

    public int getAverageDecline() {
        return avgDecline;
    }
}
