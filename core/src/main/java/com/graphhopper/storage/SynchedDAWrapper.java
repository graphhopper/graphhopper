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
package com.graphhopper.storage;

/**
 * A simple wrapper to synchronize every DataAccess object.
 * <p>
 *
 * @author Peter Karich
 */
class SynchedDAWrapper implements DataAccess {
    private final DataAccess inner;
    private final DAType type;

    public SynchedDAWrapper(DataAccess inner) {
        this.inner = inner;
        this.type = new DAType(inner.getType(), true);
    }

    @Override
    public synchronized String getName() {
        return inner.getName();
    }

    @Override
    public synchronized void rename(String newName) {
        inner.rename(newName);
    }

    @Override
    public synchronized void setInt(long bytePos, int value) {
        inner.setInt(bytePos, value);
    }

    @Override
    public synchronized int getInt(long bytePos) {
        return inner.getInt(bytePos);
    }

    @Override
    public synchronized void setShort(long bytePos, short value) {
        inner.setShort(bytePos, value);
    }

    @Override
    public synchronized short getShort(long bytePos) {
        return inner.getShort(bytePos);
    }

    @Override
    public synchronized void setBytes(long bytePos, byte[] values, int length) {
        inner.setBytes(bytePos, values, length);
    }

    @Override
    public synchronized void getBytes(long bytePos, byte[] values, int length) {
        inner.getBytes(bytePos, values, length);
    }

    @Override
    public synchronized void setHeader(int bytePos, int value) {
        inner.setHeader(bytePos, value);
    }

    @Override
    public synchronized int getHeader(int bytePos) {
        return inner.getHeader(bytePos);
    }

    @Override
    public synchronized DataAccess create(long bytes) {
        return inner.create(bytes);
    }

    @Override
    public synchronized boolean ensureCapacity(long bytes) {
        return inner.ensureCapacity(bytes);
    }

    @Override
    public synchronized void trimTo(long bytes) {
        inner.trimTo(bytes);
    }

    @Override
    public synchronized DataAccess copyTo(DataAccess da) {
        return inner.copyTo(da);
    }

    @Override
    public synchronized DataAccess setSegmentSize(int bytes) {
        return inner.setSegmentSize(bytes);
    }

    @Override
    public synchronized int getSegmentSize() {
        return inner.getSegmentSize();
    }

    @Override
    public synchronized int getSegments() {
        return inner.getSegments();
    }

    @Override
    public synchronized boolean loadExisting() {
        return inner.loadExisting();
    }

    @Override
    public synchronized void flush() {
        inner.flush();
    }

    @Override
    public synchronized void close() {
        inner.close();
    }

    @Override
    public boolean isClosed() {
        return inner.isClosed();
    }

    @Override
    public synchronized long getCapacity() {
        return inner.getCapacity();
    }

    @Override
    public DAType getType() {
        return type;
    }
}
