/*
 *  Licensed to Peter Karich under one or more contributor license 
 *  agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  Peter Karich licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except 
 *  in compliance with the License. You may obtain a copy of the 
 *  License at
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

import com.graphhopper.util.Helper;
import gnu.trove.list.array.TLongArrayList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Peter Karich
 */
public class AcceptWay {

    public static final String CAR = "CAR";
    public static final String BIKE = "BIKE";
    public static final String FOOT = "FOOT";
    private static final HashMap<String,String> defaultEncoders = new HashMap<String, String>(  );
    static {
        defaultEncoders.put( CAR, CarFlagEncoder.class.getName());
        defaultEncoders.put( BIKE, BikeFlagEncoder.class.getName() );
        defaultEncoders.put( FOOT, FootFlagEncoder.class.getName() );
    }

    // maximum number supported by int flag size is currnetly 4
    private AbstractFlagEncoder[] encoders = new AbstractFlagEncoder[4];
    private int encoderCount = 0;

//    private CarFlagEncoder carEncoder = new CarFlagEncoder();
//    private FootFlagEncoder footEncoder = new FootFlagEncoder();
//    private BikeFlagEncoder bikeEncoder = new BikeFlagEncoder();
//    private boolean car;
//    private boolean bike;
//    private boolean foot;


    public AcceptWay( String encoderList ) {
        String[] entries = encoderList.split( "," );
        for( String entry : entries ) {
            entry = entry.trim();
            String className = null;
            int pos = entry.indexOf( ":" );
            if( pos > 0 ) {
                className = entry.substring( pos+1 );
            }
            else {
                className = defaultEncoders.get( entry );
                if( className == null )
                    throw new IllegalArgumentException( "Unknown encoder name " + entry );
            }

            try {
                Class cls = Class.forName( className );
                register( (AbstractFlagEncoder) cls.newInstance() );
            }
            catch( Exception e ) {
                throw new IllegalArgumentException( "Cannot instantiate class " + className, e );
            }

        }
    }

    public void register( AbstractFlagEncoder encoder )
    {
        encoders[encoderCount++] = encoder;
    }

/*
    public AcceptWay(boolean car, boolean bike, boolean foot) {
        this.car = car;
        this.bike = bike;
        this.foot = foot;
    }

    public AcceptWay foot(boolean foot) {
        this.foot = foot;
        return this;
    }

    public AcceptWay car(boolean car) {
        this.car = car;
        return this;
    }

    public boolean acceptsCar() {
        return car;
    }

    public boolean acceptsBike() {
        return bike;
    }

    public boolean acceptsFoot() {
        return foot;
    }
*/
    public boolean accepts( String name )
    {
        return getEncoder( name ) != null;
    }

    public AbstractFlagEncoder getEncoder( String name )
    {
        for( int i = 0; i < encoderCount; i++ ) {
            if( name.equals( encoders[i].toString() ))
                return encoders[i];
        }
        return null;
    }

    /*
    Determine whether an osm way is a routable way
     */
    public int accept( Map<String, String> osmProperties ) {
        int includeWay = 0;

        for( int i=0; i<encoderCount; i++ )
            includeWay |= encoders[i].isAllowed( osmProperties );

        return includeWay;
    }

    /**
     * Processes way properties of different kind to determine speed and
     * direction. Properties are directly encoded in 4-Byte flags.
     *
     * @return the encoded flags
     */
    public int encodeTags( int includeWay, Map<String, String> osmProperties ) {

        int flags = 0;
        for( int i=0; i<encoderCount; i++ ) {
            flags |= encoders[i].handleWayTags( includeWay, osmProperties );
        }

        return flags;
    }

    /**
     * @return the speed in km/h
     */
    static int parseSpeed(String str) {
        if (Helper.isEmpty(str))
            return -1;
        int kmInteger = str.indexOf("km");
        if (kmInteger > 0)
            str = str.substring(0, kmInteger).trim();

        // see https://en.wikipedia.org/wiki/Knot_%28unit%29#Definitions
        int mpInteger = str.indexOf("m");
        if (mpInteger > 0)
            str = str.substring(0, mpInteger).trim();

        int knotInteger = str.indexOf( "knots" );
        if (knotInteger > 0)
            str = str.substring(0, knotInteger).trim();

        try {
            int val = Integer.parseInt(str);
            if (mpInteger > 0)
                return (int) Math.round(val * 1.609);
            if (knotInteger > 0)
                return (int) Math.round(val * 1.852);
            return val;
        } catch (Exception ex) {
            return -1;
        }
    }

    public int countVehicles() {
        return encoderCount;
    }

    public boolean accepts(EdgePropertyEncoder encoder) {
        for( int i = 0; i < encoderCount; i++ ) {
            if( encoders[i].getClass().equals( encoder.getClass() ) )
                return true;
        }
        return false;
    }

    public static boolean isTrue(Object obj) {
        if (obj == null)
            return false;
        return "yes".equals(obj) || "true".equals(obj) || "1".equals(obj);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder(  );

        for( int i = 0; i < encoderCount; i++ ) {
            if( str.length() > 0 )
                str.append( "," );
            str.append( encoders[i].toString() );
        }
        return str.toString();
    }

}