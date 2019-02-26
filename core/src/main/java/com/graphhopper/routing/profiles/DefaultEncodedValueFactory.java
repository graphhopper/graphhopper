package com.graphhopper.routing.profiles;

import com.graphhopper.util.Helper;

public class DefaultEncodedValueFactory implements EncodedValueFactory {
    @Override
    public EncodedValue create(String string) {
        if (Helper.isEmpty(string))
            throw new IllegalArgumentException("No string provided to load EncodedValue");

        final EncodedValue enc;
        String name = string.split("\\|")[0];
        if (name.isEmpty())
            throw new IllegalArgumentException("To load EncodedValue name is required. " + string);

        if (Roundabout.KEY.equals(name)) {
            enc = Roundabout.create();
        } else if (RoadClassLink.KEY.equals(name)) {
            enc = RoadClassLink.create();
        } else if (RoadClass.KEY.equals(name)) {
            enc = RoadClass.create();
        } else if (RoadEnvironment.KEY.equals(name)) {
            enc = RoadEnvironment.create();
        } else if (RoadAccess.KEY.equals(name)) {
            enc = RoadAccess.create();
        } else if (CarMaxSpeed.KEY.equals(name)) {
            enc = CarMaxSpeed.create();
        } else if (Surface.KEY.equals(name)) {
            enc = Surface.create();
        } else {
            throw new IllegalArgumentException("DefaultEncodedValueFactory cannot find EncodedValue " + name);
        }
        return enc;
    }
}
