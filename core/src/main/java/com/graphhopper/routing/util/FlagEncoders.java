package com.graphhopper.routing.util;

import com.graphhopper.util.PMap;

public class FlagEncoders {
    public static FlagEncoder createFoot() {
        return createFoot(new PMap());
    }

    public static FlagEncoder createFoot(PMap properties) {
        return VehicleEncodedValues.foot(properties);
    }

    public static FlagEncoder createHike() {
        return createHike(new PMap());
    }

    public static FlagEncoder createHike(PMap properties) {
        return VehicleEncodedValues.hike(properties);
    }

    public static FlagEncoder createWheelchair() {
        return createWheelchair(new PMap());
    }

    public static FlagEncoder createWheelchair(PMap properties) {
        return VehicleEncodedValues.wheelchair(properties);
    }

    public static FlagEncoder createCar() {
        return createCar(new PMap());
    }

    public static FlagEncoder createCar(PMap properties) {
        return VehicleEncodedValues.car(properties);
    }

    public static FlagEncoder createMotorcycle() {
        return createMotorcycle(new PMap());
    }

    public static FlagEncoder createMotorcycle(PMap properties) {
        return VehicleEncodedValues.motorcycle(properties);
    }

    public static FlagEncoder createCar4wd(PMap properties) {
        return VehicleEncodedValues.car4wd(properties);
    }

    public static FlagEncoder createRacingBike() {
        return createRacingBike(new PMap());
    }

    public static FlagEncoder createRacingBike(PMap properties) {
        return VehicleEncodedValues.racingbike(properties);
    }

    public static FlagEncoder createBike() {
        return createBike(new PMap());
    }

    public static FlagEncoder createBike(PMap properties) {
        return VehicleEncodedValues.bike(properties);
    }

    public static FlagEncoder createBike2() {
        return createBike2(new PMap());
    }

    public static FlagEncoder createBike2(PMap properties) {
        return VehicleEncodedValues.bike2(properties);
    }

    public static FlagEncoder createMountainBike() {
        return createMountainBike(new PMap());
    }

    public static FlagEncoder createMountainBike(PMap properties) {
        return VehicleEncodedValues.mountainbike(properties);
    }

    public static FlagEncoder createRoads() {
        return VehicleEncodedValues.roads();
    }
}
