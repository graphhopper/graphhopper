package com.graphhopper.reader.gtfs;

import com.graphhopper.util.HeuristicCAPCompressor;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

class CompressedPatternHopEdge extends AbstractPatternHopEdge {

	private final List<FrequencyEntry> frequencies = new ArrayList<>();

	CompressedPatternHopEdge(SortedMap<Integer, Integer> departureTimeXTravelTime) {
		super();
		departureTimeXTravelTime.entrySet().stream().collect(Collectors.groupingBy(e -> e.getValue())).forEach( (travelTime, departures) -> {
			List<Integer> departuresForOneTravelTime = departures.stream().map(e -> e.getKey()).collect(Collectors.toList());
			List<HeuristicCAPCompressor.ArithmeticProgression> compress = HeuristicCAPCompressor.compress(departuresForOneTravelTime);
			for (HeuristicCAPCompressor.ArithmeticProgression ap : compress) {
				FrequencyEntry frequencyEntry = new FrequencyEntry();
				frequencyEntry.ap = ap;
				frequencyEntry.travelTime = travelTime;
				frequencies.add(frequencyEntry);
			}
		});
		Collections.shuffle(frequencies);
	}

	@Override
	double nextTravelTimeIncludingWaitTime(double earliestStartTime) {
		double result = Double.POSITIVE_INFINITY;
		for (FrequencyEntry frequency : frequencies) {
			double cost = frequency.ap.distanceToNextValue(earliestStartTime) + frequency.travelTime;
			if (cost < result) {
				result = cost;
			}
		}
		return result;
	}

	private static class FrequencyEntry implements Serializable {
		public HeuristicCAPCompressor.ArithmeticProgression ap;
		public Integer travelTime;
	}
}
