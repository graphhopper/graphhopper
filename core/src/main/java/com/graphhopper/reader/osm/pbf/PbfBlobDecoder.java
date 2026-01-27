/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for
 *  additional information regarding copyright ownership.
 *
 *  GraphHopper GmbH licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except in
 *  compliance with the License. You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader.osm.pbf;

import com.carrotsearch.hppc.LongIndexedContainer;
import com.google.protobuf.InvalidProtocolBufferException;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.OSMFileHeader;
import com.graphhopper.reader.osm.SkipOptions;
import com.graphhopper.util.Helper;
import org.openstreetmap.osmosis.osmbinary.Fileformat;
import org.openstreetmap.osmosis.osmbinary.Osmformat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Synchronous PBF blob decoder that returns decoded entities directly.
 * This is a refactored version of PbfBlobDecoder that doesn't use callbacks.
 */
public class PbfBlobDecoder {
    private static final Logger log = LoggerFactory.getLogger(PbfBlobDecoder.class);
    private static final boolean CHECK_DATA = false;

    private final String blobType;
    private final byte[] rawBlob;
    private final SkipOptions skipOptions;
    private List<ReaderElement> decodedEntities;

    public PbfBlobDecoder(String blobType, byte[] rawBlob, SkipOptions skipOptions) {
        this.blobType = blobType;
        this.rawBlob = rawBlob;
        this.skipOptions = skipOptions;
    }

