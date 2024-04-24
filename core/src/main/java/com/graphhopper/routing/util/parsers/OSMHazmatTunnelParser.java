package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EdgeBytesAccess;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HazmatTunnel;
import com.graphhopper.storage.BytesRef;

public class OSMHazmatTunnelParser implements TagParser {

    private static final String[] TUNNEL_CATEGORY_NAMES;

    static {
        HazmatTunnel[] categories = HazmatTunnel.values();
        TUNNEL_CATEGORY_NAMES = new String[categories.length];
        for (int i = 0; i < categories.length; i++) {
            TUNNEL_CATEGORY_NAMES[i] = categories[i].name();
        }
    }

    private final EnumEncodedValue<HazmatTunnel> hazTunnelEnc;

    public OSMHazmatTunnelParser(EnumEncodedValue<HazmatTunnel> hazTunnelEnc) {
        this.hazTunnelEnc = hazTunnelEnc;
    }

    @Override
    public void handleWayTags(int edgeId, EdgeBytesAccess edgeAccess, ReaderWay readerWay, BytesRef relationFlags) {
        if (readerWay.hasTag("hazmat:adr_tunnel_cat", TUNNEL_CATEGORY_NAMES)) {
            HazmatTunnel code = HazmatTunnel.valueOf(readerWay.getTag("hazmat:adr_tunnel_cat"));
            hazTunnelEnc.setEnum(false, edgeId, edgeAccess, code);
        } else if (readerWay.hasTag("hazmat:tunnel_cat", TUNNEL_CATEGORY_NAMES)) {
            HazmatTunnel code = HazmatTunnel.valueOf(readerWay.getTag("hazmat:tunnel_cat"));
            hazTunnelEnc.setEnum(false, edgeId, edgeAccess, code);
        } else if (readerWay.hasTag("tunnel", "yes")) {
            HazmatTunnel[] codes = HazmatTunnel.values();
            for (int i = codes.length - 1; i >= 0; i--) {
                if (readerWay.hasTag("hazmat:" + codes[i].name(), "no")) {
                    hazTunnelEnc.setEnum(false, edgeId, edgeAccess, codes[i]);
                    break;
                }
            }
        }
    }
}
