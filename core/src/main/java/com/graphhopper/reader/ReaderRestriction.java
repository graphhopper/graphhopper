package com.graphhopper.reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class ReaderRestriction {
    private List<Restriction> restrictions;
    private List<Long> ways;

    public ReaderRestriction(List<Long> ways) {
        if (ways.size() < 2) {
            throw new IllegalArgumentException("Restriction needs 2 or more ways");
        }
        this.restrictions = new ArrayList<Restriction>(1);
        this.ways = ways;
    }

    static class Restriction {
        private final Long from;
        private final Long via;
        private final Long to;

        public Restriction(Long from, Long via, Long to) {
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

    public void buildRestriction(HashMap<Long, Long[]> wayNodesMap) {
        for (int i = 0; i < ways.size() - 1; i++) {
            Long from = ways.get(i);
            Long to = ways.get(i + 1);
            Long via = getViaNode(wayNodesMap.get(from), wayNodesMap.get(to));
            restrictions.add(new Restriction(from, via, to));
        }
    }

    public Long getViaNode(Long[] fromNodes, Long[] toNodes) {
        if (Arrays.asList(toNodes).contains(fromNodes[0])) {
            return fromNodes[0];
        } else if (Arrays.asList(toNodes).contains(fromNodes[1])) {
            return fromNodes[1];
        } else {
            throw new IllegalStateException("No Via Node found!");
        }
    }

    public List<Restriction> getRestrictions() {
        if (restrictions.size() == 0)
            throw new IllegalStateException("Restriction has not yet been build");
        return restrictions;
    }

    public List<Long> getWays() {
        return ways;
    }
}
