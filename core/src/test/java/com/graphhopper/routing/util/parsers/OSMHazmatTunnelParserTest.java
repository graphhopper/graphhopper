package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.*;
import com.graphhopper.storage.IntsRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class OSMHazmatTunnelParserTest {
    private EnumEncodedValue<HazmatTunnel> hazTunnelEnc;
    private OSMHazmatTunnelParser parser;
    private IntsRef relFlags;

    @BeforeEach
    public void setUp() {
        hazTunnelEnc = new EnumEncodedValue<>(HazmatTunnel.KEY, HazmatTunnel.class);
        hazTunnelEnc.init(new EncodedValue.InitializerConfig());
        parser = new OSMHazmatTunnelParser(hazTunnelEnc);
        relFlags = new IntsRef(2);
    }

    @Test
    public void testADRTunnelCat() {
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "A");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "B");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.B, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "C");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "D");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.D, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "E");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testTunnelCat() {
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "A");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "B");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.B, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "C");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "D");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.D, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "E");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testHazmatSubtags() {
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:A", "no");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:B", "no");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.B, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:C", "no");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:D", "no");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.D, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:E", "no");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testOrder() {
        IntAccess intAccess = new ArrayIntAccess(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:A", "no");
        readerWay.setTag("hazmat:B", "no");
        readerWay.setTag("hazmat:C", "no");
        readerWay.setTag("hazmat:D", "no");
        readerWay.setTag("hazmat:E", "no");
        int edgeId = 0;
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:A", "no");
        readerWay.setTag("hazmat:B", "no");
        readerWay.setTag("hazmat:C", "no");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, edgeId, intAccess));

        intAccess = new ArrayIntAccess(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:B", "no");
        readerWay.setTag("hazmat:E", "no");
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testIgnoreNonTunnelSubtags() {
        IntAccess intAccess = new ArrayIntAccess(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:B", "no");
        int edgeId = 0;
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, edgeId, intAccess));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntAccess intAccess = new ArrayIntAccess(1);
        int edgeId = 0;
        parser.handleWayTags(edgeId, intAccess, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, edgeId, intAccess));
    }
}