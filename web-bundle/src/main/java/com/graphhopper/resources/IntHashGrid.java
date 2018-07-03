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

/* This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public License
 as published by the Free Software Foundation, either version 3 of
 the License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package com.graphhopper.resources;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.LineString;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.set.hash.TLongHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A spatial index using a 2D fast long hashtable from the Trove library.
 * This is a modified version of laurentg's original hashgrid class,
 * using primitive int values instead of Object lists, as well as some Java 8 functional syntax.
 * It is also intended to store fixed-precision integer geographic coordinates rather than double-precision.
 * 
 * Objects to index are placed in all grid bins touching the bounding envelope. We *do not store*
 * any bounding envelope for each object: we will therefore return false positives when querying,
 * and it's up to the caller to filter them out (with whatever knowledge it has on the location of the object).
 * 
 * Note: For performance reasons, write operations are not synchronized, synchronization must be handled by the caller.
 * Read-only operations are thread-safe though.
 * 
 * @author laurentg, abyrd
 */
public class IntHashGrid implements Serializable {

    public static final double FIXED_FACTOR = 1e7; // we could just reuse the constant from osm-lib Node.

    public static int floatingDegreesToFixed(double degrees) {
        return (int)(degrees * FIXED_FACTOR);
    }

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(IntHashGrid.class);

    /* Size of bin in X and Y direction, in coordinate units. */
    private final int xBinSize, yBinSize;

    /* The map of all bins. Please see visit() and xKey/yKey for details on the key. */
    private final TLongObjectMap<TIntList> bins;

    private int nBins = 0;

    private int nObjects = 0;

    private int nEntries = 0;

    public IntHashGrid(double binSizeDegrees) {
        yBinSize = floatingDegreesToFixed(binSizeDegrees);
        // FIXME Assuming about 45 degrees latitude for now, cos(45deg)
        xBinSize = (int)(yBinSize / 0.7);
        if (binSizeDegrees <= 0) {
            throw new IllegalStateException("bin size must be positive.");
        }
        // For 200m bins, 500x500 = 100x100km = 250000 bins
        bins = new TLongObjectHashMap<>();
    }

    /** Create a HashGrid with the default grid dimensions. */
    public IntHashGrid() {
        this(0.0018); // About 200m
    }


    // TODO check that the number of bins to is sane.
    public final void insert(Envelope envelope, final int item) {
        visit(envelope, true, (bin, mapKey) -> {
            /*
             * Note: here we can end-up having several time the same object in the same bin, if
             * the client insert multiple times the same object with different envelopes.
             * However we do filter duplicated when querying, so apart for memory/performance
             * reasons it should work. If this becomes a problem, we can use a set instead of a
             * list.
             */
            bin.add(item);
            nEntries++;
            return false;
        });
        nObjects++;
    }

    /**
     * Insert a linestring into the index. NB: the line string uses real-world float coordinates, not fixed coordinates.
     * This function keeps long and angular line strings from winding up in many unnecessary cells by inserting each segment
     * individually and splitting long segments into pieces.
     *
     * We could use a rasterization algorithm, but just splitting the line segments up into manageable pieces works as
     * well and is easier to follow, at the expense of slower insert performance (which so far doesn't seem to be a problem).
     */
    public final void insert(LineString geom, final int item) {
        Coordinate[] coord = geom.getCoordinates();
        final TLongSet keys = new TLongHashSet(coord.length * 8);
        for (int i = 0; i < coord.length - 1; i++) {
            // Cut the segment if longer than bin size to reduce the number of wrong bins
            double dX = coord[i].x - coord[i + 1].x;
            double dY = coord[i].y - coord[i + 1].y;
            int segments = (int) Math.max(Math.abs(dX) / xBinSize, Math.abs(dY) / yBinSize);

            if (segments > 1000 || segments < 0)
                LOG.warn("Huge number of segments ({}) for edge, or possible int overflow)", segments);

            segments = Math.max(segments, 1);
            double segFrac = 1D / segments;

            for (int s = 0; s < segments; s++) {
                // interpolate the coordinates
                Coordinate c0 = new Coordinate(
                        floatingDegreesToFixed(coord[i].x + dX * segFrac * s),
                        floatingDegreesToFixed(coord[i].y + dY * segFrac * s)
                );

                Coordinate c1 = new Coordinate(
                        floatingDegreesToFixed(coord[i].x + dX * segFrac * (s + 1)),
                        floatingDegreesToFixed(coord[i].y + dY * segFrac * (s + 1))
                );

                Envelope env = new Envelope(c0, c1);
                visit(env, true, (bin, mapKey) -> {
                    keys.add(mapKey);
                    return false;
                });
            }
        }
        keys.forEach(key -> {
            // Note: bins have been initialized in the previous visit
            bins.get(key).add(item);
            nEntries++;
            return true;
        });
        nObjects++;
    }

