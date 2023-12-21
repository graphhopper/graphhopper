package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HazmatTunnel;
import com.graphhopper.routing.ev.EdgeIntAccess;
import com.graphhopper.storage.IntsRef;

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
    public void handleWayTags(int edgeId, EdgeIntAccess edgeIntAccess, ReaderWay readerWay, IntsRef relationFlags) {
        if (readerWay.hasTag("hazmat:adr_tunnel_cat", TUNNEL_CATEGORY_NAMES)) {
            HazmatTunnel code = HazmatTunnel.valueOf(readerWay.getTag("hazmat:adr_tunnel_cat"));
            hazTunnelEnc.setEnum(false, edgeId, edgeIntAccess, code);
        } else if (readerWay.hasTag("hazmat:tunnel_cat", TUNNEL_CATEGORY_NAMES)) {
            HazmatTunnel code = HazmatTunnel.valueOf(readerWay.getTag("hazmat:tunnel_cat"));
            hazTunnelEnc.setEnum(false, edgeId, edgeIntAccess, code);
        } else if (readerWay.hasTag("tunnel", "yes")) {
            HazmatTunnel[] codes = HazmatTunnel.values();
            for (int i = codes.length - 1; i >= 0; i--) {
                if (readerWay.hasTag("hazmat:" + codes[i].name(), "no")) {
                    hazTunnelEnc.setEnum(false, edgeId, edgeIntAccess, codes[i]);
                    break;
                }
            }
        }
    }

    @Override
    public String getName() {
        return hazTunnelEnc.getName();
    }
}
