package com.graphhopper.reader;

public interface ITurnCostTableEntry {
	long getItemId();
	int getEdgeFrom();
	int getEdgeTo();
	int getVia();
	long getFlags();
	void setFlags(long flags);
}