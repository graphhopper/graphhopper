package com.graphhopper.storage.index;

import com.carrotsearch.hppc.IntArrayList;
import com.graphhopper.geohash.SpatialKeyAlgo;
import com.graphhopper.util.shapes.BBox;

import java.util.Arrays;

import static com.graphhopper.util.DistanceCalcEarth.C;
import static com.graphhopper.util.DistanceCalcEarth.DIST_EARTH;

public class IndexStructureInfo {
    private final int[] entries;
    private final byte[] shifts;
    private final PixelGridTraversal pixelGridTraversal;
    private final SpatialKeyAlgo keyAlgo;
    private final BBox bounds;
    private final int parts;

    public IndexStructureInfo(int[] entries, byte[] shifts, PixelGridTraversal pixelGridTraversal, SpatialKeyAlgo keyAlgo, BBox bounds, int parts) {
        this.entries = entries;
        this.shifts = shifts;
        this.pixelGridTraversal = pixelGridTraversal;
        this.keyAlgo = keyAlgo;
        this.bounds = bounds;
        this.parts = parts;
    }

    public static IndexStructureInfo create(BBox bounds, int minResolutionInMeter) {
        // I still need to be able to save and load an empty LocationIndex, and I can't when the extent
        // is zero.
        if (!bounds.isValid())
            bounds = new BBox(-10.0, 10.0, -10.0, 10.0);

        double lat = Math.min(Math.abs(bounds.maxLat), Math.abs(bounds.minLat));
        double maxDistInMeter = Math.max(
                (bounds.maxLat - bounds.minLat) / 360 * C,
                (bounds.maxLon - bounds.minLon) / 360 * DIST_EARTH.calcCircumference(lat));
        double tmp = maxDistInMeter / minResolutionInMeter;
        tmp = tmp * tmp;
        IntArrayList tmpEntries = new IntArrayList();
        // the last one is always 4 to reduce costs if only a single entry
        tmp /= 4;
        while (tmp > 1) {
            int tmpNo;
            if (tmp >= 16) {
                tmpNo = 16;
            } else if (tmp >= 4) {
                tmpNo = 4;
            } else {
                break;
            }
            tmpEntries.add(tmpNo);
            tmp /= tmpNo;
        }
        tmpEntries.add(4);
        int[] entries = tmpEntries.toArray();
        if (entries.length < 1) {
            // at least one depth should have been specified
            throw new IllegalStateException("depth needs to be at least 1");
        }
        int depth = entries.length;
        byte[] shifts = new byte[depth];
        int lastEntry = entries[0];
        for (int i1 = 0; i1 < depth; i1++) {
            if (lastEntry < entries[i1]) {
                throw new IllegalStateException("entries should decrease or stay but was:"
                        + Arrays.toString(entries));
            }
            lastEntry = entries[i1];
            shifts[i1] = getShift(entries[i1]);
        }
        int shiftSum = 0;
        long parts = 1;
        for (int i = 0; i < shifts.length; i++) {
            shiftSum += shifts[i];
            parts *= entries[i];
        }
        if (shiftSum > 64)
            throw new IllegalStateException("sum of all shifts does not fit into a long variable");
        parts = (int) Math.round(Math.sqrt(parts));

        return new IndexStructureInfo(entries, shifts, new PixelGridTraversal((int) parts, bounds), new SpatialKeyAlgo(shiftSum, bounds), bounds, (int) parts);
    }

    private static byte getShift(int entries) {
        byte b = (byte) Math.round(Math.log(entries) / Math.log(2));
        if (b <= 0)
            throw new IllegalStateException("invalid shift:" + b);

        return b;
    }

    public int[] getEntries() {
        return entries;
    }

    public byte[] getShifts() {
        return shifts;
    }

    public PixelGridTraversal getPixelGridTraversal() {
        return pixelGridTraversal;
    }

    public SpatialKeyAlgo getKeyAlgo() {
        return keyAlgo;
    }

    public BBox getBounds() {
        return bounds;
    }

    public int getParts() {
        return parts;
    }

    public double getDeltaLat() {
        return (bounds.maxLat - bounds.minLat) / parts;
    }

    public double getDeltaLon() {
        return (bounds.maxLon - bounds.minLon) / parts;
    }
}
