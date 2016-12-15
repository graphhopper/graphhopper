package com.graphhopper.reader.gtfs;

import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.QueryResult;

class EmptyLocationIndex implements LocationIndex {
    @Override
    public LocationIndex setResolution(int resolution) {
        return this;
    }

    @Override
    public LocationIndex prepareIndex() {
        return this;
    }

    @Override
    public int findID(double lat, double lon) {
        return 0;
    }

    @Override
    public QueryResult findClosest(double lat, double lon, EdgeFilter edgeFilter) {
        return new QueryResult(lat, lon);
    }

    @Override
    public LocationIndex setApproximation(boolean approxDist) {
        return this;
    }

    @Override
    public void setSegmentSize(int bytes) {

    }

    @Override
    public boolean loadExisting() {
        return false;
    }

    @Override
    public LocationIndex create(long byteCount) {
        return this;
    }

    @Override
    public void flush() {

    }

    @Override
    public void close() {

    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public long getCapacity() {
        return 0;
    }
}
