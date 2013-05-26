/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.reader;

import com.graphhopper.routing.util.AcceptWay;
import com.graphhopper.storage.Graph;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.PointList;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Peter Karich
 */
public abstract class OSMReaderHelper {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected long zeroCounter = 0;
    protected final Graph g;
    protected final long expectedNodes;
    private DistanceCalc distCalc = new DistanceCalc();
    private AcceptWay acceptWay;
    protected TLongArrayList wayNodes = new TLongArrayList(10);
    private Map<String, String> osmProperties = new HashMap<String, String>();
    private Map<String, Object> outProperties = new HashMap<String, Object>();
    private DouglasPeucker dpAlgo = new DouglasPeucker();

    public OSMReaderHelper(Graph g, long expectedNodes) {
        this.g = g;
        this.expectedNodes = expectedNodes;
    }

    public OSMReaderHelper wayPointMaxDistance(double maxDist) {
        dpAlgo.maxDistance(maxDist);
        return this;
    }

    public OSMReaderHelper acceptWay(AcceptWay acceptWay) {
        this.acceptWay = acceptWay;
        return this;
    }

    public AcceptWay acceptWay() {
        return acceptWay;
    }

    /**
     * @return inclusive pillar nodes (either via pre-parsing or via
     * expectedNodes)
     */
    public long foundNodes() {
        return expectedNodes;
    }

    public long edgeCount() {
        return g.getAllEdges().maxId();
    }

    public void preProcess(InputStream osmXml) {
    }

    public abstract boolean addNode(long osmId, double lat, double lon);

    public abstract int addEdge(TLongList nodes, int flags);

    int addEdge(int fromIndex, int toIndex, PointList pointList, int flags) {
        if (fromIndex < 0 || toIndex < 0)
            throw new AssertionError("to or from index is invalid for this edge "
                    + fromIndex + "->" + toIndex + ", points:" + pointList);

        double towerNodeDistance = 0;
        double prevLat = pointList.latitude(0);
        double prevLon = pointList.longitude(0);
        double lat;
        double lon;
        PointList pillarNodes = new PointList(pointList.size() - 2);
        int nodes = pointList.size();
        for (int i = 1; i < nodes; i++) {
            lat = pointList.latitude(i);
            lon = pointList.longitude(i);
            towerNodeDistance += distCalc.calcDist(prevLat, prevLon, lat, lon);
            prevLat = lat;
            prevLon = lon;
            if (nodes > 2 && i < nodes - 1)
                pillarNodes.add(lat, lon);
        }
        if (towerNodeDistance == 0) {
            // As investigation shows often two paths should have crossed via one identical point 
            // but end up in two very close points.
            zeroCounter++;
            towerNodeDistance = 0.0001;
        }

        EdgeIterator iter = g.edge(fromIndex, toIndex, towerNodeDistance, flags);
        if (nodes > 2) {
            dpAlgo.simplify(pillarNodes);
            iter.wayGeometry(pillarNodes);
        }
        return nodes;
    }

    String getInfo() {
        return "Found " + zeroCounter + " zero distances.";
    }

    void finishedReading() {
    }

    void startWayProcessing() {
    }

    public void processWay(XMLStreamReader sReader) throws XMLStreamException {
        boolean valid = parseWay(sReader);
        if (valid) {
            int flags = acceptWay.toFlags(outProperties);
            addEdge(wayNodes, flags);
        }
    }

    /**
     * Filter ways but do not anayze properties
     * wayNodes will be filled with participating node ids.
     *
     * @return true the current xml entry is a way entry and has nodes
     */
    boolean filterWay(XMLStreamReader sReader) throws XMLStreamException {

        readWayAttributes( sReader );

        boolean isWay = acceptWay.accept(osmProperties);
        boolean hasNodes = wayNodes.size() > 1;
        return isWay && hasNodes;
    }

    /**
     * wayNodes will be filled with participating node ids. outProperties will
     * be filled with way information after calling this method.
     *
     * @return true the current xml entry is a way entry and has nodes
     */
    boolean parseWay(XMLStreamReader sReader) throws XMLStreamException {
        outProperties.clear();

        readWayAttributes( sReader );

        boolean isWay = acceptWay.handleTags( outProperties, osmProperties, wayNodes );
        boolean hasNodes = wayNodes.size() > 1;
        return isWay && hasNodes;
    }

    private void readWayAttributes( XMLStreamReader sReader ) throws XMLStreamException
    {
        wayNodes.clear();
        osmProperties.clear();
        for (int tmpE = sReader.nextTag(); tmpE != XMLStreamConstants.END_ELEMENT;
                tmpE = sReader.nextTag()) {
            if (tmpE == XMLStreamConstants.START_ELEMENT) {
                if ("nd".equals(sReader.getLocalName())) {
                    String ref = sReader.getAttributeValue(null, "ref");
                    try {
                        wayNodes.add(Long.parseLong(ref));
                    } catch (Exception ex) {
                        logger.error("cannot get ref from way. ref:" + ref, ex);
                    }
                } else if ("tag".equals(sReader.getLocalName())) {
                    String tagKey = sReader.getAttributeValue(null, "k");
                    // check for null values is included in Helper.isEmpty
                    if ( !Helper.isEmpty( tagKey )) {
                        String tagValue = sReader.getAttributeValue(null, "v");
                        osmProperties.put(tagKey, tagValue);
                    }
                }
                sReader.next();
            }
        }
    }
}
