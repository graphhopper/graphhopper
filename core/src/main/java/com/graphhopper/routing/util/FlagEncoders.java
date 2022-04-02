package com.graphhopper.routing.util;

import com.graphhopper.util.PMap;

public class FlagEncoders {
    public static FlagEncoder createFoot() {
        return new FootFlagEncoder();
    }

    public static FlagEncoder createFoot(PMap properties) {
        return new FootFlagEncoder(properties);
    }

    protected static FlagEncoder createFoot(int speedBits, double speedFactor, boolean speedTwoDirections) {
        return new FootFlagEncoder(speedBits, speedFactor, speedTwoDirections);
    }

    protected static FlagEncoder createFoot(String name, int speedBits, double speedFactor, boolean speedTwoDirections) {
        return new FootFlagEncoder(name, speedBits, speedFactor, speedTwoDirections);
    }

    public static FlagEncoder createHike() {
        return new HikeFlagEncoder();
    }

    public static FlagEncoder createHike(PMap properties) {
        return new HikeFlagEncoder(properties);
    }

    protected static FlagEncoder createHike(int speedBits, double speedFactor, boolean speedTwoDirections) {
        return new HikeFlagEncoder(speedBits, speedFactor, speedTwoDirections);
    }

    protected static FlagEncoder createHike(String name, int speedBits, double speedFactor, boolean speedTwoDirections) {
        return new HikeFlagEncoder(name, speedBits, speedFactor, speedTwoDirections);
    }

    public static FlagEncoder createWheelchair() {
        return new WheelchairFlagEncoder();
    }

    public static FlagEncoder createWheelchair(PMap properties) {
        return new WheelchairFlagEncoder(properties);
    }

    protected static FlagEncoder createWheelchair(int speedBits, double speedFactor) {
        return new WheelchairFlagEncoder(speedBits, speedFactor);
    }

    public static FlagEncoder createCar() {
        return new CarFlagEncoder();
    }

    public static FlagEncoder createCar(int speedBits, double speedFactor, int maxTurnCosts) {
        return new CarFlagEncoder(speedBits, speedFactor, maxTurnCosts);
    }

    public static FlagEncoder createCar(String name, int speedBits, double speedFactor, int maxTurnCosts) {
        return new CarFlagEncoder(name, speedBits, speedFactor, maxTurnCosts);
    }

    public static FlagEncoder createCar(int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        return new CarFlagEncoder(speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
    }

    public static FlagEncoder createCar(String name, int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        return new CarFlagEncoder(name, speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
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

    protected static FlagEncoder createRacingBike(int speedBits, double speedFactor, int maxTurnCosts) {
        return new RacingBikeFlagEncoder(speedBits, speedFactor, maxTurnCosts);
    }

    public static FlagEncoder createBike() {
        return new BikeFlagEncoder();
    }

    public static FlagEncoder createBike(String name) {
        return new BikeFlagEncoder(name);
    }

    public static FlagEncoder createBike(PMap properties) {
        return new BikeFlagEncoder(properties);
    }

    public static FlagEncoder createBike(int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        return new BikeFlagEncoder(speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
    }

    public static FlagEncoder createBike(String name, int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        return new BikeFlagEncoder(name, speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
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

    protected static FlagEncoder createMountainBike(int speedBits, double speedFactor, int maxTurnCosts) {
        return new MountainBikeFlagEncoder(speedBits, speedFactor, maxTurnCosts);
    }

    public static FlagEncoder createRoadsFlagEncoder() {
        return new RoadsFlagEncoder();
    }
}
