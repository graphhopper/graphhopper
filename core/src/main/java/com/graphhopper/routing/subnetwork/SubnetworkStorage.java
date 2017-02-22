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
package com.graphhopper.routing.subnetwork;

import com.graphhopper.storage.DAType;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Storable;

/**
 * This class handles storage of subnetwork ids for every node. Useful to pick the correct set of
 * landmarks or fail fast for routing when two nodes are from different subnetworks.
 *
 * @author Peter Karich
 */
public class SubnetworkStorage implements Storable<SubnetworkStorage> {
    private final DataAccess da;

    public SubnetworkStorage(Directory dir, String postfix) {
        DAType type = dir.getDefaultType();
        da = dir.find("subnetwork_" + postfix, type.isMMap() ? DAType.MMAP : (type.isStoring() ? DAType.RAM_STORE : DAType.RAM));
    }

    /**
     * Returns the subnetwork ID for the specified nodeId or 0 if non is associated e.g. because the
     * subnetwork is too small.
     */
    public int getSubnetwork(int nodeId) {
        byte[] bytes = new byte[1];
        da.getBytes(nodeId, bytes, bytes.length);

        return (int) bytes[0];
    }

    /**
     * This method sets the subnetwork if of the specified nodeId. Default is 0 and means subnetwork
     * was too small to be useful to be stored.
     */
    public void setSubnetwork(int nodeId, int subnetwork) {
        if (subnetwork > 127)
            throw new IllegalArgumentException("Number of subnetworks is currently limited to 127 but requested " + subnetwork);

        byte[] bytes = new byte[1];
        bytes[0] = (byte) subnetwork;
        da.setBytes(nodeId, bytes, bytes.length);
    }

    @Override

    public boolean loadExisting() {
        return da.loadExisting();
    }

    @Override
    public SubnetworkStorage create(long byteCount) {
        da.create(2000);
        da.ensureCapacity(byteCount);
        return this;
    }

    @Override
    public void flush() {
        da.flush();
    }

    @Override
    public void close() {
        da.close();
    }

    @Override
    public boolean isClosed() {
        return da.isClosed();
    }

    @Override
    public long getCapacity() {
        return da.getCapacity();
    }
}
