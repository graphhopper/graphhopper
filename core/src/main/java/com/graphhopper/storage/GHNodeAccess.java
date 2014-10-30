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
package com.graphhopper.storage;

import com.graphhopper.util.Helper;

/**
 * A helper class for GraphHopperStorage for its node access.
 * <p>
 * @author Peter Karich
 */
class GHNodeAccess implements NodeAccess
{
    private final GraphHopperStorage that;
    private final boolean elevation;

    public GHNodeAccess( GraphHopperStorage that, boolean withElevation )
    {
        this.that = that;
        this.elevation = withElevation;
    }

    @Override
    public final void setNode( int nodeId, double lat, double lon )
    {
        setNode(nodeId, lat, lon, Double.NaN);
    }

    @Override
    public final void setNode( int index, double lat, double lon, double ele )
    {
        that.ensureNodeIndex(index);
        long tmp = (long) index * that.nodeEntryBytes;
        that.nodes.setInt(tmp + that.N_LAT, Helper.degreeToInt(lat));
        that.nodes.setInt(tmp + that.N_LON, Helper.degreeToInt(lon));

        if (is3D())
        {
            // meter precision is sufficient for now
            that.nodes.setInt(tmp + that.N_ELE, Helper.eleToInt(ele));
            if (ele > that.bounds.maxEle)
                that.bounds.maxEle = ele;

            if (ele < that.bounds.minEle)
                that.bounds.minEle = ele;
        }

        if (lat > that.bounds.maxLat)
            that.bounds.maxLat = lat;

        if (lat < that.bounds.minLat)
            that.bounds.minLat = lat;

        if (lon > that.bounds.maxLon)
            that.bounds.maxLon = lon;

        if (lon < that.bounds.minLon)
            that.bounds.minLon = lon;

        // set the default value for the additional field of this node
        if (that.extStorage.isRequireNodeField())
            that.nodes.setInt(tmp + that.N_ADDITIONAL, that.extStorage.getDefaultNodeFieldValue());
    }

    @Override
    public final double getLatitude( int nodeId )
    {
        return Helper.intToDegree(that.nodes.getInt((long) nodeId * that.nodeEntryBytes + that.N_LAT));
    }

    @Override
    public final double getLongitude( int nodeId )
    {
        return Helper.intToDegree(that.nodes.getInt((long) nodeId * that.nodeEntryBytes + that.N_LON));
    }

    @Override
    public final double getElevation( int nodeId )
    {
        if (!elevation)
            throw new IllegalStateException("Cannot access elevation - 3D is not enabled");

        return Helper.intToEle(that.nodes.getInt((long) nodeId * that.nodeEntryBytes + that.N_ELE));
    }

    @Override
    public final double getEle( int nodeId )
    {
        return getElevation(nodeId);
    }

    @Override
    public final double getLat( int nodeId )
    {
        return getLatitude(nodeId);
    }

    @Override
    public final double getLon( int nodeId )
    {
        return getLongitude(nodeId);
    }

    /**
     * @deprecated use graph.getExtendedStorageAccess() instead
     */
    @Deprecated 
    @Override
    public final void setAdditionalNodeField( String storageIdentifier, int index, int additionalValue )
    {
        if (that.extStorage.isRequireNodeField() && that.N_ADDITIONAL >= 0)
        {
            that.getExtendedStorageAccess().writeToExtendedNodeStorage(storageIdentifier, index, additionalValue);
        } else
        {
            throw new AssertionError("This graph does not provide an additional node field");
        }
    }

     /**
     * @deprecated use graph.getExtendedStorageAccess() instead
     */
    @Deprecated
    @Override
    public final int getAdditionalNodeField( String storageIdentifier, int index )
    {
        if (that.extStorage.isRequireNodeField() && that.N_ADDITIONAL >= 0)
            return that.getExtendedStorageAccess().readFromExtendedNodeStorage(storageIdentifier, index);
        else
            throw new AssertionError("This graph does not provide an additional node field");
    }

    @Override
    public final boolean is3D()
    {
        return elevation;
    }

    @Override
    public int getDimension()
    {
        if (elevation)
            return 3;
        return 2;
    }
}
