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

package com.graphhopper;

/**
 * This class is used for junit4->5 migration. Unfortunately to do the migration the (optional) message parameter of
 * the various assertXYZ methods was moved from the beginning to the end of the parameter list. This wrapper class
 * contains (or is supposed to contain) all these methods in both variations, so it can be used as drop-in replacement
 * for the junit4 imports. As a final step all the methods can be inlined by IntelliJ to complete the migration.
 */
public class Junit4To5Assertions {
    public static void assertTrue(boolean b) {
        org.junit.jupiter.api.Assertions.assertTrue(b);
    }

    public static void assertTrue(String message, boolean b) {
        org.junit.jupiter.api.Assertions.assertTrue(b, message);
    }

    public static void assertTrue(boolean b, String message) {
        org.junit.jupiter.api.Assertions.assertTrue(b, message);
    }

    public static void assertFalse(boolean b) {
        org.junit.jupiter.api.Assertions.assertFalse(b);
    }

    public static void assertFalse(boolean b, String message) {
        org.junit.jupiter.api.Assertions.assertFalse(b, message);
    }

    public static void assertFalse(String message, boolean b) {
        org.junit.jupiter.api.Assertions.assertFalse(b, message);
    }

    public static void assertEquals(int expected, int given) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given);
    }

    public static void assertEquals(String message, int expected, int given) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, message);
    }

    public static void assertEquals(int expected, int given, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, message);
    }

    public static void assertEquals(long expected, long given) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given);
    }

    public static void assertEquals(String message, long expected, long given) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, message);
    }

    public static void assertEquals(int expected, long given, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, message);
    }

    public static void assertEquals(String message, float expected, float given, float precision) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, precision, message);
    }

    public static void assertEquals(float expected, float given, float precision, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, precision, message);
    }

    public static void assertEquals(float expected, float given, float precision) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, precision);
    }

    public static void assertEquals(String message, double expected, double given, double precision) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, precision, message);
    }

    public static void assertEquals(double expected, double given, double precision, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, precision, message);
    }

    public static void assertEquals(double expected, double given, double precision) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, precision);
    }

    public static void assertEquals(String message, Object expected, Object given) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, message);
    }

    public static void assertEquals(Object expected, Object given, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given, message);
    }

    public static void assertEquals(Object expected, Object given) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, given);
    }

    public static void assertNotEquals(int expected, int given) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, given);
    }

    public static void assertNotEquals(String message, int expected, int given) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, given, message);
    }

    public static void assertNotEquals(int expected, int given, String message) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, given, message);
    }

    public static void assertNotEquals(String message, double expected, double given, double precision) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, given, precision, message);
    }

    public static void assertNotEquals(double expected, double given, double precision, String message) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, given, precision, message);
    }

    public static void assertNotEquals(double expected, double given, double precision) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, given, precision);
    }

    public static void assertNotEquals(String message, Object expected, Object given) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, given, message);
    }

    public static void assertNotEquals(Object expected, Object given, String message) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, given, message);
    }

    public static void assertNotEquals(Object expected, Object given) {
        org.junit.jupiter.api.Assertions.assertNotEquals(expected, given);
    }

    public static void assertNull(Object object) {
        org.junit.jupiter.api.Assertions.assertNull(object);
    }

    public static void assertNull(String message, Object object) {
        org.junit.jupiter.api.Assertions.assertNull(object, message);
    }

    public static void assertNull(Object object, String message) {
        org.junit.jupiter.api.Assertions.assertNull(object, message);
    }

    public static void assertNotNull(Object object) {
        org.junit.jupiter.api.Assertions.assertNotNull(object);
    }

    public static void assertNotNull(String message, Object object) {
        org.junit.jupiter.api.Assertions.assertNotNull(object, message);
    }

    public static void assertNotNull(Object object, String message) {
        org.junit.jupiter.api.Assertions.assertNotNull(object, message);
    }
}
