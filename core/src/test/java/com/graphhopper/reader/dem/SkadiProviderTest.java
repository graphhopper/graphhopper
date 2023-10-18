package com.graphhopper.reader.dem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SkadiProviderTest {
    SkadiProvider instance;

    @BeforeEach
    public void setUp() {
        instance = new SkadiProvider();
    }

    @AfterEach
    public void tearDown() {
        instance.release();
    }

    @Test
    public void testGetDownloadUrl() {
        assertEquals("N42/N42E011.hgt.gz", instance.getDownloadURL(42.940339, 11.953125));
        assertEquals("N38/N38W078.hgt.gz", instance.getDownloadURL(38.548165, -77.167969));
        assertEquals("N14/N14W005.hgt.gz", instance.getDownloadURL(14.116047, -4.277344));
        assertEquals("S52/S52W058.hgt.gz", instance.getDownloadURL(-51.015725, -57.621094));
        assertEquals("N24/N24E120.hgt.gz", instance.getDownloadURL(24.590108, 120.640625));
        assertEquals("S42/S42W063.hgt.gz", instance.getDownloadURL(-41.015725, -62.949219));
    }

    @Test
    public void testGetFileName() {
        assertEquals("n42e011", instance.getFileName(42.940339, 11.953125));
        assertEquals("n38w078", instance.getFileName(38.548165, -77.167969));
        assertEquals("n14w005", instance.getFileName(14.116047, -4.277344));
        assertEquals("s52w058", instance.getFileName(-51.015725, -57.621094));
        assertEquals("n24e120", instance.getFileName(24.590108, 120.640625));
        assertEquals("s42w063", instance.getFileName(-41.015725, -62.949219));
    }

}
