package com.graphhopper.application;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.LMProfile;
import com.graphhopper.gpx.GpxConversions;
import com.graphhopper.jackson.Gpx;
import com.graphhopper.matching.*;
import com.graphhopper.routing.TestProfiles;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for map matching using Andorra OSM data, based on
 * <a href="https://github.com/graphhopper/graphhopper/issues/3023">issue #3023</a>.
 */
public class MapMatchingAndorraTest {

    private static final String GH_LOCATION = "../target/mapmatchingtest-andorra";
    private static GraphHopper graphHopper;

    @BeforeAll
    public static void setup() {
        Helper.removeDir(new File(GH_LOCATION));
        graphHopper = new GraphHopper();
        graphHopper.setOSMFile("../core/files/andorra.osm.pbf");
        graphHopper.setGraphHopperLocation(GH_LOCATION);
        graphHopper.setEncodedValuesString("bike_access, bike_average_speed, bike_priority");
        graphHopper.setProfiles(TestProfiles.accessSpeedAndPriority("bike"));
        graphHopper.importOrLoad();
    }

    @AfterAll
    public static void after() {
        Helper.removeDir(new File(GH_LOCATION));
        graphHopper = null;
    }

    /**
     * Issue #3023: map matching should not produce dead-end detours or unnecessary backtracking.
     * The GPX trace follows the CG-2 road near Soldeu, Andorra. The matched result should stay
     * on or near that road without detouring into dead-end side streets.
     */
    @Test
    public void testIssue3023_noDeadEndDetours() throws Exception {
        PMap hints = new PMap()
                .putObject("profile", "bike");
        MapMatching mapMatching = MapMatching.fromGraphHopper(graphHopper, hints);
        mapMatching.setMeasurementErrorSigma(20);

        XmlMapper xmlMapper = new XmlMapper();
        Gpx gpx = xmlMapper.readValue(
                new FileInputStream("../map-matching/files/andorra_for_map_matching.gpx"), Gpx.class);
        List<Observation> observations = GpxConversions.getEntries(gpx.trk.get(0));

        MatchResult mr = mapMatching.match(observations);

        assertFalse(mr.getEdgeMatches().isEmpty());

        // The GPX trace covers ~600m along the road near Soldeu.
        // The matched distance should be in a reasonable range — not much longer than the GPX trace,
        // which would indicate dead-end detours/backtracking.
        double gpxLength = mr.getGpxEntriesLength();
        double matchLength = mr.getMatchLength();
        System.out.println("GPX entries length: " + gpxLength + "m");
        System.out.println("Match length: " + matchLength + "m");
        System.out.println("Ratio: " + (matchLength / gpxLength));

        // Print the street names traversed
        List<String> streets = MapMatchingTest.fetchStreets(mr.getEdgeMatches());
        System.out.println("Streets: " + streets);

        // Print the matched path geometry for inspection
        PointList points = new PointList();
        for (EdgeMatch em : mr.getEdgeMatches()) {
            PointList pl = em.getEdgeState().fetchWayGeometry(com.graphhopper.util.FetchMode.ALL);
            for (int i = 0; i < pl.size(); i++) {
                points.add(pl.getLat(i), pl.getLon(i));
            }
        }
        System.out.println("Matched path has " + points.size() + " geometry points");
        System.out.println("Matched edges: " + mr.getEdgeMatches().size());

        // The key assertion: the matched path should not be significantly longer than the
        // GPX trace. A ratio > 1.5 would indicate dead-end detours or backtracking.
        assertTrue(matchLength / gpxLength < 1.5,
                "Matched path (" + matchLength + "m) is much longer than GPX trace (" + gpxLength + "m), " +
                        "indicating dead-end detours or backtracking. Ratio: " + (matchLength / gpxLength));
    }
}
