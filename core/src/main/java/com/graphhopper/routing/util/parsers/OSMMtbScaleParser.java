package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;

import java.util.List;

public class OSMMtbScaleParser implements TagParser {

    private final EnumEncodedValue<MtbScale> mtbScaleEnc;

    public OSMMtbScaleParser() {
        this(new EnumEncodedValue<>(MtbScale.KEY, MtbScale.class));
    }

    public OSMMtbScaleParser(final EnumEncodedValue<MtbScale> mtbScaleEnc) {
        this.mtbScaleEnc = mtbScaleEnc;
    }

    @Override
    public void createEncodedValues(EncodedValueLookup lookup, List<EncodedValue> list) {
        list.add(mtbScaleEnc);
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay readerWay, boolean ferry, IntsRef relationFlags) {
        if (readerWay.hasTag("mtb:scale", "0"))
            mtbScaleEnc.setEnum(false, edgeFlags, MtbScale.S0);
        if (readerWay.hasTag("mtb:scale", "1"))
            mtbScaleEnc.setEnum(false, edgeFlags, MtbScale.S1);
        if (readerWay.hasTag("mtb:scale", "2"))
            mtbScaleEnc.setEnum(false, edgeFlags, MtbScale.S2);
        if (readerWay.hasTag("mtb:scale", "3"))
            mtbScaleEnc.setEnum(false, edgeFlags, MtbScale.S3);
        if (readerWay.hasTag("mtb:scale", "4"))
            mtbScaleEnc.setEnum(false, edgeFlags, MtbScale.S4);
        if (readerWay.hasTag("mtb:scale", "5"))
            mtbScaleEnc.setEnum(false, edgeFlags, MtbScale.S5);
        if (readerWay.hasTag("mtb:scale", "6"))
            mtbScaleEnc.setEnum(false, edgeFlags, MtbScale.S6);
        return edgeFlags;
    }
}
