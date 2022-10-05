package com.graphhopper.reader.osm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.util.Helper;

public abstract class RelationHandlerBase {
    /**
     * Creates turn relations out of an unspecified OSM relation
     */
    static List<OSMTurnRestriction> createTurnRestrictions(ReaderRelation relation) {
        List<OSMTurnRestriction> osmTurnRelations = new ArrayList<>();
        String vehicleTypeRestricted = "";
        List<String> vehicleTypesExcept = new ArrayList<>();
        if (relation.hasTag("except")) {
            String tagExcept = relation.getTag("except");
            if (!Helper.isEmpty(tagExcept)) {
                List<String> vehicleTypes = new ArrayList<>(Arrays.asList(tagExcept.split(";")));
                for (String vehicleType : vehicleTypes)
                    vehicleTypesExcept.add(vehicleType.trim());
            }
        }
        if (relation.hasTag("restriction")) {
            osmTurnRelations.addAll(createTurnRelations(relation, relation.getTag("restriction"),
                    vehicleTypeRestricted, vehicleTypesExcept));
            return osmTurnRelations;
        }
        if (relation.hasTagWithKeyPrefix("restriction:")) {
            List<String> vehicleTypesRestricted = relation.getKeysWithPrefix("restriction:");
            for (String vehicleType : vehicleTypesRestricted) {
                String restrictionType = relation.getTag(vehicleType);
                vehicleTypeRestricted = vehicleType.replace("restriction:", "").trim();
                osmTurnRelations.addAll(createTurnRelations(relation, restrictionType, vehicleTypeRestricted,
                        vehicleTypesExcept));
            }
        }
        return osmTurnRelations;
    }

    static List<OSMTurnRestriction> createTurnRelations(ReaderRelation relation, String restrictionType,
                                                        String vehicleTypeRestricted, List<String> vehicleTypesExcept) {
        OSMTurnRestriction.RestrictionType type = OSMTurnRestriction.RestrictionType.get(restrictionType);
        if (type != OSMTurnRestriction.RestrictionType.UNSUPPORTED) {
            ArrayList<Long> viaIDs = new ArrayList<>();
            OSMTurnRestriction.ViaType viaType = OSMTurnRestriction.ViaType.UNSUPPORTED;
            // we use -1 to indicate 'missing', which is fine because we exclude negative OSM IDs (see #2652)
            long toWayID = -1;

            // A Turn Restriction (https://wiki.openstreetmap.org/wiki/Relation:restriction) can consist of...

            for (ReaderRelation.Member member : relation.getMembers()) {
                // ONE TO Way (a "no_exit" restriction can have ONE OR MORE TO ways)
                if (ReaderElement.Type.WAY == member.getType() && "to".equals(member.getRole())) {
                    toWayID = member.getRef();
                }

                // ONE VIA NODE or
                else if (ReaderElement.Type.NODE == member.getType() && "via".equals(member.getRole())) {
                    if (viaType == OSMTurnRestriction.ViaType.UNSUPPORTED) {
                        viaIDs.add(member.getRef());
                        viaType = OSMTurnRestriction.ViaType.NODE;
                    }
                }
                // ONE OR MORE VIA WAYS
                else if (ReaderElement.Type.WAY == member.getType() && "via".equals(member.getRole())) {
                    if (viaType == OSMTurnRestriction.ViaType.UNSUPPORTED) {
                        viaIDs.add(member.getRef());
                        viaType = OSMTurnRestriction.ViaType.WAY;
                    } else if (viaType == OSMTurnRestriction.ViaType.WAY || viaType == OSMTurnRestriction.ViaType.MULTI_WAY) {
                        viaIDs.add(member.getRef());
                        viaType = OSMTurnRestriction.ViaType.MULTI_WAY;
                    }
                }
            }

            // ONE FROM Way (a "no_entry" restriction can have ONE OR MORE FROM ways)
            if (toWayID >= 0 && viaIDs.size() > 0) {
                List<OSMTurnRestriction> res = new ArrayList<>(2);
                for (ReaderRelation.Member member : relation.getMembers()) {
                    if (ReaderElement.Type.WAY == member.getType() && "from".equals(member.getRole())) {
                        OSMTurnRestriction osmTurnRelation = new OSMTurnRestriction(relation.getId(), member.getRef(), viaIDs, toWayID, type, viaType);
                        osmTurnRelation.setVehicleTypeRestricted(vehicleTypeRestricted);
                        osmTurnRelation.setVehicleTypesExcept(vehicleTypesExcept);
                        res.add(osmTurnRelation);
                    }
                }
                return res;
            }
        }
        return Collections.emptyList();
    }

}
