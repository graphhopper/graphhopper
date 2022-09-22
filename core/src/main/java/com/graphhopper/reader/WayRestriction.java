package com.graphhopper.reader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class WayRestriction {
    private final Long id;
    private List<Long> ways;
    private List<NodeRestriction> restrictions;
    private Long startNode;

    public WayRestriction(Long id, List<Long> ways) {
        if (ways.size() < 2) {
            throw new IllegalArgumentException("Restriction needs 2 or more ways");
        }
        this.id = id;
        this.ways = ways;
        this.restrictions = new ArrayList<NodeRestriction>(2);
        this.startNode = -1L;
    }

    public void buildRestriction(HashMap<Long, ReaderWay> wayNodesMap) {
        for (int i = 0; i < ways.size() - 1; i++) {
            Long from = ways.get(i);
            Long to = ways.get(i + 1);
            Long via = getViaNode(wayNodesMap.get(from), wayNodesMap.get(to));
            restrictions.add(new NodeRestriction(from, via, to));
        }
        setStartNode(wayNodesMap);
    }

    public Long getViaNode(ReaderWay from, ReaderWay to) {
        Long[] fromNodes = from.getEndNodes();
        Long[] toNodes = to.getEndNodes();

        if (Arrays.asList(toNodes).contains(fromNodes[0])) {
            return fromNodes[0];
        } else if (Arrays.asList(toNodes).contains(fromNodes[1])) {
            return fromNodes[1];
        } else {
            throw new IllegalStateException("No Via Node found!");
        }
    }

    public List<NodeRestriction> getRestrictions() {
        if (restrictions.size() == 0)
            throw new IllegalStateException("Restriction has not yet been build");
        return restrictions;
    }

    public List<Long> getWays() {
        return ways;
    }

    private void setStartNode(HashMap<Long, ReaderWay> wayNodesMap) {
        Long via = restrictions.get(0).getVia();
        Long[] fromEndnodes = wayNodesMap.get(ways.get(0)).getEndNodes();
        if (fromEndnodes[0] == via) {
            startNode = fromEndnodes[1];
        } else {
            startNode = fromEndnodes[0];
        }
    }

    public Long getStartNode() {
        if (startNode < 0)
            throw new IllegalStateException("Restriction has not yet been build");
        return startNode;
    }

    public Long getId() {
        return id;
    }
}
