package com.graphhopper.reader.osm;

import java.util.ArrayList;
import java.util.List;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.WayRestriction;
import com.graphhopper.reader.osm.OSMTurnRestriction.ViaType;
import com.graphhopper.routing.util.OSMParsers;
import com.graphhopper.storage.IntsRef;

public class RelationPreprocessor extends RelationHandlerBase {
	private final OSMParsers osmParsers;
	private final OSMTurnRestrictionData restrictionData;
	private final RelationFlagsData relationFlagsData;

	public RelationPreprocessor(OSMParsers osmParsers, OSMTurnRestrictionData restrictionData, RelationFlagsData relationFlagsData) {
		this.osmParsers = osmParsers;
		this.restrictionData = restrictionData;
		this.relationFlagsData = relationFlagsData;
	}

    protected void preprocessRelation(ReaderRelation relation) {
        if (!relation.isMetaRelation() && relation.hasTag("type", "route")) {
            // we keep track of all route relations, so they are available when we create edges later
            for (ReaderRelation.Member member : relation.getMembers()) {
                if (member.getType() != ReaderElement.Type.WAY)
                    continue;
                IntsRef oldRelationFlags = relationFlagsData.getRelFlagsMap(member.getRef());
                IntsRef newRelationFlags = osmParsers.handleRelationTags(relation, oldRelationFlags);
                relationFlagsData.putRelFlagsMap(member.getRef(), newRelationFlags);
            }
        }

        if (relation.hasTag("type", "restriction")) {
            // we keep the osm way ids that occur in turn relations, because this way we know for which GH edges
            // we need to remember the associated osm way id. this is just an optimization that is supposed to save
            // memory compared to simply storing the osm way ids in a long array where the array index is the GH edge
            // id.
            List<OSMTurnRestriction> turnRestrictions = createTurnRestrictions(relation);
            for (OSMTurnRestriction turnRestriction : turnRestrictions) {
                ArrayList<Long> ways = turnRestriction.getWays();
                
                if (turnRestriction.getViaType() == ViaType.WAY) {
                	restrictionData.wayRestrictions.add(new WayRestriction(relation.getId(), ways));
                	restrictionData.osmWayIdsToIgnore.add(ways.get(0));
                }
                
                for (Long wayId : ways) {
                	restrictionData.osmWayIdSet.add(wayId);
                    if (turnRestriction.getViaType() == ViaType.WAY)
                    	restrictionData.osmWayMap.put(wayId, null);
                }
            }
        }
    }
}
