// This software is released into the Public Domain.  See copying.txt for details.
package com.graphhopper.reader.pbf;

import com.google.protobuf.InvalidProtocolBufferException;
import com.graphhopper.reader.OSMElement;
import com.graphhopper.reader.OSMNode;
import com.graphhopper.reader.OSMRelation;
import com.graphhopper.reader.OSMWay;
import org.openstreetmap.osmosis.osmbinary.Fileformat;
import org.openstreetmap.osmosis.osmbinary.Osmformat;
import gnu.trove.list.TLongList;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Converts PBF block data into decoded entities ready to be passed into an Osmosis pipeline. This
 * class is designed to be passed into a pool of worker threads to allow multi-threaded decoding.
 * <p/>
 * @author Brett Henderson
 */
public class PbfBlobDecoder implements Runnable
{
    private static Logger log = Logger.getLogger(PbfBlobDecoder.class.getName());
    private final boolean checkData = false;
    private String blobType;
    private byte[] rawBlob;
    private PbfBlobDecoderListener listener;
    private List<OSMElement> decodedEntities;

    /**
     * Creates a new instance.
     * <p/>
     * @param blobType The type of blob.
     * @param rawBlob The raw data of the blob.
     * @param listener The listener for receiving decoding results.
     */
    public PbfBlobDecoder( String blobType, byte[] rawBlob, PbfBlobDecoderListener listener )
    {
        this.blobType = blobType;
        this.rawBlob = rawBlob;
        this.listener = listener;
    }

    private byte[] readBlobContent() throws IOException
    {
        Fileformat.Blob blob = Fileformat.Blob.parseFrom(rawBlob);
        byte[] blobData;

        if (blob.hasRaw())
        {
            blobData = blob.getRaw().toByteArray();
        } else if (blob.hasZlibData())
        {
            Inflater inflater = new Inflater();
            inflater.setInput(blob.getZlibData().toByteArray());
            blobData = new byte[blob.getRawSize()];
            try
            {
                inflater.inflate(blobData);
            } catch (DataFormatException e)
            {
                throw new RuntimeException("Unable to decompress PBF blob.", e);
            }
            if (!inflater.finished())
            {
                throw new RuntimeException("PBF blob contains incomplete compressed data.");
            }
        } else
        {
            throw new RuntimeException("PBF blob uses unsupported compression, only raw or zlib may be used.");
        }

        return blobData;
    }

    private void processOsmHeader( byte[] data ) throws InvalidProtocolBufferException
    {
        Osmformat.HeaderBlock header = Osmformat.HeaderBlock.parseFrom(data);

        // Build the list of active and unsupported features in the file.
        List<String> supportedFeatures = Arrays.asList("OsmSchema-V0.6", "DenseNodes");
        List<String> activeFeatures = new ArrayList<String>();
        List<String> unsupportedFeatures = new ArrayList<String>();
        for (String feature : header.getRequiredFeaturesList())
        {
            if (supportedFeatures.contains(feature))
            {
                activeFeatures.add(feature);
            } else
            {
                unsupportedFeatures.add(feature);
            }
        }

        // We can't continue if there are any unsupported features. We wait
        // until now so that we can display all unsupported features instead of
        // just the first one we encounter.
        if (unsupportedFeatures.size() > 0)
        {
            throw new RuntimeException("PBF file contains unsupported features " + unsupportedFeatures);
        }

        // Build a new bound object which corresponds to the header.
/*
         Bound bound;
         if (header.hasBbox()) {
         HeaderBBox bbox = header.getBbox();
         bound = new Bound(bbox.getRight() * COORDINATE_SCALING_FACTOR, bbox.getLeft() * COORDINATE_SCALING_FACTOR,
         bbox.getTop() * COORDINATE_SCALING_FACTOR, bbox.getBottom() * COORDINATE_SCALING_FACTOR,
         header.getSource());
         } else {
         bound = new Bound(header.getSource());
         }

         // Add the bound object to the results.
         decodedEntities.add(new BoundContainer(bound));
         */
    }

    private Map<String, String> buildTags( List<Integer> keys, List<Integer> values, PbfFieldDecoder fieldDecoder )
    {

        // Ensure parallel lists are of equal size.
        if (checkData)
        {
            if (keys.size() != values.size())
            {
                throw new RuntimeException("Number of tag keys (" + keys.size() + ") and tag values ("
                        + values.size() + ") don't match");
            }
        }

        Iterator<Integer> keyIterator = keys.iterator();
        Iterator<Integer> valueIterator = values.iterator();
        if (keyIterator.hasNext())
        {
            Map<String, String> tags = new HashMap<String, String>();
            while (keyIterator.hasNext())
            {
                String key = fieldDecoder.decodeString(keyIterator.next());
                String value = fieldDecoder.decodeString(valueIterator.next());
                tags.put(key, value);
            }
            return tags;
        }
        return null;
    }

