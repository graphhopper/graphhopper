package com.graphhopper.routing.ch;

public interface NodeOrderingProvider {

    static NodeOrderingProvider identity(int nodes) {
        return new NodeOrderingProvider() {
            @Override
            public int getNodeIdForLevel(int level) {
                return level;
            }

            @Override
            public int getNumNodes() {
                return nodes;
            }
        };
    }

    static NodeOrderingProvider fromArray(int... nodes) {
        return new NodeOrderingProvider() {
            @Override
            public int getNodeIdForLevel(int level) {
                return nodes[level];
            }

            @Override
            public int getNumNodes() {
                return nodes.length;
            }
        };
    }

    int getNodeIdForLevel(int level);

    int getNumNodes();
}
