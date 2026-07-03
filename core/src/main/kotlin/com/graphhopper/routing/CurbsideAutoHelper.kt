package com.graphhopper.routing

import com.graphhopper.routing.ev.Country
import com.graphhopper.routing.ev.RoadClass
import com.graphhopper.routing.util.DirectedEdgeFilter
import com.graphhopper.routing.util.EncodingManager
import com.graphhopper.storage.index.Snap
import com.graphhopper.util.Parameters.Curbsides.CURBSIDE_ANY
import com.graphhopper.util.Parameters.Curbsides.CURBSIDE_LEFT
import com.graphhopper.util.Parameters.Curbsides.CURBSIDE_RIGHT
import java.util.function.Function

object CurbsideAutoHelper {

    /**
     * Resolve AUTO curbsides based on road class and country. It will return ANY for one-ways.
     * Later maybe lanes and max_speed.
     * @param edgeFilter required to determine if one-way.
     * @param em retrieves road class and country encoded values from this look up
     * @return a function that takes the Snap as input and returns ANY, RIGHT or LEFT. I.e. resolves AUTO.
     */
    @JvmStatic
    fun createResolver(edgeFilter: DirectedEdgeFilter, em: EncodingManager): Function<Snap, String> {
        val roadClassEnc = em.getEnumEncodedValue(RoadClass.KEY, RoadClass::class.java)
        val countryEnc = if (em.hasEncodedValue(Country.KEY)) em.getEnumEncodedValue(Country.KEY, Country::class.java) else null

        return Function { snap ->
            val edge = snap.closestEdge!!

            // do not force curbside for 'smaller roads' (for now not configurable)
            val roadClass = edge.get(roadClassEnc)
            if (roadClass != RoadClass.PRIMARY && roadClass != RoadClass.SECONDARY && roadClass != RoadClass.TRUNK)
                return@Function CURBSIDE_ANY

            // do not force curbside for one-ways
            if (!edgeFilter.accept(edge, false) || !edgeFilter.accept(edge, true))
                return@Function CURBSIDE_ANY

            // do not force curbside for 'smaller roads' regarding lanes and max_speed
            // note: lane count in OSM is for the entire road - not just for one direction
            // TODO LATER: 'lanes' is 1 if OSM tag is missing, which might be rather misleading in this case
//            if (lanesEnc != null && edge.get(lanesEnc) < 2 && maxSpeedEnc != null && edge.get(maxSpeedEnc) <= 50)
//                return CURBSIDE_ANY;

            // could be different per point
            if (countryEnc == null || edge.get(countryEnc).isRightHandTraffic) CURBSIDE_RIGHT else CURBSIDE_LEFT
        }
    }
}
