package com.graphhopper.routing.ch;

public interface NodeOrderingProvider {
    int getNodeIdForLevel(int level);

    int getNumNodes();
}
