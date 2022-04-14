package com.graphhopper.routing.util;

import com.graphhopper.util.PMap;

public class FlagEncoders {
    public static FlagEncoder createFoot() {
        return new FootFlagEncoder();
    }

    public static FlagEncoder createFoot(PMap properties) {
        return new FootFlagEncoder(properties);
    }

    public static FlagEncoder createHike() {
        return new HikeFlagEncoder(new PMap());
    }

    public static FlagEncoder createHike(PMap properties) {
        return new HikeFlagEncoder(properties);
    }

    public static FlagEncoder createWheelchair() {
        return new WheelchairFlagEncoder();
    }

    public static FlagEncoder createWheelchair(PMap properties) {
        return new WheelchairFlagEncoder(properties);
    }

    public static FlagEncoder createCar() {
        return new CarFlagEncoder();
    }

    public static FlagEncoder createCar(PMap properties) {
        return new CarFlagEncoder(properties);
    }

    public static FlagEncoder createMotorcycle() {
        return new MotorcycleFlagEncoder();
    }

    public static FlagEncoder createMotorcycle(PMap properties) {
        return new MotorcycleFlagEncoder(properties);
    }

    public static FlagEncoder createCar4wd(PMap properties) {
        return new Car4WDFlagEncoder(properties);
    }

    public static FlagEncoder createRacingBike() {
        return new RacingBikeFlagEncoder();
    }

    public static FlagEncoder createRacingBike(PMap properties) {
        return new RacingBikeFlagEncoder(properties);
    }

    public static FlagEncoder createBike() {
        return new BikeFlagEncoder();
    }

    public static FlagEncoder createBike(PMap properties) {
        return new BikeFlagEncoder(properties);
    }

    public static FlagEncoder createBike2() {
        return new Bike2WeightFlagEncoder();
    }

    public static FlagEncoder createBike2(PMap properties) {
        return new Bike2WeightFlagEncoder(properties);
    }

    public static FlagEncoder createMountainBike() {
        return new MountainBikeFlagEncoder();
    }

    public static FlagEncoder createMountainBike(PMap properties) {
        return new MountainBikeFlagEncoder(properties);
    }

    public static FlagEncoder createRoadsFlagEncoder() {
        return new RoadsFlagEncoder();
    }
}
