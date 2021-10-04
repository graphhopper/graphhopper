// ORS-GH MOD START - new class
// TODO ORS: Why do we need this? How does GH deal with it?
package com.graphhopper.routing.weighting;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.PMap;

public interface WeightingFactory {
	
	public Weighting createWeighting(PMap hintsMap, FlagEncoder encoder, GraphHopperStorage graphStorage);
}
// ORS_GH MOD END