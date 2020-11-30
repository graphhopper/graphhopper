// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.osm.pbf;

import com.carrotsearch.hppc.LongArrayList;
import com.google.protobuf.InvalidProtocolBufferException;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.OSMFileHeader;
import com.graphhopper.util.Helper;
import org.openstreetmap.osmosis.osmbinary.Fileformat;
import org.openstreetmap.osmosis.osmbinary.Osmformat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.zip.InflaterInputStream;

/**
 * Converts PBF block data into decoded entities ready to be passed into an Osmosis pipeline. This
 * class is designed to be passed into a pool of worker threads to allow multi-threaded decoding.
 * <p>
 *
 * @author Brett Henderson
 */
public class PbfBlobDecoder implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(PbfBlobDecoder.class);
    private final String blobType;
    private final byte[] rawBlob;
    private final PbfBlobDecoderListener listener;
    private List<ReaderElement> decodedEntities;

    /**
     * Creates a new instance.
     * <p>
     *
     * @param blobType The type of blob.
     * @param rawBlob  The raw data of the blob.
     * @param listener The listener for receiving decoding results.
     */
    public PbfBlobDecoder(String blobType, byte[] rawBlob, PbfBlobDecoderListener listener) {
        this.blobType = blobType;
        this.rawBlob = rawBlob;
        this.listener = listener;
    }

    private InputStream readBlobContent() throws IOException {
        Fileformat.Blob blob = Fileformat.Blob.parseFrom(rawBlob);

        if (blob.hasRaw()) {
            return blob.getRaw().newInput();
        }
        
        if (blob.hasZlibData()) {
            return new InflaterInputStream(blob.getZlibData().newInput());
        }
        
        throw new RuntimeException("PBF blob uses unsupported compression, only raw or zlib may be used.");
    }

    private void processOsmHeader(InputStream data) throws InvalidProtocolBufferException {
        Osmformat.HeaderBlock header;
        try {
            header = Osmformat.HeaderBlock.parseFrom(data);
        } catch (IOException e) {
             throw new UncheckedIOException(e);
        }

        // Build the list of active and unsupported features in the file.
        List<String> supportedFeatures = Arrays.asList("OsmSchema-V0.6", "DenseNodes");
        List<String> unsupportedFeatures = new ArrayList<>();
        for (String feature : header.getRequiredFeaturesList()) {
            if (!supportedFeatures.contains(feature)) {
                unsupportedFeatures.add(feature);
            }
        }

        // We can't continue if there are any unsupported features. We wait
        // until now so that we can display all unsupported features instead of
        // just the first one we encounter.
        if (!unsupportedFeatures.isEmpty()) {
            throw new RuntimeException("PBF file contains unsupported features " + unsupportedFeatures);
        }

        OSMFileHeader fileheader = new OSMFileHeader();
        long milliSecondDate = header.getOsmosisReplicationTimestamp();
        fileheader.setTag("timestamp", Helper.createFormatter().format(new Date(milliSecondDate * 1000)));
        decodedEntities.add(fileheader);
    }

    private Map<String, Object> buildNodeTags(Osmformat.Node node, PbfFieldDecoder fieldDecoder) {
        if (node.getKeysList().isEmpty()) {
            return null;
        }

        Map<String, Object> tags = new HashMap<>(node.getKeysCount());
        for (int i = 0; i < node.getKeysCount(); i++) {
            String key = fieldDecoder.decodeString(node.getKeys(i));
            String value = fieldDecoder.decodeString(node.getVals(i));
            tags.put(key, value);
        }
        return tags;
    }
    
    private Map<String, Object> buildWayTags(Osmformat.Way way, PbfFieldDecoder fieldDecoder) {
        if (way.getKeysList().isEmpty()) {
            return null;
        }

        Map<String, Object> tags = new HashMap<>(way.getKeysCount());
        for (int i = 0; i < way.getKeysCount(); i++) {
            String key = fieldDecoder.decodeString(way.getKeys(i));
            String value = fieldDecoder.decodeString(way.getVals(i));
            tags.put(key, value);
        }
        return tags;
    }
    
    private Map<String, Object> buildRelationTags(Osmformat.Relation relation, PbfFieldDecoder fieldDecoder) {
        if (relation.getKeysList().isEmpty()) {
            return null;
        }

        Map<String, Object> tags = new HashMap<>(relation.getKeysCount());
        for (int i = 0; i < relation.getKeysCount(); i++) {
            String key = fieldDecoder.decodeString(relation.getKeys(i));
            String value = fieldDecoder.decodeString(relation.getVals(i));
            tags.put(key, value);
        }
        return tags;
    }

    private void processNodes(List<Osmformat.Node> nodes, PbfFieldDecoder fieldDecoder) {
        for (Osmformat.Node node : nodes) {
            Map<String, Object> tags = buildNodeTags(node, fieldDecoder);

            ReaderNode osmNode = new ReaderNode(node.getId(), fieldDecoder.decodeLatitude(node
                    .getLat()), fieldDecoder.decodeLatitude(node.getLon()));
            osmNode.setTags(tags);

            // Add the bound object to the results.
            decodedEntities.add(osmNode);
        }
    }

    private void processNodes(Osmformat.DenseNodes nodes, PbfFieldDecoder fieldDecoder) {
        long nodeId = 0;
        long latitude = 0;
        long longitude = 0;
        int keyValueIndex = 0;
        for (int i = 0; i < nodes.getIdCount(); i++) {
            // Delta decode node fields.
            nodeId += nodes.getId(i);
            latitude += nodes.getLat(i);
            longitude += nodes.getLon(i);

            // Build the tags. The key and value string indexes are sequential
            // in the same PBF array. Each set of tags is delimited by an index
            // with a value of 0.
            Map<String, Object> tags = null;
            while (keyValueIndex < nodes.getKeysValsCount()) {
                int keyIndex = nodes.getKeysVals(keyValueIndex++);
                if (keyIndex == 0) {
                    break;
                }
                int valueIndex = nodes.getKeysVals(keyValueIndex++);

                if (tags == null) {
                    // divide by 2 as key&value, multiple by 2 because of the better approximation
                    tags = new HashMap<>(Math.max(3, 2 * (nodes.getKeysValsCount() / 2) / nodes.getIdCount()));
                }

                tags.put(fieldDecoder.decodeString(keyIndex), fieldDecoder.decodeString(valueIndex));
            }

            ReaderNode node = new ReaderNode(nodeId, ((double) latitude) / 10000000, ((double) longitude) / 10000000);
            node.setTags(tags);

            // Add the bound object to the results.
            decodedEntities.add(node);
        }
    }

    private void processWays(List<Osmformat.Way> ways, PbfFieldDecoder fieldDecoder) {
        for (Osmformat.Way way : ways) {
            Map<String, Object> tags = buildWayTags(way, fieldDecoder);

            // Build up the list of way nodes for the way. The node ids are
            // delta encoded meaning that each id is stored as a delta against
            // the previous one.
            long nodeId = 0;
            LongArrayList wayNodes = new LongArrayList(way.getRefsCount());
            for (int i = 0; i < way.getRefsCount(); i++) {
                nodeId += way.getRefs(i);
                wayNodes.add(nodeId);
            }
            
            ReaderWay osmWay = new ReaderWay(way.getId(), wayNodes);
            osmWay.setTags(tags);

            decodedEntities.add(osmWay);
        }
    }

    private void buildRelationMembers(ReaderRelation readerRelation, Osmformat.Relation pbfRelation, PbfFieldDecoder fieldDecoder) {
        // Build up the list of relation members for the way. The member ids are
        // delta encoded meaning that each id is stored as a delta against
        // the previous one.
        long refId = 0;
        for (int i = 0; i < pbfRelation.getMemidsCount(); i++) {
            refId += pbfRelation.getMemids(i);
            Osmformat.Relation.MemberType memberType = pbfRelation.getTypes(i);
            
            int entityType;
            if (memberType == Osmformat.Relation.MemberType.NODE) {
                entityType = ReaderRelation.Member.NODE;
            } else if (memberType == Osmformat.Relation.MemberType.WAY) {
                entityType = ReaderRelation.Member.WAY;
            } else {
                entityType = ReaderRelation.Member.RELATION;
            }

            ReaderRelation.Member member = new ReaderRelation.Member(entityType, refId,
                            fieldDecoder.decodeString(pbfRelation.getRolesSid(i)));
            readerRelation.add(member);
        }
    }
    

    private void processRelations(List<Osmformat.Relation> relations, PbfFieldDecoder fieldDecoder) {
        for (Osmformat.Relation relation : relations) {
            Map<String, Object> tags = buildRelationTags(relation, fieldDecoder);

            ReaderRelation osmRelation = new ReaderRelation(relation.getId());
            osmRelation.setTags(tags);

            buildRelationMembers(osmRelation, relation, fieldDecoder);

            // Add the bound object to the results.
            decodedEntities.add(osmRelation);
        }
    }

    private void processOsmPrimitives(InputStream data) throws InvalidProtocolBufferException {
        Osmformat.PrimitiveBlock block;
        try {
            block = Osmformat.PrimitiveBlock.parseFrom(data);
        } catch (IOException e) {
             throw new UncheckedIOException(e);
        }
        PbfFieldDecoder fieldDecoder = new PbfFieldDecoder(block);

        for (Osmformat.PrimitiveGroup primitiveGroup : block.getPrimitivegroupList()) {
            // A PrimitiveGroup MUST NEVER contain different types of objects.
            if (!primitiveGroup.getNodesList().isEmpty()) {
                processNodes(primitiveGroup.getNodesList(), fieldDecoder);
            } else if (!primitiveGroup.getDense().getIdList().isEmpty()) {
                processNodes(primitiveGroup.getDense(), fieldDecoder);
            } else if (!primitiveGroup.getWaysList().isEmpty()) {
                processWays(primitiveGroup.getWaysList(), fieldDecoder);
            } else if (!primitiveGroup.getRelationsList().isEmpty()) {
                processRelations(primitiveGroup.getRelationsList(), fieldDecoder);
            }
        }
    }

    private void runAndTrapExceptions() {
        try {
            decodedEntities = new ArrayList<>();
            if ("OSMHeader".equals(blobType)) {
                processOsmHeader(readBlobContent());

            } else if ("OSMData".equals(blobType)) {
                processOsmPrimitives(readBlobContent());

            } else {
                log.debug("Skipping unrecognised blob type {}", blobType);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to process PBF blob", e);
        }
    }

    @Override
    public void run() {
        try {
            runAndTrapExceptions();
            listener.complete(decodedEntities);

        } catch (RuntimeException e) {
            listener.error(e);
        }
    }
}
