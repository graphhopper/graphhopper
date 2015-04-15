package com.graphhopper.reader.osgb.hn;

import java.io.File;
import java.io.IOException;

import com.graphhopper.reader.RoutingElement;
import com.graphhopper.reader.osgb.AbstractOsInputFile;

public class OsHnInputFile extends AbstractOsInputFile<RoutingElement> {

    public OsHnInputFile(File file) throws IOException {
        super(file, new OsHnRoutingElementFactory());
    }

}
