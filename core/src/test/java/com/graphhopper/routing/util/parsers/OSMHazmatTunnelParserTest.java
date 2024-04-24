package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.BytesRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMHazmatTunnelParserTest {
    private EnumEncodedValue<HazmatTunnel> hazTunnelEnc;
    private OSMHazmatTunnelParser parser;
    private BytesRef relFlags;

    @BeforeEach
    public void setUp() {
        hazTunnelEnc = HazmatTunnel.create();
        hazTunnelEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMHazmatTunnelParser(hazTunnelEnc);
        relFlags = new BytesRef(8);
    }

    @Test
    public void testADRTunnelCat() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "A");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "B");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.B, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "C");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "D");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.D, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "E");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testTunnelCat() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "A");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "B");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.B, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "C");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "D");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.D, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "E");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testHazmatSubtags() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:A", "no");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:B", "no");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.B, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:C", "no");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:D", "no");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.D, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:E", "no");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testOrder() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:A", "no");
        readerWay.setTag("hazmat:B", "no");
        readerWay.setTag("hazmat:C", "no");
        readerWay.setTag("hazmat:D", "no");
        readerWay.setTag("hazmat:E", "no");
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:A", "no");
        readerWay.setTag("hazmat:B", "no");
        readerWay.setTag("hazmat:C", "no");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));

        edgeAccess = new EdgeBytesAccessArray(4);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:B", "no");
        readerWay.setTag("hazmat:E", "no");
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testIgnoreNonTunnelSubtags() {
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:B", "no");
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        EdgeBytesAccess edgeAccess = new EdgeBytesAccessArray(4);
        int edgeId = 0;
        parser.handleWayTags(edgeId, edgeAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, edgeId, edgeAccess));
    }
}
