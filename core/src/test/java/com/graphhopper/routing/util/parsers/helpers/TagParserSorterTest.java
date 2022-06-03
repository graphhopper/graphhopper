package com.graphhopper.routing.util.parsers.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.parsers.TagParser;
import com.graphhopper.storage.IntsRef;

public class TagParserSorterTest {
    
    @Test
    public void testOnlyProviding() {
        TagParser a = create(new String[0], new String[] { "A" });
        TagParser b = create(new String[0], new String[] { "B" });
        TagParser c = create(new String[0], new String[] { "C" });
        
        List<TagParser> parsers = Arrays.asList(a, b, c);
        List<TagParser> sorted = new ArrayList<>(parsers);
        
        TagParserSorter sorter = new TagParserSorter(sorted);
        sorter.sort();
        
        assertEquals(parsers.size(), sorted.size());

        assertEquals(sorted.get(0), a);
        assertEquals(sorted.get(1), b);
        assertEquals(sorted.get(2), c);
    }
    
    @Test
    public void testChain() {
        TagParser a = create(new String[0], new String[] { "A" });
        TagParser b = create(new String[] { "A" }, new String[] { "B" });
        TagParser c = create(new String[] { "B" }, new String[] { "C" });
        
        List<TagParser> parsers = Arrays.asList(c, b, a);
        List<TagParser> sorted = new ArrayList<>(parsers);
        
        TagParserSorter sorter = new TagParserSorter(sorted);
        sorter.sort();
        
        assertEquals(parsers.size(), sorted.size());
        
        assertEquals(sorted.get(0), a);
        assertEquals(sorted.get(1), b);
        assertEquals(sorted.get(2), c);
    }
    
    @Test
    public void testComplex() {
        TagParser a = create(new String[0], new String[] { "A" });
        TagParser b = create(new String[0], new String[] { "B" });
        TagParser c = create(new String[0], new String[] { "C" });
        TagParser d = create(new String[] { "B", "C" }, new String[] { "D" });
        TagParser e = create(new String[] { "A", "B" }, new String[] { "E" });
        TagParser f = create(new String[] { "D" }, new String[] { "F" });
        TagParser g = create(new String[] { "D", "E" }, new String[] { "G" });
        TagParser h = create(new String[] { "A", "D" }, new String[] { "H" });
        
        List<TagParser> parsers = Arrays.asList(h, g, f, e, d, c, b, a);
        List<TagParser> sorted = new ArrayList<>(parsers);
        
        TagParserSorter sorter = new TagParserSorter(sorted);
        sorter.sort();
        
        assertEquals(parsers.size(), sorted.size());
        
        assertEquals(sorted.get(0), c);
        assertEquals(sorted.get(1), b);
        assertEquals(sorted.get(2), d);
        assertEquals(sorted.get(3), a);
        assertEquals(sorted.get(4), h);
        assertEquals(sorted.get(5), e);
        assertEquals(sorted.get(6), g);
        assertEquals(sorted.get(7), f);
    }
    
    @Test
    public void testUnresolved() {
        TagParser a = create(new String[0], new String[] { "A" });
        TagParser b = create(new String[0], new String[] { "B" });
        TagParser c = create(new String[] { "X" }, new String[] { "C" });
        
        List<TagParser> parsers = Arrays.asList(b, c, a);
        
        TagParserSorter sorter = new TagParserSorter(parsers);
        
        try {
            sorter.sort();
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
            assertTrue(e.getMessage().startsWith("Unable to satisfy dependency for [X]"));
        }
    }
    
    @Test
    public void testCycle() {
        TagParser a = create(new String[] { "C" }, new String[] { "A" });
        TagParser b = create(new String[] { "A" }, new String[] { "B" });
        TagParser c = create(new String[] { "B" }, new String[] { "C" });
        
        List<TagParser> parsers = Arrays.asList(b, c, a);
        
        TagParserSorter sorter = new TagParserSorter(parsers);
        
        try {
            sorter.sort();
            fail();
        } catch (Exception e) {
            assertTrue(e instanceof IllegalStateException);
            assertTrue(e.getMessage().startsWith("Dependency cycle for parser"));
        }
    }
    
    private TagParser create(String[] required, String[] provided) {
        return new TagParser() {
            
            @Override
            public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, IntsRef relationFlags) {
                throw new UnsupportedOperationException();
            }
            
            @Override
            public List<String> getProvidedEncodedValues() {
                return Arrays.asList(provided);
            }
            
            @Override
            public List<String> getRequiredEncodedValues() {
                return Arrays.asList(required);
            }
        };
    }
}
