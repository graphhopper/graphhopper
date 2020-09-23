package com.graphhopper.tools;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphhopper.routing.util.parsers.helpers.OSMValueExtractor;

public class TagInfoUtil {
    
    private static final String URL_TEMPLATE = "https://taginfo.openstreetmap.org/api/4/key/values?"
                    + "filter=all&sortname=count&sortorder=desc&qtype=value&format=json&key=";
    private static final Extractor TONS_EXTRACTOR  = OSMValueExtractor::stringToTons;
    private static final Extractor METER_EXTRACTOR = OSMValueExtractor::stringToMeter;
    private static final Extractor KMH_EXTRACTOR = OSMValueExtractor::stringToKmh;

    public static void main(String[] args) throws IOException {
        Map<String, Extractor> keyMap = new LinkedHashMap<>();
        keyMap.put("maxweight",   TONS_EXTRACTOR);
        keyMap.put("maxaxleload", TONS_EXTRACTOR);
        keyMap.put("maxwidth",  METER_EXTRACTOR);
        keyMap.put("maxheight", METER_EXTRACTOR);
        keyMap.put("maxlength", METER_EXTRACTOR);
        keyMap.put("maxspeed",  KMH_EXTRACTOR);
        
        for (Entry<String, Extractor> entry: keyMap.entrySet()) {
            String key = entry.getKey();
            Extractor extractor = entry.getValue();
            analyzeTags(key, extractor);
        }
    }
    
    private static void analyzeTags(String key, Extractor extractor) throws IOException {
        System.out.println("Tag: " + key);
        Map<String, Tag> parsedMap = new LinkedHashMap<>();
        List<Tag> failed = new ArrayList<>();
        int count = 0;
        for (Tag tag : loadTags(key)) {
            count += tag.getCount();
            double val = extractor.extract(tag.getValue());
            if (Double.isNaN(val)) {
                failed.add(tag);
                continue;
            }
            
            System.out.println("\"" + tag.getValue() + "\" -> " + val);
            
            String normalized = Double.toString(val);
            if (parsedMap.containsKey(normalized)) {
                Tag existing = parsedMap.get(normalized);
                existing.setCount(existing.getCount() + tag.getCount());
            } else {
                parsedMap.put(normalized, new Tag(normalized, tag.getCount()));
            }
        }

        for (Tag tag : failed) {
            System.out.println("Unable to parse \"" + tag.getValue() + "\" (" + tag.getCount() + " occurrences)");
        }
        
        int parsedCount = parsedMap.values().stream().mapToInt(Tag::getCount).sum();
        double percentage = parsedCount / (double) count * 100;
        System.out.println("Success rate: " + percentage + "%");
    }

    private static List<Tag> loadTags(String key) throws IOException {
        JsonNode node;
        try (InputStream in = new URL(URL_TEMPLATE + key).openStream(); BufferedInputStream bufferedIn = new BufferedInputStream(in)) {
            node = new ObjectMapper().readTree(bufferedIn);
        }
        
        List<Tag> tags = new ArrayList<>();
        Iterator<JsonNode> iter = node.path("data").elements();
        while (iter.hasNext()) {
            JsonNode tagElement = iter.next();
            String value = tagElement.path("value").asText();
            int count = tagElement.path("count").asInt();
            tags.add(new Tag(value, count));
        }
        
        return tags;
    }
    
    private interface Extractor {
        double extract(String value);
    }
    
    private static class Tag {
        private final String value;
        private int count;
        
        public Tag(String value, int count) {
            this.value = value;
            this.count = count;
        }

        public String getValue() {
            return value;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Tag [value=");
            builder.append(value);
            builder.append(", count=");
            builder.append(count);
            builder.append("]");
            return builder.toString();
        }        
    }
}
