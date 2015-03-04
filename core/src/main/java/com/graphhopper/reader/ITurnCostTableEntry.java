package com.graphhopper.reader;

public interface ITurnCostTableEntry {
    long getItemId();
    int getEdgeFrom();
    int getEdgeTo();
    void setEdgeFrom(int from);
    void setEdgeTo(int to);
    int getVia();
    long getFlags();
    void setFlags(long flags);
}