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
package com.graphhopper.reader.osm.pbf

import com.carrotsearch.hppc.LongIndexedContainer
import com.google.protobuf.InvalidProtocolBufferException
import com.graphhopper.reader.ReaderElement
import com.graphhopper.reader.ReaderNode
import com.graphhopper.reader.ReaderRelation
import com.graphhopper.reader.ReaderWay
import com.graphhopper.reader.osm.OSMFileHeader
import com.graphhopper.reader.osm.SkipOptions
import com.graphhopper.util.Helper
import crosby.binary.Fileformat
import crosby.binary.Osmformat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.Date
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * Synchronous PBF blob decoder that returns decoded entities directly.
 * This is a refactored version of PbfBlobDecoder that doesn't use callbacks.
 */
class PbfBlobDecoder(
    private val blobType: String,
    private val rawBlob: ByteArray,
    private val skipOptions: SkipOptions
) {
    private lateinit var decodedEntities: MutableList<ReaderElement>

    /**
     * Decode the blob and return the list of entities.
     */
    fun decode(): List<ReaderElement> {
        decodedEntities = ArrayList()
        try {
            if ("OSMHeader" == blobType) {
                processOsmHeader(readBlobContent())
            } else if ("OSMData" == blobType) {
                processOsmPrimitives(readBlobContent())
            } else if (log.isDebugEnabled) {
                log.debug("Skipping unrecognised blob type $blobType")
            }
        } catch (e: IOException) {
            throw RuntimeException("Unable to process PBF blob", e)
        }
        return decodedEntities
    }

    @Throws(IOException::class)
    private fun readBlobContent(): ByteArray {
        val blob = Fileformat.Blob.parseFrom(rawBlob)
        val blobData: ByteArray

        if (blob.hasRaw()) {
            blobData = blob.raw.toByteArray()
        } else if (blob.hasZlibData()) {
            val inflater = Inflater()
            inflater.setInput(blob.zlibData.toByteArray())
            blobData = ByteArray(blob.rawSize)
            try {
                inflater.inflate(blobData)
            } catch (e: DataFormatException) {
                throw RuntimeException("Unable to decompress PBF blob.", e)
            }
            if (!inflater.finished()) {
                throw RuntimeException("PBF blob contains incomplete compressed data.")
            }
            inflater.end()
        } else {
            throw RuntimeException("PBF blob uses unsupported compression, only raw or zlib may be used.")
        }

        return blobData
    }

    @Throws(InvalidProtocolBufferException::class)
    private fun processOsmHeader(data: ByteArray) {
        val header = Osmformat.HeaderBlock.parseFrom(data)

        val supportedFeatures = listOf("OsmSchema-V0.6", "DenseNodes")
        val unsupportedFeatures = ArrayList<String>()
        for (feature in header.requiredFeaturesList) {
            if (!supportedFeatures.contains(feature)) {
                unsupportedFeatures.add(feature)
            }
        }

        if (unsupportedFeatures.isNotEmpty()) {
            throw RuntimeException("PBF file contains unsupported features $unsupportedFeatures")
        }

        val fileheader = OSMFileHeader()
        val milliSecondDate = header.osmosisReplicationTimestamp
        fileheader.setTag("timestamp", Helper.createFormatter().format(Date(milliSecondDate * 1000)))
        decodedEntities.add(fileheader)
    }

    @Throws(InvalidProtocolBufferException::class)
    private fun processOsmPrimitives(data: ByteArray) {
        val block = Osmformat.PrimitiveBlock.parseFrom(data)
        val fieldDecoder = PbfFieldDecoder(block)

        for (primitiveGroup in block.primitivegroupList) {
            if (!skipOptions.isSkipNodes) {
                processNodes(primitiveGroup.dense, fieldDecoder)
                processNodes(primitiveGroup.nodesList, fieldDecoder)
            }
            if (!skipOptions.isSkipWays)
                processWays(primitiveGroup.waysList, fieldDecoder)
            if (!skipOptions.isSkipRelations)
                processRelations(primitiveGroup.relationsList, fieldDecoder)
        }
    }

    private fun buildTags(keys: List<Int>, values: List<Int>, fieldDecoder: PbfFieldDecoder): MutableMap<String, Any>? {
        if (CHECK_DATA && keys.size != values.size) {
            throw RuntimeException("Number of tag keys (" + keys.size + ") and tag values ("
                    + values.size + ") don't match")
        }

        val keyIterator = keys.iterator()
        val valueIterator = values.iterator()
        if (keyIterator.hasNext()) {
            val tags = HashMap<String, Any>(keys.size)
            while (keyIterator.hasNext()) {
                val key = fieldDecoder.decodeString(keyIterator.next())
                val value = fieldDecoder.decodeString(valueIterator.next())
                tags[key] = value
            }
            return tags
        }
        return null
    }

    private fun processNodes(nodes: List<Osmformat.Node>, fieldDecoder: PbfFieldDecoder) {
        for (node in nodes) {
            val tags = buildTags(node.keysList, node.valsList, fieldDecoder)
            // note: like the original (Osmosis-derived) Java code this uses decodeLatitude for the longitude,
            // which is only correct as long as granularity/offset are identical for both axes
            val osmNode = ReaderNode(node.id,
                    fieldDecoder.decodeLatitude(node.lat),
                    fieldDecoder.decodeLatitude(node.lon))
            osmNode.setTags(tags)
            decodedEntities.add(osmNode)
        }
    }

    private fun processNodes(nodes: Osmformat.DenseNodes, fieldDecoder: PbfFieldDecoder) {
        val idList = nodes.idList
        val latList = nodes.latList
        val lonList = nodes.lonList

        if (CHECK_DATA && (idList.size != latList.size || idList.size != lonList.size)) {
            throw RuntimeException("Number of ids (" + idList.size + "), latitudes (" + latList.size
                    + "), and longitudes (" + lonList.size + ") don't match")
        }

        val keysValuesIterator = nodes.keysValsList.iterator()

        var nodeId = 0L
        var latitude = 0L
        var longitude = 0L

        for (i in idList.indices) {
            nodeId += idList[i]
            latitude += latList[i]
            longitude += lonList[i]

            var tags: MutableMap<String, Any>? = null
            while (keysValuesIterator.hasNext()) {
                val keyIndex = keysValuesIterator.next()
                if (keyIndex == 0) {
                    break
                }
                if (CHECK_DATA && !keysValuesIterator.hasNext()) {
                    throw RuntimeException(
                            "The PBF DenseInfo keys/values list contains a key with no corresponding value.")
                }
                val valueIndex = keysValuesIterator.next()

                if (tags == null) {
                    tags = HashMap(Math.max(3, 2 * (nodes.keysValsList.size / 2) / idList.size))
                }
                tags[fieldDecoder.decodeString(keyIndex)] = fieldDecoder.decodeString(valueIndex)
            }

            val node = ReaderNode(nodeId,
                    fieldDecoder.decodeLatitude(latitude),
                    fieldDecoder.decodeLongitude(longitude))
            node.setTags(tags)
            decodedEntities.add(node)
        }
    }

    private fun processWays(ways: List<Osmformat.Way>, fieldDecoder: PbfFieldDecoder) {
        for (way in ways) {
            val tags = buildTags(way.keysList, way.valsList, fieldDecoder)
            val osmWay = ReaderWay(way.id)
            osmWay.setTags(tags)

            var nodeId = 0L
            val wayNodes: LongIndexedContainer = osmWay.nodes
            for (nodeIdOffset in way.refsList) {
                nodeId += nodeIdOffset
                wayNodes.add(nodeId)
            }

            decodedEntities.add(osmWay)
        }
    }

    private fun processRelations(relations: List<Osmformat.Relation>, fieldDecoder: PbfFieldDecoder) {
        for (relation in relations) {
            val tags = buildTags(relation.keysList, relation.valsList, fieldDecoder)

            val osmRelation = ReaderRelation(relation.id)
            osmRelation.setTags(tags)

            buildRelationMembers(osmRelation, relation.memidsList, relation.rolesSidList,
                    relation.typesList, fieldDecoder)

            decodedEntities.add(osmRelation)
        }
    }

    private fun buildRelationMembers(relation: ReaderRelation,
                                     memberIds: List<Long>, memberRoles: List<Int>,
                                     memberTypes: List<Osmformat.Relation.MemberType>,
                                     fieldDecoder: PbfFieldDecoder) {
        if (CHECK_DATA && (memberIds.size != memberRoles.size || memberIds.size != memberTypes.size)) {
            throw RuntimeException("Number of member ids (" + memberIds.size + "), member roles ("
                    + memberRoles.size + "), and member types (" + memberTypes.size + ") don't match")
        }

        val memberIdIterator = memberIds.iterator()
        val memberRoleIterator = memberRoles.iterator()
        val memberTypeIterator = memberTypes.iterator()

        var refId = 0L
        while (memberIdIterator.hasNext()) {
            val memberType = memberTypeIterator.next()
            refId += memberIdIterator.next()

            var entityType = ReaderElement.Type.NODE
            if (memberType == Osmformat.Relation.MemberType.WAY) {
                entityType = ReaderElement.Type.WAY
            } else if (memberType == Osmformat.Relation.MemberType.RELATION) {
                entityType = ReaderElement.Type.RELATION
            }

            val member = ReaderRelation.Member(entityType, refId,
                    fieldDecoder.decodeString(memberRoleIterator.next()))
            relation.add(member)
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(PbfBlobDecoder::class.java)
        private const val CHECK_DATA = false
    }
}
