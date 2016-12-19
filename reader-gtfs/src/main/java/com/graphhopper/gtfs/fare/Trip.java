package com.graphhopper.gtfs.fare;

import java.util.ArrayList;
import java.util.List;

public class Trip {

    static class Segment {

    }

    final List<Segment> segments = new ArrayList<>();

    Trip() {

    }

    public long duration() {
        return 6000;
    }

}
