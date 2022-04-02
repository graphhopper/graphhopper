package com.graphhopper.routing.util;

import com.graphhopper.util.PMap;

public class FlagEncoders {
    public static FootFlagEncoder createFoot() {
        return new FootFlagEncoder();
    }

    public static FootFlagEncoder createFoot(PMap properties) {
        return new FootFlagEncoder(properties);
    }

    protected static FootFlagEncoder createFoot(int speedBits, double speedFactor, boolean speedTwoDirections) {
        return new FootFlagEncoder(speedBits, speedFactor, speedTwoDirections);
    }

    protected static FootFlagEncoder createFoot(String name, int speedBits, double speedFactor, boolean speedTwoDirections) {
        return new FootFlagEncoder(name, speedBits, speedFactor, speedTwoDirections);
    }

    public static HikeFlagEncoder createHike() {
        return new HikeFlagEncoder();
    }

    public static HikeFlagEncoder createHike(PMap properties) {
        return new HikeFlagEncoder(properties);
    }

    protected static HikeFlagEncoder createHike(int speedBits, double speedFactor, boolean speedTwoDirections) {
        return new HikeFlagEncoder(speedBits, speedFactor, speedTwoDirections);
    }

    protected static HikeFlagEncoder createHike(String name, int speedBits, double speedFactor, boolean speedTwoDirections) {
        return new HikeFlagEncoder(name, speedBits, speedFactor, speedTwoDirections);
    }

    public static WheelchairFlagEncoder createWheelchair() {
        return new WheelchairFlagEncoder();
    }

    public static WheelchairFlagEncoder createWheelchair(PMap properties) {
        return new WheelchairFlagEncoder(properties);
    }

    protected static WheelchairFlagEncoder createWheelchair(int speedBits, double speedFactor) {
        return new WheelchairFlagEncoder(speedBits, speedFactor);
    }

    public static CarFlagEncoder createCar() {
        return new CarFlagEncoder();
    }

    public static CarFlagEncoder createCar(int speedBits, double speedFactor, int maxTurnCosts) {
        return new CarFlagEncoder(speedBits, speedFactor, maxTurnCosts);
    }

    public static CarFlagEncoder createCar(String name, int speedBits, double speedFactor, int maxTurnCosts) {
        return new CarFlagEncoder(name, speedBits, speedFactor, maxTurnCosts);
    }

    public static CarFlagEncoder createCar(int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        return new CarFlagEncoder(speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
    }

    public static CarFlagEncoder createCar(String name, int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        return new CarFlagEncoder(name, speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
    }

    public static CarFlagEncoder createCar(PMap properties) {
        return new CarFlagEncoder(properties);
    }

    public static MotorcycleFlagEncoder createMotorcycle() {
        return new MotorcycleFlagEncoder();
    }

    public static MotorcycleFlagEncoder createMotorcycle(PMap properties) {
        return new MotorcycleFlagEncoder(properties);
    }

    public static Car4WDFlagEncoder createCar4wd(PMap properties) {
        return new Car4WDFlagEncoder(properties);
    }

    public static RacingBikeFlagEncoder createRacingBike() {
        return new RacingBikeFlagEncoder();
    }

    public static RacingBikeFlagEncoder createRacingBike(PMap properties) {
        return new RacingBikeFlagEncoder(properties);
    }

    protected static RacingBikeFlagEncoder createRacingBike(int speedBits, double speedFactor, int maxTurnCosts) {
        return new RacingBikeFlagEncoder(speedBits, speedFactor, maxTurnCosts);
    }

    public static BikeFlagEncoder createBike() {
        return new BikeFlagEncoder();
    }

    public static BikeFlagEncoder createBike(String name) {
        return new BikeFlagEncoder(name);
    }

    public static BikeFlagEncoder createBike(PMap properties) {
        return new BikeFlagEncoder(properties);
    }

    public static BikeFlagEncoder createBike(int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        return new BikeFlagEncoder(speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
    }

    public static BikeFlagEncoder createBike(String name, int speedBits, double speedFactor, int maxTurnCosts, boolean speedTwoDirections) {
        return new BikeFlagEncoder(name, speedBits, speedFactor, maxTurnCosts, speedTwoDirections);
    }

    public static Bike2WeightFlagEncoder createBike2() {
        return new Bike2WeightFlagEncoder();
    }

    public static Bike2WeightFlagEncoder createBike2(PMap properties) {
        return new Bike2WeightFlagEncoder(properties);
    }

    public static MountainBikeFlagEncoder createMountainBike() {
        return new MountainBikeFlagEncoder();
    }

    public static MountainBikeFlagEncoder createMountainBike(PMap properties) {
        return new MountainBikeFlagEncoder(properties);
    }

    protected static MountainBikeFlagEncoder createMountainBike(int speedBits, double speedFactor, int maxTurnCosts) {
        return new MountainBikeFlagEncoder(speedBits, speedFactor, maxTurnCosts);
    }

    public static RoadsFlagEncoder createRoadsFlagEncoder() {
        return new RoadsFlagEncoder();
    }
}
