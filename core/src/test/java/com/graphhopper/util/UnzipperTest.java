package com.graphhopper.util;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.ArrayList;
import java.util.List;

public class UnzipperTest {

    @Test
    public void testUnzip() throws Exception {
        String to = "./target/tmp/test";
        Helper.removeDir(new File(to));
        new Unzipper().unzip("./src/test/resources/com/graphhopper/util/test.zip", to, false);
        assertTrue(new File("./target/tmp/test/file2 bäh").exists());
        assertTrue(new File("./target/tmp/test/folder1").isDirectory());
        assertTrue(new File("./target/tmp/test/folder1/folder 3").isDirectory());
        Helper.removeDir(new File(to));
    }

    /**
     * Pins behavior discovered during the Kotlin conversion, verified against the pre-migration
     * java implementation. See docs/pinned-behavior.md.
     */
    @Test
    public void unzipperProgressAccumulatesAcrossEntries() throws Exception {
        // the progress listener reports the total number of (compression-scaled) bytes read so
        // far, accumulated over ALL entries (never reset per entry); directories and empty
        // files report nothing. test.zip: file1 (5 bytes), "file2 bäh" (5), "folder1/file 3"
        // (0), "folder1/folder 3/file4" (4), all stored (factor 1)
        String to = "./target/tmp/unzip-progress";
        Helper.removeDir(new File(to));
        List<Long> progress = new ArrayList<>();
        new Unzipper().unzip(getClass().getResourceAsStream("test.zip"), new File(to), progress::add);
        assertEquals(List.of(5L, 10L, 14L), progress);
        Helper.removeDir(new File(to));
    }
}