    private void processNodes( List<Osmformat.Node> nodes, PbfFieldDecoder fieldDecoder )
    {
        for (Osmformat.Node node : nodes)
        {
            Map<String, String> tags = buildTags(node.getKeysList(), node.getValsList(), fieldDecoder);

            OSMNode osmNode = new OSMNode(node.getId(), tags, fieldDecoder.decodeLatitude(node
                    .getLat()), fieldDecoder.decodeLatitude(node.getLon()));

            // Add the bound object to the results.
            decodedEntities.add(osmNode);
        }
    }

    private void processNodes( Osmformat.DenseNodes nodes, PbfFieldDecoder fieldDecoder )
    {
        List<Long> idList = nodes.getIdList();
        List<Long> latList = nodes.getLatList();
        List<Long> lonList = nodes.getLonList();

        // Ensure parallel lists are of equal size.
        if (checkData)
        {
            if ((idList.size() != latList.size()) || (idList.size() != lonList.size()))
            {
                throw new RuntimeException("Number of ids (" + idList.size() + "), latitudes (" + latList.size()
                        + "), and longitudes (" + lonList.size() + ") don't match");
            }
        }

        Iterator<Integer> keysValuesIterator = nodes.getKeysValsList().iterator();

        /*
         Osmformat.DenseInfo denseInfo;
         if (nodes.hasDenseinfo()) {
         denseInfo = nodes.getDenseinfo();
         } else {
         denseInfo = null;
         }
         */

        long nodeId = 0;
        long latitude = 0;
        long longitude = 0;
//		int userId = 0;
//		int userSid = 0;
//		long timestamp = 0;
//		long changesetId = 0;
        for (int i = 0; i < idList.size(); i++)
        {
            // Delta decode node fields.
            nodeId += idList.get(i);
            latitude += latList.get(i);
            longitude += lonList.get(i);

            /*
             if (denseInfo != null) {
             // Delta decode dense info fields.
             userId += denseInfo.getUid(i);
             userSid += denseInfo.getUserSid(i);
             timestamp += denseInfo.getTimestamp(i);
             changesetId += denseInfo.getChangeset(i);

             // Build the user, but only if one exists.
             OsmUser user;
             if (userId >= 0) {
             user = new OsmUser(userId, fieldDecoder.decodeString(userSid));
             } else {
             user = OsmUser.NONE;
             }

             entityData = new CommonEntityData(nodeId, denseInfo.getVersion(i),
             fieldDecoder.decodeTimestamp(timestamp), user, changesetId);
             } else {
             entityData = new CommonEntityData(nodeId, EMPTY_VERSION, EMPTY_TIMESTAMP, OsmUser.NONE,
             EMPTY_CHANGESET);
             }
             */

            // Build the tags. The key and value string indexes are sequential
            // in the same PBF array. Each set of tags is delimited by an index
            // with a value of 0.
            Map<String, String> tags = null;
            while (keysValuesIterator.hasNext())
            {
                int keyIndex = keysValuesIterator.next();
                if (keyIndex == 0)
                {
                    break;
                }
                if (checkData)
                {
                    if (!keysValuesIterator.hasNext())
                    {
                        throw new RuntimeException(
                                "The PBF DenseInfo keys/values list contains a key with no corresponding value.");
                    }
                }
                int valueIndex = keysValuesIterator.next();

                if (tags == null)
                {
                    tags = new HashMap<String, String>();
                }

                tags.put(fieldDecoder.decodeString(keyIndex), fieldDecoder.decodeString(valueIndex));
            }

            OSMNode node = new OSMNode(nodeId, tags, ((double) latitude) / 10000000, ((double) longitude) / 10000000);

            // Add the bound object to the results.
            decodedEntities.add(node);
        }
    }

    private void processWays( List<Osmformat.Way> ways, PbfFieldDecoder fieldDecoder )
    {
        for (Osmformat.Way way : ways)
        {
            Map<String, String> tags = buildTags(way.getKeysList(), way.getValsList(), fieldDecoder);

            OSMWay osmWay = new OSMWay(way.getId(), tags);

            // Build up the list of way nodes for the way. The node ids are
            // delta encoded meaning that each id is stored as a delta against
            // the previous one.
            long nodeId = 0;
            TLongList wayNodes = osmWay.getNodes();
            for (long nodeIdOffset : way.getRefsList())
            {
                nodeId += nodeIdOffset;
                wayNodes.add(nodeId);
            }

            decodedEntities.add(osmWay);
        }
    }

