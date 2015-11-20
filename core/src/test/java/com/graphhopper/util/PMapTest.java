package com.graphhopper.util;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PMapTest
{

    @Test
    public void singleStringPropertyCanBeRetrieved()
    {
        PMap subject = new PMap("foo=bar");

        Assert.assertEquals("bar", subject.get("foo"));
    }

    @Test
    public void propertyFromStringWithMultiplePropertiesCanBeRetrieved()
    {
        PMap subject = new PMap("foo=valueA|bar=valueB");

        Assert.assertEquals("valueA", subject.get("foo", ""));
        Assert.assertEquals("valueB", subject.get("bar", ""));
    }

    @Test
    public void keyCanHaveAnyCasing()
    {
        PMap subject = new PMap("foo=valueA|bar=valueB");

        assertEquals("valueA", subject.get("foo", ""));
        assertEquals("valueA", subject.get("FOO", ""));
        assertEquals("valueA", subject.get("Foo", ""));
    }

    @Test
    public void numericPropertyCanBeRetrievedAsLong()
    {
        PMap subject = new PMap("foo=1234|bar=5678");

        assertEquals(1234L, subject.getLong("foo", 0));
    }

    @Test
    public void numericPropertyCanBeRetrievedAsDouble()
    {
        PMap subject = new PMap("foo=123.45|bar=56.78");

        assertEquals(123.45, subject.getDouble("foo", 0), 1e-4);
    }

    @Test
    public void hasReturnsCorrectResult()
    {
        PMap subject = new PMap("foo=123.45|bar=56.78");

        assertTrue(subject.has("foo"));
        assertTrue(subject.has("bar"));
        assertFalse(subject.has("baz"));
    }

}