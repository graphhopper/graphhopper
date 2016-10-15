package com.graphhopper.reader.gtfs;

import com.graphhopper.storage.index.QueryResult;
import de.schildbach.pte.NetworkId;
import de.schildbach.pte.NetworkProvider;
import de.schildbach.pte.dto.*;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;

public class GraphHopperProvider implements NetworkProvider {

    private final GraphHopperGtfs graphHopper;

    public GraphHopperProvider(GraphHopperGtfs graphHopper) {
        this.graphHopper = graphHopper;
    }

    @Override
    public NetworkId id() {
        return null;
    }

    @Override
    public boolean hasCapabilities(Capability... capabilities) {
        return false;
    }

    @Override
    public NearbyLocationsResult queryNearbyLocations(EnumSet<LocationType> enumSet, Location location, int i, int i1) throws IOException {
        if (location.hasLocation()) {
            GtfsStorage extension = (GtfsStorage) graphHopper.getGraphHopperStorage().getExtension();
            QueryResult closest = graphHopper.getLocationIndex().findClosest(location.getLatAsDouble(), location.getLonAsDouble(), new PtPositionLookupEdgeFilter(extension));
            int node = closest.getClosestNode();
            Location closestLocation = new Location(
                    LocationType.STATION,
                    Integer.toString(node),
                    Point.fromDouble(graphHopper.getGraphHopperStorage().getNodeAccess().getLatitude(node), graphHopper.getGraphHopperStorage().getNodeAccess().getLongitude(node)));
            ArrayList<Location> locations = new ArrayList<>();
            locations.add(closestLocation);
            return new NearbyLocationsResult(null, locations);
        } else if (location.type == LocationType.STATION && location.hasId()) {
            int node = Integer.parseInt(location.id);
            ArrayList<Location> locations = new ArrayList<>();
            if (node < graphHopper.getGraphHopperStorage().getNodes()) {
                Location idLocation = new Location(
                        LocationType.STATION,
                        Integer.toString(node),
                        Point.fromDouble(graphHopper.getGraphHopperStorage().getNodeAccess().getLatitude(node), graphHopper.getGraphHopperStorage().getNodeAccess().getLongitude(node)));
                locations.add(idLocation);
            }
            return new NearbyLocationsResult(null, locations);
        } else {
            throw new IllegalArgumentException("cannot handle: " + location);
        }
    }

    @Override
    public QueryDeparturesResult queryDepartures(String s, @Nullable Date date, int i, boolean b) throws IOException {
        return null;
    }

    @Override
    public SuggestLocationsResult suggestLocations(CharSequence charSequence) throws IOException {
        return null;
    }

    @Override
    public Set<Product> defaultProducts() {
        return null;
    }

    @Override
    public QueryTripsResult queryTrips(Location location, @Nullable Location location1, Location location2, Date date, boolean b, @Nullable Set<Product> set, @Nullable Optimize optimize, @Nullable WalkSpeed walkSpeed, @Nullable Accessibility accessibility, @Nullable Set<Option> set1) throws IOException {
        return null;
    }

    @Override
    public QueryTripsResult queryMoreTrips(QueryTripsContext queryTripsContext, boolean b) throws IOException {
        return null;
    }

    @Override
    public Style lineStyle(@Nullable String s, @Nullable Product product, @Nullable String s1) {
        return null;
    }

    @Override
    public Point[] getArea() throws IOException {
        return new Point[0];
    }
}