    public final TIntSet query(Envelope envelope) {
        final TIntSet ret = new TIntHashSet();
        visit(envelope, false, (bin, mapKey) -> {
            ret.addAll(bin);
            return false;
        });
        return ret;
    }

    public final boolean remove(Envelope envelope, final int item) {
        final AtomicInteger removedCount = new AtomicInteger();
        visit(envelope, false, (bin, mapKey) -> {
            boolean removed = bin.remove(item);
            if (removed) {
                nEntries--;
                removedCount.addAndGet(1);
            }
            return removed;
        });
        if (removedCount.get() > 0) {
            nObjects--;
            return true;
        } else {
            return false;
        }
    }

    /** Defines a callback that will be called on a range of bins. */
    private interface BinVisitor {
        /** @return true if something has been removed from the bin. */
        abstract boolean visit(TIntList bin, long mapKey);
    }

    /**
     * @param envelope The envelope within which all bins should be visited.
     * @param createIfEmpty Create a new bin if it does not exist.
     * @param binVisitor The callback to call for each visited bin.
     */
    private void visit(Envelope envelope, boolean createIfEmpty, final BinVisitor binVisitor) {
        Coordinate min = new Coordinate(envelope.getMinX(), envelope.getMinY());
        Coordinate max = new Coordinate(envelope.getMaxX(), envelope.getMaxY());
        long minXKey = Math.round(min.x / xBinSize);
        long maxXKey = Math.round(max.x / xBinSize);
        long minYKey = Math.round(min.y / yBinSize);
        long maxYKey = Math.round(max.y / yBinSize);
        // Check sanity before iterating
        long dx = (maxXKey - minXKey);
        long dy = (maxYKey - minYKey);
        if (dx * dy > 100000) {
            System.out.println("Visiting too many spatial index cells." + dx*dy);
            return;
        }
        for (long xKey = minXKey; xKey <= maxXKey; xKey++) {
            for (long yKey = minYKey; yKey <= maxYKey; yKey++) {
                /*
                 * For all known use, the average absolute value of x/y keys will be rather small
                 * compared to Integer.MAX_VALUE. We need to swap the two words (MSB and LSB) of
                 * xKey in order to have a well-behaving long hash, fitting in an int, because the
                 * default implementation is: hashInt = (int)(value ^ (value >>> 32));
                 */
                long mapKey = (yKey << 32) | ((xKey & 0xFFFF) << 16) | ((xKey >> 16) & 0xFFFF);
                TIntList bin = bins.get(mapKey);
                if (createIfEmpty && bin == null) {
                    bin = new TIntArrayList();
                    bins.put(mapKey, bin);
                    nBins++;
                }
                if (bin != null) {
                    boolean modified = binVisitor.visit(bin, mapKey);
                    if (modified && bin.isEmpty()) {
                        bins.remove(mapKey);
                        nBins--;
                    }
                }
            }
        }
    }

    public String toString() {
        return String
                .format("HashGridSpatialIndex %f x %f, %d bins allocated, %d objs, %d entries (avg %.2f entries/bin, %.2f entries/object)",
                        this.xBinSize, this.yBinSize, this.nBins, this.nObjects, this.nEntries,
                        this.nEntries * 1.0 / this.nBins, this.nEntries * 1.0 / this.nObjects);
    }
}
