package com.graphhopper.routing.profiles;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.LaneInfoDecoder;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.Lane;

import java.util.*;

public class LaneInfoTagParser extends TagParserFactory.AbstractTagParser implements LaneInfoDecoder {
    private final IntEncodedValue ev;
    private final Map<String, Integer> turnLaneMap;

    public LaneInfoTagParser(IntEncodedValue ev, Map<String, Integer> turnLaneMap) {
        super(ev);
        this.ev = ev;
        this.turnLaneMap = turnLaneMap;
    }

    @Override
    public void parse(IntsRef ints, ReaderWay way) {

        String tag = way.getTag("turn:lanes");
        String tagForward = way.getTag("turn:lanes:forward");
        Integer value;
        if (tag != null) {
            value = encodeLanes(tag);
        } else if (tagForward != null) {
            value = encodeLanes(tagForward);
        } else {
            value = TagParserFactory.Car.NONE_LANE_CODE;
        }
        ev.setInt(false, ints, value);
    }

    private Integer encodeLanes(String tag) {
        Integer encoded = 0;
        String[] laneTags = tag.split("\\|", -1);
        Collections.reverse(Arrays.asList(laneTags));
        for (int i = 0; i < laneTags.length; i++) {
            String laneTag = laneTags[i];
            Integer turnCode = turnLaneMap.get(laneTag);
            if (laneTag.contains(";")) {
                turnCode = encodeTurnLanesWithMultipleDirections(laneTag);
            }
            turnCode = turnCode == null ? TagParserFactory.Car.NONE_LANE_CODE : turnCode;
            encoded = encoded + (turnCode << (TagParserFactory.Car.LANE_MASK_SIZE * i));
            if (encoded < 0) {
                encoded = TagParserFactory.Car.NONE_LANE_CODE;
                break;
            }
        }
        return encoded;
    }

    private Integer encodeTurnLanesWithMultipleDirections(String laneTag) {
        Integer turnCode;
        boolean right = false;
        boolean through = false;
        boolean left = false;
        String[] singleDirections = laneTag.split(";");
        for (String direction : singleDirections) {
            Integer code = turnLaneMap.get(direction);
            if (code != null) {
                left = left || code <= TagParserFactory.Car.LEFT_LANE_CODE;
                right = right || code >= TagParserFactory.Car.RIGHT_LANE_CODE;
                through = through || code == TagParserFactory.Car.NONE_LANE_CODE || code == 7;
            }
        }
        if (left && !right && through) {
            turnCode = turnLaneMap.get("through;left");
        } else if (!left && right && through) {
            turnCode = turnLaneMap.get("through;right");
        } else if (left && right && !through) {
            turnCode = turnLaneMap.get("left;right");
        } else if (left && right && through) {
            turnCode = turnLaneMap.get("left;right;through");
        } else if (right) {
            turnCode = turnLaneMap.get("right");
        } else if (left) {
            turnCode = turnLaneMap.get("left");
        } else {
            turnCode = TagParserFactory.Car.NONE_LANE_CODE;
        }
        return turnCode;
    }

    @Override
    public ReaderWayFilter getReadWayFilter() {
        return new ReaderWayFilter() {
            @Override
            public boolean accept(ReaderWay way) {
                return way.hasTag("turn:lanes") || way.hasTag("turn:lanes:forward");
            }
        };
    }

    @Override
    public String decodeTurnLanes(long flags) {
        int mask = TagParserFactory.Car.LANE_MASK;
        int code = (int) flags;
        int lane;
        String lanes = "";
        while ((lane = (code & mask)) != 0) {
            String turnLaneString = getTurnLaneString(lane);
            String appendedLanes = "|" + lanes;
            lanes = turnLaneString + (lanes.isEmpty() ? "" : appendedLanes);
            mask = mask << TagParserFactory.Car.LANE_MASK_SIZE;
        }
        return lanes;
    }

    @Override
    public List<Lane> decodeTurnLanesToList(long flags) {
        int mask = TagParserFactory.Car.LANE_MASK;
        int code = (int) flags;
        int lane;
        int shifts = 0;
        List<Lane> lanes = new ArrayList<>();
        while ((lane = (code & mask)) != 0) {
            lane = lane >> TagParserFactory.Car.LANE_MASK_SIZE * shifts;
            String turnLaneString = getTurnLaneString(lane);
            lanes.add(new Lane(turnLaneString, lane));
            mask = mask << TagParserFactory.Car.LANE_MASK_SIZE;
            shifts++;
        }
        Collections.reverse(lanes);
        return lanes;
    }

    private String getTurnLaneString(int lane) {
        for (Map.Entry<String, Integer> entry : turnLaneMap.entrySet()) {
            if (lane == entry.getValue()) {
                return entry.getKey();
            }
        }
        return "none";
    }
}