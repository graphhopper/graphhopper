package com.graphhopper.routing.matrix;

import com.graphhopper.storage.index.Snap;

import java.util.List;

public interface MatrixCalculator {
    DistanceMatrix calcMatrix(List<Snap> origins, List<Snap> destinations);

    String getDebugString();

    int getVisitedNodes();
}