    private void buildRelationMembers( OSMRelation relation,
            List<Long> memberIds, List<Integer> memberRoles, List<Osmformat.Relation.MemberType> memberTypes,
            PbfFieldDecoder fieldDecoder )
    {

        ArrayList<OSMRelation.Member> members = relation.getMembers();

        // Ensure parallel lists are of equal size.
        if (checkData)
        {
            if ((memberIds.size() != memberRoles.size()) || (memberIds.size() != memberTypes.size()))
            {
                throw new RuntimeException("Number of member ids (" + memberIds.size() + "), member roles ("
                        + memberRoles.size() + "), and member types (" + memberTypes.size() + ") don't match");
            }
        }

        Iterator<Long> memberIdIterator = memberIds.iterator();
        Iterator<Integer> memberRoleIterator = memberRoles.iterator();
        Iterator<Osmformat.Relation.MemberType> memberTypeIterator = memberTypes.iterator();

        // Build up the list of relation members for the way. The member ids are
        // delta encoded meaning that each id is stored as a delta against
        // the previous one.
        long refId = 0;
        while (memberIdIterator.hasNext())
        {
            Osmformat.Relation.MemberType memberType = memberTypeIterator.next();
            refId += memberIdIterator.next();

            int entityType = OSMRelation.Member.NODE;
            if (memberType == Osmformat.Relation.MemberType.WAY)
            {
                entityType = OSMRelation.Member.WAY;
            } else if (memberType == Osmformat.Relation.MemberType.RELATION)
            {
                entityType = OSMRelation.Member.RELATION;
            }
            if (checkData)
            {
                if (entityType == OSMRelation.Member.NODE && memberType != Osmformat.Relation.MemberType.NODE)
                {
                    throw new RuntimeException("Member type of " + memberType + " is not supported.");
                }
            }


            OSMRelation.Member member = new OSMRelation.Member(entityType, refId, fieldDecoder.decodeString(memberRoleIterator.next()));

            members.add(member);
        }
    }

    private void processRelations( List<Osmformat.Relation> relations, PbfFieldDecoder fieldDecoder )
    {
        for (Osmformat.Relation relation : relations)
        {
            Map<String, String> tags = buildTags(relation.getKeysList(), relation.getValsList(), fieldDecoder);

            OSMRelation osmRelation = new OSMRelation(relation.getId(), tags);

            buildRelationMembers(osmRelation, relation.getMemidsList(), relation.getRolesSidList(),
                    relation.getTypesList(), fieldDecoder);

            // Add the bound object to the results.
            decodedEntities.add(osmRelation);
        }
    }

    private void processOsmPrimitives( byte[] data ) throws InvalidProtocolBufferException
    {
        Osmformat.PrimitiveBlock block = Osmformat.PrimitiveBlock.parseFrom(data);
        PbfFieldDecoder fieldDecoder = new PbfFieldDecoder(block);

        for (Osmformat.PrimitiveGroup primitiveGroup : block.getPrimitivegroupList())
        {
            log.finer("Processing OSM primitive group.");
            processNodes(primitiveGroup.getDense(), fieldDecoder);
            processNodes(primitiveGroup.getNodesList(), fieldDecoder);
            processWays(primitiveGroup.getWaysList(), fieldDecoder);
            processRelations(primitiveGroup.getRelationsList(), fieldDecoder);
        }
    }

    private void runAndTrapExceptions()
    {
        try
        {
            decodedEntities = new ArrayList<OSMElement>();

            if ("OSMHeader".equals(blobType))
            {
                processOsmHeader(readBlobContent());

            } else if ("OSMData".equals(blobType))
            {
                processOsmPrimitives(readBlobContent());

            } else
            {
                if (log.isLoggable(Level.FINER))
                {
                    log.finer("Skipping unrecognised blob type " + blobType);
                }
            }

        } catch (IOException e)
        {
            throw new RuntimeException("Unable to process PBF blob", e);
        }
    }

    @Override
    public void run()
    {
        try
        {
            runAndTrapExceptions();

            listener.complete(decodedEntities);

        } catch (RuntimeException e)
        {
            listener.error();
        }
    }
}
