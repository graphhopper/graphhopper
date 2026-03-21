package com.graphhopper.routing.util;

import com.graphhopper.routing.util.CustomArea;

import java.util.List;

public interface CustomAreasProvider {
    List<CustomArea> loadAreas();
}
