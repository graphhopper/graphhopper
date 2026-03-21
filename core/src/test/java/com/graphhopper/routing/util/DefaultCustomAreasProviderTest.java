package com.graphhopper.routing.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultCustomAreasProviderTest {

    @Test
    void loadsAreasFromGeoJsonFilesInDirectory() throws URISyntaxException {
        Path directory = Paths.get(getClass().getResource("test-areas.geojson").toURI()).getParent();

        List<CustomArea> areas = new DefaultCustomAreasProvider(directory.toString()).loadAreas();

        assertEquals(1, areas.size());
        assertEquals("test-area", areas.get(0).getProperties().get("id"));
    }

    @Test
    void returnsEmptyListWhenDirectoryHasNoGeoJsonFiles(@TempDir Path tempDir) {
        List<CustomArea> areas = new DefaultCustomAreasProvider(tempDir.toString()).loadAreas();

        assertEquals(0, areas.size());
    }

    @Test
    void loadsAreasFromMultipleFiles(@TempDir Path tempDir) throws IOException, URISyntaxException {
        Path source = Paths.get(getClass().getResource("test-areas.geojson").toURI());
        Files.copy(source, tempDir.resolve("areas1.geojson"));
        Files.copy(source, tempDir.resolve("areas2.geojson"));

        List<CustomArea> areas = new DefaultCustomAreasProvider(tempDir.toString()).loadAreas();

        assertEquals(2, areas.size());
    }

    @Test
    void throwsWhenDirectoryDoesNotExist() {
        assertThrows(Exception.class,
                () -> new DefaultCustomAreasProvider("/nonexistent/path").loadAreas());
    }
}
