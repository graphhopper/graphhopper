package com.graphhopper.reader;

public class NodeRestriction {
    private final Long from;
    private final Long via;
    private final Long to;

    public NodeRestriction(Long from, Long via, Long to) {
        this.from = from;
        this.via = via;
        this.to = to;
    }

    public Long getFrom() {
        return from;
    }

    public Long getVia() {
        return via;
    }

    public Long getTo() {
        return to;
    }

    @Override
    public String toString() {
        return "*-(" + from + ")->" + via + "-(" + to + ")->*";
    }
}
