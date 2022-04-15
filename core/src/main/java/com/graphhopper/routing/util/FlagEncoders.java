package com.graphhopper.routing.util;

import com.graphhopper.util.PMap;

public class FlagEncoders {
    public static FlagEncoder createFoot() {
        return new FootTagParser();
    }

    public static FlagEncoder createFoot(PMap properties) {
        return new FootTagParser(properties);
    }

    public static FlagEncoder createHike() {
        return new HikeTagParser(new PMap());
    }

    public static FlagEncoder createHike(PMap properties) {
        return new HikeTagParser(properties);
    }

    public static FlagEncoder createWheelchair() {
        return new WheelchairTagParser();
    }

    public static FlagEncoder createWheelchair(PMap properties) {
        return new WheelchairTagParser(properties);
    }

    public static FlagEncoder createCar() {
        return new CarTagParser();
    }

    public static FlagEncoder createCar(PMap properties) {
        return new CarTagParser(properties);
    }

    public static FlagEncoder createMotorcycle() {
        return new MotorcycleTagParser();
    }

    public static FlagEncoder createMotorcycle(PMap properties) {
        return new MotorcycleTagParser(properties);
    }

    public static FlagEncoder createCar4wd(PMap properties) {
        return new Car4WDTagParser(properties);
    }

    public static FlagEncoder createRacingBike() {
        return new RacingBikeTagParser();
    }

    public static FlagEncoder createRacingBike(PMap properties) {
        return new RacingBikeTagParser(properties);
    }

    public static FlagEncoder createBike() {
        return new BikeTagParser();
    }

    public static FlagEncoder createBike(PMap properties) {
        return new BikeTagParser(properties);
    }

    public static FlagEncoder createBike2() {
        return new Bike2WeightTagParser();
    }

    public static FlagEncoder createBike2(PMap properties) {
        return new Bike2WeightTagParser(properties);
    }

    public static FlagEncoder createMountainBike() {
        return new MountainBikeTagParser();
    }

    public static FlagEncoder createMountainBike(PMap properties) {
        return new MountainBikeTagParser(properties);
    }

    public static FlagEncoder createRoads() {
        return new RoadsTagParser();
    }
}
