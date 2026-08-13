package com.graphhopper.routing;

import com.graphhopper.routing.ev.Country;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.RoadClass;
import com.graphhopper.routing.util.DirectedEdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.index.Snap;
import com.graphhopper.util.EdgeIteratorState;

import java.util.function.Function;

import static com.graphhopper.util.Parameters.Curbsides.*;

public class CurbsideAutoHelper {

    /**
     * Resolve AUTO curbsides based on road class and country. It will return ANY for one-ways.
     * Later maybe lanes and max_speed.
     * @param edgeFilter required to determine if one-way.
     * @param em retrieves road class and country encoded values from this look up
     * @return a function that takes the Snap as input and returns ANY, RIGHT or LEFT. I.e. resolves AUTO.
     */
    public static Function<Snap, String> createResolver(final DirectedEdgeFilter edgeFilter, final EncodingManager em) {
        EnumEncodedValue<RoadClass> roadClassEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass.class);
        EnumEncodedValue<Country> countryEnc = em.hasEncodedValue(Country.KEY) ? em.getEnumEncodedValue(Country.KEY, Country.class) : null;

        return snap -> {
            EdgeIteratorState edge = snap.getClosestEdge();

            // do not force curbside for 'smaller roads' (for now not configurable)
            RoadClass roadClass = edge.get(roadClassEnc);
            if (roadClass != RoadClass.PRIMARY && roadClass != RoadClass.SECONDARY && roadClass != RoadClass.TRUNK)
                return CURBSIDE_ANY;

            // do not force curbside for one-ways
            if (!edgeFilter.accept(edge, false) || !edgeFilter.accept(edge, true))
                return CURBSIDE_ANY;

            // do not force curbside for 'smaller roads' regarding lanes and max_speed
            // note: lane count in OSM is for the entire road - not just for one direction
            // TODO LATER: 'lanes' is 1 if OSM tag is missing, which might be rather misleading in this case
//            if (lanesEnc != null && edge.get(lanesEnc) < 2 && maxSpeedEnc != null && edge.get(maxSpeedEnc) <= 50)
//                return CURBSIDE_ANY;

            // could be different per point
            return countryEnc == null || edge.get(countryEnc).isRightHandTraffic() ? CURBSIDE_RIGHT : CURBSIDE_LEFT;
        };
    }
}
