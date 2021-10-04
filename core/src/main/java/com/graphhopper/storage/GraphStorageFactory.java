package com.graphhopper.storage;

import com.graphhopper.GraphHopper;

// ORS-GH MOD - Modification by Maxim Rylov: Added a new class.
public interface GraphStorageFactory {

	public GraphHopperStorage createStorage(GHDirectory dir, GraphHopper gh); 
}
