package com.graphhopper;

import com.graphhopper.reader.gtfs.GraphHopperProvider;
import com.graphhopper.reader.gtfs.GraphHopperGtfs;
import com.graphhopper.util.Helper;
import de.schildbach.pte.dto.Location;
import de.schildbach.pte.dto.LocationType;
import de.schildbach.pte.dto.NearbyLocationsResult;
import de.schildbach.pte.dto.Point;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.util.EnumSet;

public class GraphHopperRnvPteIT {

    private static final String GRAPH_LOC = "target/graphhopperIT-rnvpte-gtfs";
    private static GraphHopperProvider provider;

    @BeforeClass
    public static void init() {
        Helper.removeDir(new File(GRAPH_LOC));

        GraphHopperGtfs graphHopper = new GraphHopperGtfs();
        graphHopper.setGtfsFile("files/rnv.zip");
        graphHopper.setGraphHopperLocation(GRAPH_LOC);
        graphHopper.importOrLoad();
        provider = new GraphHopperProvider(graphHopper);
    }

    @Test
    public void stationById() throws Exception {
        NearbyLocationsResult result = provider.queryNearbyLocations(EnumSet.of(LocationType.STATION), new Location(LocationType.STATION, "1"), 0, 0);
        System.out.println(result);
    }

    final double TO_LAT = 49.42799, TO_LON = 8.6833; // 113612, Hans-Thoma-Platz

    @Test
    public void stationByCoord() throws Exception {
        NearbyLocationsResult result = provider.queryNearbyLocations(EnumSet.of(LocationType.STATION), Location.coord(Point.fromDouble(TO_LAT, TO_LON)), 0, 0);
        System.out.println(result);
    }

}