    /**
     * Decode the blob and return the list of entities.
     */
    public List<ReaderElement> decode() {
        decodedEntities = new ArrayList<>();
        try {
            if ("OSMHeader".equals(blobType)) {
                processOsmHeader(readBlobContent());
            } else if ("OSMData".equals(blobType)) {
                processOsmPrimitives(readBlobContent());
            } else if (log.isDebugEnabled()) {
                log.debug("Skipping unrecognised blob type " + blobType);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to process PBF blob", e);
        }
        return decodedEntities;
    }

    private byte[] readBlobContent() throws IOException {
        Fileformat.Blob blob = Fileformat.Blob.parseFrom(rawBlob);
        byte[] blobData;

        if (blob.hasRaw()) {
            blobData = blob.getRaw().toByteArray();
        } else if (blob.hasZlibData()) {
            Inflater inflater = new Inflater();
            inflater.setInput(blob.getZlibData().toByteArray());
            blobData = new byte[blob.getRawSize()];
            try {
                inflater.inflate(blobData);
            } catch (DataFormatException e) {
                throw new RuntimeException("Unable to decompress PBF blob.", e);
            }
            if (!inflater.finished()) {
                throw new RuntimeException("PBF blob contains incomplete compressed data.");
            }
            inflater.end();
        } else {
            throw new RuntimeException("PBF blob uses unsupported compression, only raw or zlib may be used.");
        }

        return blobData;
    }

    private void processOsmHeader(byte[] data) throws InvalidProtocolBufferException {
        Osmformat.HeaderBlock header = Osmformat.HeaderBlock.parseFrom(data);

        List<String> supportedFeatures = Arrays.asList("OsmSchema-V0.6", "DenseNodes");
        List<String> unsupportedFeatures = new ArrayList<>();
        for (String feature : header.getRequiredFeaturesList()) {
            if (!supportedFeatures.contains(feature)) {
                unsupportedFeatures.add(feature);
            }
        }

        if (!unsupportedFeatures.isEmpty()) {
            throw new RuntimeException("PBF file contains unsupported features " + unsupportedFeatures);
        }

        OSMFileHeader fileheader = new OSMFileHeader();
        long milliSecondDate = header.getOsmosisReplicationTimestamp();
        fileheader.setTag("timestamp", Helper.createFormatter().format(new Date(milliSecondDate * 1000)));
        decodedEntities.add(fileheader);
    }

    private void processOsmPrimitives(byte[] data) throws InvalidProtocolBufferException {
        Osmformat.PrimitiveBlock block = Osmformat.PrimitiveBlock.parseFrom(data);
        PbfFieldDecoder fieldDecoder = new PbfFieldDecoder(block);

        for (Osmformat.PrimitiveGroup primitiveGroup : block.getPrimitivegroupList()) {
            if (!skipOptions.isSkipNodes()) {
                processNodes(primitiveGroup.getDense(), fieldDecoder);
                processNodes(primitiveGroup.getNodesList(), fieldDecoder);
            }
            if (!skipOptions.isSkipWays())
                processWays(primitiveGroup.getWaysList(), fieldDecoder);
            if (!skipOptions.isSkipRelations())
                processRelations(primitiveGroup.getRelationsList(), fieldDecoder);
        }
    }

    private Map<String, Object> buildTags(List<Integer> keys, List<Integer> values, PbfFieldDecoder fieldDecoder) {
        if (CHECK_DATA && keys.size() != values.size()) {
            throw new RuntimeException("Number of tag keys (" + keys.size() + ") and tag values ("
                    + values.size() + ") don't match");
        }

        Iterator<Integer> keyIterator = keys.iterator();
        Iterator<Integer> valueIterator = values.iterator();
        if (keyIterator.hasNext()) {
            Map<String, Object> tags = new HashMap<>(keys.size());
            while (keyIterator.hasNext()) {
                String key = fieldDecoder.decodeString(keyIterator.next());
                String value = fieldDecoder.decodeString(valueIterator.next());
                tags.put(key, value);
            }
            return tags;
        }
        return null;
    }

    private void processNodes(List<Osmformat.Node> nodes, PbfFieldDecoder fieldDecoder) {
        for (Osmformat.Node node : nodes) {
            Map<String, Object> tags = buildTags(node.getKeysList(), node.getValsList(), fieldDecoder);
            ReaderNode osmNode = new ReaderNode(node.getId(),
                    fieldDecoder.decodeLatitude(node.getLat()),
                    fieldDecoder.decodeLatitude(node.getLon()));
            osmNode.setTags(tags);
            decodedEntities.add(osmNode);
        }
    }

    private void processNodes(Osmformat.DenseNodes nodes, PbfFieldDecoder fieldDecoder) {
        List<Long> idList = nodes.getIdList();
        List<Long> latList = nodes.getLatList();
        List<Long> lonList = nodes.getLonList();

        if (CHECK_DATA && ((idList.size() != latList.size()) || (idList.size() != lonList.size()))) {
            throw new RuntimeException("Number of ids (" + idList.size() + "), latitudes (" + latList.size()
                    + "), and longitudes (" + lonList.size() + ") don't match");
        }

        Iterator<Integer> keysValuesIterator = nodes.getKeysValsList().iterator();

        long nodeId = 0;
        long latitude = 0;
        long longitude = 0;

        for (int i = 0; i < idList.size(); i++) {
            nodeId += idList.get(i);
            latitude += latList.get(i);
            longitude += lonList.get(i);

            Map<String, Object> tags = null;
            while (keysValuesIterator.hasNext()) {
                int keyIndex = keysValuesIterator.next();
                if (keyIndex == 0) {
                    break;
                }
                if (CHECK_DATA && !keysValuesIterator.hasNext()) {
                    throw new RuntimeException(
                            "The PBF DenseInfo keys/values list contains a key with no corresponding value.");
                }
                int valueIndex = keysValuesIterator.next();

                if (tags == null) {
                    tags = new HashMap<>(Math.max(3, 2 * (nodes.getKeysValsList().size() / 2) / idList.size()));
                }
                tags.put(fieldDecoder.decodeString(keyIndex), fieldDecoder.decodeString(valueIndex));
            }

            ReaderNode node = new ReaderNode(nodeId,
                    fieldDecoder.decodeLatitude(latitude),
                    fieldDecoder.decodeLongitude(longitude));
            node.setTags(tags);
            decodedEntities.add(node);
        }
    }

    private void processWays(List<Osmformat.Way> ways, PbfFieldDecoder fieldDecoder) {
        for (Osmformat.Way way : ways) {
            Map<String, Object> tags = buildTags(way.getKeysList(), way.getValsList(), fieldDecoder);
            ReaderWay osmWay = new ReaderWay(way.getId());
            osmWay.setTags(tags);

            long nodeId = 0;
            LongIndexedContainer wayNodes = osmWay.getNodes();
            for (long nodeIdOffset : way.getRefsList()) {
                nodeId += nodeIdOffset;
                wayNodes.add(nodeId);
            }

            decodedEntities.add(osmWay);
        }
    }

    private void processRelations(List<Osmformat.Relation> relations, PbfFieldDecoder fieldDecoder) {
        for (Osmformat.Relation relation : relations) {
            Map<String, Object> tags = buildTags(relation.getKeysList(), relation.getValsList(), fieldDecoder);

            ReaderRelation osmRelation = new ReaderRelation(relation.getId());
            osmRelation.setTags(tags);

            buildRelationMembers(osmRelation, relation.getMemidsList(), relation.getRolesSidList(),
                    relation.getTypesList(), fieldDecoder);

            decodedEntities.add(osmRelation);
        }
    }

    private void buildRelationMembers(ReaderRelation relation,
                                      List<Long> memberIds, List<Integer> memberRoles,
                                      List<Osmformat.Relation.MemberType> memberTypes,
                                      PbfFieldDecoder fieldDecoder) {
        if (CHECK_DATA && ((memberIds.size() != memberRoles.size()) || (memberIds.size() != memberTypes.size()))) {
            throw new RuntimeException("Number of member ids (" + memberIds.size() + "), member roles ("
                    + memberRoles.size() + "), and member types (" + memberTypes.size() + ") don't match");
        }

        Iterator<Long> memberIdIterator = memberIds.iterator();
        Iterator<Integer> memberRoleIterator = memberRoles.iterator();
        Iterator<Osmformat.Relation.MemberType> memberTypeIterator = memberTypes.iterator();

        long refId = 0;
        while (memberIdIterator.hasNext()) {
            Osmformat.Relation.MemberType memberType = memberTypeIterator.next();
            refId += memberIdIterator.next();

            ReaderElement.Type entityType = ReaderElement.Type.NODE;
            if (memberType == Osmformat.Relation.MemberType.WAY) {
                entityType = ReaderElement.Type.WAY;
            } else if (memberType == Osmformat.Relation.MemberType.RELATION) {
                entityType = ReaderElement.Type.RELATION;
            }

            ReaderRelation.Member member = new ReaderRelation.Member(entityType, refId,
                    fieldDecoder.decodeString(memberRoleIterator.next()));
            relation.add(member);
        }
    }
}
