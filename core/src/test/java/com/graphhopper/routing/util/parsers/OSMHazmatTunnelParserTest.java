package com.graphhopper.routing.util.parsers;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.ev.EncodedValue;
import com.graphhopper.routing.ev.EnumEncodedValue;
import com.graphhopper.routing.ev.HazmatTunnel;
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
        IntsRef intsRef = new IntsRef(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "A");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "B");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.B, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "C");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "D");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.D, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:adr_tunnel_cat", "E");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, intsRef));
    }

    @Test
    public void testTunnelCat() {
        IntsRef intsRef = new IntsRef(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "A");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "B");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.B, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "C");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "D");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.D, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:tunnel_cat", "E");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, intsRef));
    }

    @Test
    public void testHazmatSubtags() {
        IntsRef intsRef = new IntsRef(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:A", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:B", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.B, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:C", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:D", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.D, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:E", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, intsRef));
    }

    @Test
    public void testOrder() {
        IntsRef intsRef = new IntsRef(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:A", "no");
        readerWay.setTag("hazmat:B", "no");
        readerWay.setTag("hazmat:C", "no");
        readerWay.setTag("hazmat:D", "no");
        readerWay.setTag("hazmat:E", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:A", "no");
        readerWay.setTag("hazmat:B", "no");
        readerWay.setTag("hazmat:C", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.C, hazTunnelEnc.getEnum(false, intsRef));

        intsRef = new IntsRef(1);
        readerWay = new ReaderWay(1);
        readerWay.setTag("tunnel", "yes");
        readerWay.setTag("hazmat:B", "no");
        readerWay.setTag("hazmat:E", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.E, hazTunnelEnc.getEnum(false, intsRef));
    }

    @Test
    public void testIgnoreNonTunnelSubtags() {
        IntsRef intsRef = new IntsRef(1);
        ReaderWay readerWay = new ReaderWay(1);
        readerWay.setTag("hazmat:B", "no");
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, intsRef));
    }

    @Test
    public void testNoNPE() {
        ReaderWay readerWay = new ReaderWay(1);
        IntsRef intsRef = new IntsRef(1);
        parser.handleWayTags(intsRef, readerWay, relFlags);
        assertEquals(HazmatTunnel.A, hazTunnelEnc.getEnum(false, intsRef));
    }
}