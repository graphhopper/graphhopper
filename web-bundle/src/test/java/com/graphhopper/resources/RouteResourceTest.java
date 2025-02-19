package com.graphhopper.resources;

import com.graphhopper.util.PMap;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RouteResourceTest {

    @Nested
    class InitHintsTest {

        /**
         * Not a blacklisted_multiple key; behaves as normal.
         */
        @Test
        public void testSingleNonBlacklistedValue() {
            String blacklistedMultipleKey = "blacklisted_multiple";
            String otherKey = "other";
            String value = "value1";
            String defaultValue = "default";

            PMap hints = new PMap();

            MultivaluedMap<String, String> parameterMap = new MultivaluedStringMap();
            parameterMap.add(otherKey, value);

            List<String> blacklistedMultipleHints = new ArrayList<>();
            blacklistedMultipleHints.add(blacklistedMultipleKey);

            RouteResource.initHints(hints, parameterMap, blacklistedMultipleHints);

            assertEquals(hints.getString(otherKey, defaultValue), value);
        }

        /**
         * Not a blacklisted_multiple key; behaves as normal.
         */
        @Test
        public void testMultipleNonBlacklistedValue() {
            String blacklistedMultipleKey = "blacklisted_multiple";
            String otherKey = "other";
            String value1 = "value1";
            String value2 = "value2";
            String defaultValue = "default";

            PMap hints = new PMap();

            MultivaluedMap<String, String> parameterMap = new MultivaluedStringMap();
            parameterMap.add(otherKey, value1);
            parameterMap.add(otherKey, value2);

            List<String> blacklistedMultipleHints = new ArrayList<>();
            blacklistedMultipleHints.add(blacklistedMultipleKey);

            RouteResource.initHints(hints, parameterMap, blacklistedMultipleHints);

            assertEquals(hints.getString(otherKey, defaultValue), defaultValue);
        }

        /**
         * Blacklisted_multiple key but single value; behaves as normal.
         */
        @Test
        public void testSingleBlacklistedValue() {
            String blacklistedMultipleKey = "blacklisted_multiple";
            String value = "value1";
            String defaultValue = "default";

            PMap hints = new PMap();

            MultivaluedMap<String, String> parameterMap = new MultivaluedStringMap();
            parameterMap.add(blacklistedMultipleKey, value);

            List<String> blacklistedMultipleHints = new ArrayList<>();
            blacklistedMultipleHints.add(blacklistedMultipleKey);

            RouteResource.initHints(hints, parameterMap, blacklistedMultipleHints);

            assertEquals(hints.getString(blacklistedMultipleKey, defaultValue), value);
        }

        /**
         * Blacklisted_multiple key with multiple values; not allowed.
         */
        @Test
        public void testMultipleBlacklistedValue() {
            String blacklistedMultipleKey = "blacklisted_multiple";
            String value1 = "value1";
            String value2 = "value2";

            PMap hints = new PMap();

            MultivaluedMap<String, String> parameterMap = new MultivaluedStringMap();
            parameterMap.add(blacklistedMultipleKey, value1);
            parameterMap.add(blacklistedMultipleKey, value2);

            List<String> blacklistedMultipleHints = new ArrayList<>();
            blacklistedMultipleHints.add(blacklistedMultipleKey);

            assertThrows(WebApplicationException.class, () -> RouteResource.initHints(hints, parameterMap, blacklistedMultipleHints));
        }

    }

}
