package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.util.PMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.graphhopper.routing.util.FlagEncoderFactory.AVOID_TOLL_ROADS;

public class SupportTollRoadsFlagEncoder extends CarFlagEncoder {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public SupportTollRoadsFlagEncoder(PMap properties) {
        super(properties);
    }

    @Override
    public long acceptWay(ReaderWay way) {
        if (way.hasTag("toll", String.valueOf(Boolean.TRUE)) || way.hasTag("toll", "yes") || way.hasTag("barrier", "toll_booth")) {
            return 0;
        }

        return super.acceptWay(way);
    }

    @Override
    public String toString() {
        return AVOID_TOLL_ROADS;
    }
}