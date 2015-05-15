package com.graphhopper.reader.osgb.dpn.potentialHazards;

import javax.xml.stream.XMLStreamException;

public class InvalidPotentialHazardException extends XMLStreamException {

    public InvalidPotentialHazardException(String msg) {
        super(msg);
    }
}
