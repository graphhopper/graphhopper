package com.graphhopper.reader;

import java.util.List;

public class ReaderBlock {
    
    private final List<ReaderElement> elements;

    public ReaderBlock(List<ReaderElement> elements) {
        this.elements = elements;
    }
    
    public List<ReaderElement> getElements() {
        return elements;
    }
}
