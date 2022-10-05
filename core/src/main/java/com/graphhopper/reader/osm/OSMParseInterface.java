package com.graphhopper.reader.osm;

import java.text.ParseException;

import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;

public interface OSMParseInterface {
    default void handleElement(ReaderElement elem) throws ParseException {
        switch (elem.getType()) {
            case NODE:
                handleNode((ReaderNode) elem);
                break;
            case WAY:
                handleWay((ReaderWay) elem);
                break;
            case RELATION:
                handleRelation((ReaderRelation) elem);
                break;
            case FILEHEADER:
                handleFileHeader((OSMFileHeader) elem);
                break;
            default:
                throw new IllegalStateException("Unknown reader element type: " + elem.getType());
        }
    }

    default void handleNode(ReaderNode node) {
    }

    default void handleWay(ReaderWay way) {
    }

    default void handleRelation(ReaderRelation relation) {
    }

    default void handleFileHeader(OSMFileHeader fileHeader) throws ParseException {
    }

    default void onFinish() {
    }
}