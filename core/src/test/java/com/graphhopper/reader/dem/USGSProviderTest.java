package com.graphhopper.reader.dem;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class USGSProviderTest {

    USGSProvider provider = new USGSProvider("");

    @Test
    public void testMinLat() {
        assertEquals(37.75, provider.getMinLatForTile(37.76));
        assertEquals(37.75, provider.getMinLatForTile(37.75));
        assertEquals(37.5, provider.getMinLatForTile(37.74));

        assertEquals(-38, provider.getMinLatForTile(-37.76));
        assertEquals(-37.75, provider.getMinLatForTile(-37.75));
        assertEquals(-37.75, provider.getMinLatForTile(-37.74));
    }

    @Test
    public void testMinLon() {
        assertEquals(122.25, provider.getMinLonForTile(122.26));
        assertEquals(122.25, provider.getMinLonForTile(122.25));
        assertEquals(122, provider.getMinLonForTile(122.24));

        assertEquals(-122.5, provider.getMinLonForTile(-122.26));
        assertEquals(-122.25, provider.getMinLonForTile(-122.25));
        assertEquals(-122.25, provider.getMinLonForTile(-122.24));
    }

    @Test
    public void testFilename() {
        assertEquals("ned19_n38x00_w122x50",
                provider.getFileName(38, -122.5));
        assertEquals("ned19_n38x00_w122x50",
                provider.getFileName(37.76, -122.26));
    }
}
