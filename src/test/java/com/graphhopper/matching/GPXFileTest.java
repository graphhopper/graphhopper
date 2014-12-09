/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.graphhopper.matching;

import com.graphhopper.util.GPXEntry;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Peter Karich
 */
public class GPXFileTest
{
    @Test
    public void testDoImport()
    {
        GPXFile instance = new GPXFile();
        instance.doImport("./src/test/resources/test.gpx");
        List<GPXEntry> res = instance.getEntries();
        assertEquals(264, res.size());

        assertEquals(new GPXEntry(51.377719, 12.338217, 0), res.get(0));
        assertEquals(new GPXEntry(51.371482, 12.363795, 235000), res.get(50));
    }
}
