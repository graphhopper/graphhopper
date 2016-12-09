/*
 *  Licensed to GraphHopper GmbH under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper GmbH licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.routing.util;

import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.util.spatialrules.*;
import com.graphhopper.util.DouglasPeucker;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.BBox;

import java.util.ArrayList;
import java.util.List;

/**
 * This class creates FlagEncoders that are already included in the GraphHopper distribution.
 *
 * @author Peter Karich
 */
public class DefaultFlagEncoderFactory implements FlagEncoderFactory {
    @Override
    public FlagEncoder createFlagEncoder(String name, PMap configuration) {
        if (name.equals(GENERIC)) {
            // TODO: Find a better way to setup the SpatialLookup ;)
            SpatialRuleLookup spatialRuleLookup = new SpatialRuleLookupArray(new BBox(-180,180,-90,90), .1);
            String germanPolygonJson = "[9.921906,54.983104],[9.93958,54.596642],[10.950112,54.363607],[10.939467,54.008693],[11.956252,54.196486],[12.51844,54.470371],[13.647467,54.075511],[14.119686,53.757029],[14.353315,53.248171],[14.074521,52.981263],[14.4376,52.62485],[14.685026,52.089947],[14.607098,51.745188],[15.016996,51.106674],[14.570718,51.002339],[14.307013,51.117268],[14.056228,50.926918],[13.338132,50.733234],[12.966837,50.484076],[12.240111,50.266338],[12.415191,49.969121],[12.521024,49.547415],[13.031329,49.307068],[13.595946,48.877172],[13.243357,48.416115],[12.884103,48.289146],[13.025851,47.637584],[12.932627,47.467646],[12.62076,47.672388],[12.141357,47.703083],[11.426414,47.523766],[10.544504,47.566399],[10.402084,47.302488],[9.896068,47.580197],[9.594226,47.525058],[8.522612,47.830828],[8.317301,47.61358],[7.466759,47.620582],[7.593676,48.333019],[8.099279,49.017784],[6.65823,49.201958],[6.18632,49.463803],[6.242751,49.902226],[6.043073,50.128052],[6.156658,50.803721],[5.988658,51.851616],[6.589397,51.852029],[6.84287,52.22844],[7.092053,53.144043],[6.90514,53.482162],[7.100425,53.693932],[7.936239,53.748296],[8.121706,53.527792],[8.800734,54.020786],[8.572118,54.395646],[8.526229,54.962744],[9.282049,54.830865],[9.921906,54.983104]";
            String[] germanPolygonArr = germanPolygonJson.split("\\],\\[");
            double[] lats = new double[germanPolygonArr.length];
            double[] lons = new double[germanPolygonArr.length];
            for (int i = 0; i < germanPolygonArr.length; i++) {
                String temp = germanPolygonArr[i];
                temp = temp.replaceAll("\\[", "");
                temp = temp.replaceAll("\\]", "");
                String[] coords = temp.split(",");
                lats[i] = Double.parseDouble(coords[1]);
                lons[i] = Double.parseDouble(coords[0]);
            }

            Polygon p = new Polygon(lats, lons);
            spatialRuleLookup.addRule(new SpatialRule() {
                @Override
                public AccessValue isAccessible(ReaderWay readerWay, String transportationMode) {
                    if(readerWay.hasTag("highway", "track")){
                        return AccessValue.NOT_ACCESSIBLE;
                    }else{
                        return AccessValue.ACCESSIBLE;
                    }
                }
            }, p);


            return new DataFlagEncoder(spatialRuleLookup);

        }else if (name.equals(CAR))
            return new CarFlagEncoder(configuration);

        else if (name.equals(CAR4WD))
            return new Car4WDFlagEncoder(configuration);

        if (name.equals(BIKE))
            return new BikeFlagEncoder(configuration);

        if (name.equals(BIKE2))
            return new Bike2WeightFlagEncoder(configuration);

        if (name.equals(RACINGBIKE))
            return new RacingBikeFlagEncoder(configuration);

        if (name.equals(MOUNTAINBIKE))
            return new MountainBikeFlagEncoder(configuration);

        if (name.equals(FOOT))
            return new FootFlagEncoder(configuration);

        if (name.equals(HIKE))
            return new HikeFlagEncoder(configuration);

        if (name.equals(MOTORCYCLE))
            return new MotorcycleFlagEncoder(configuration);

        throw new IllegalArgumentException("entry in encoder list not supported " + name);
    }
}
