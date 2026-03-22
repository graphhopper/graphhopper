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

import com.graphhopper.config.Profile;
import com.graphhopper.util.CustomModel;
import com.graphhopper.util.JsonFeatureCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomModelResolverTest {

    @TempDir
    Path tempDir;

    private CustomModelResolver sut = new CustomModelResolver("", new JsonFeatureCollection());

    @Test
    void nonCustomProfile_passesThrough() {
        Profile profile = new Profile("car").setWeighting("fastest");
        List<Profile> result = sut.resolveAll(List.of(profile));
        assertEquals(1, result.size());
        assertSame(profile, result.get(0));
    }

    @Test
    void inlineCustomModel_isDeserialized() {
        Profile profile = new Profile("car").setWeighting("custom");
        profile.getHints().putObject(CustomModel.KEY, new CustomModel());

        List<Profile> result = sut.resolveAll(List.of(profile));

        assertNotNull(result.get(0).getCustomModel());
    }

    @Test
    void emptyCustomModelFiles_createsEmptyCustomModel() {
        Profile profile = new Profile("car").setWeighting("custom")
                .setCustomModel(null);
        profile.getHints().putObject("custom_model_files", Collections.emptyList());

        List<Profile> result = sut.resolveAll(List.of(profile));

        assertNotNull(result.get(0).getCustomModel());
    }

    @Test
    void customModelFileFromJar_isLoaded() {
        Profile profile = new Profile("car").setWeighting("custom")
                .setCustomModel(null);
        profile.getHints().putObject("custom_model_files", List.of("car.json"));

        List<Profile> result = sut.resolveAll(List.of(profile));

        assertNotNull(result.get(0).getCustomModel());
    }

    @Test
    void customModelFileFromExternalDir_isLoaded() throws IOException {
        Path file = tempDir.resolve("my_model.json");
        Files.writeString(file, "{\"speed\":[]}");

        Profile profile = new Profile("car").setWeighting("custom")
                .setCustomModel(null);
        profile.getHints().putObject("custom_model_files", List.of("my_model.json"));

        List<Profile> result = new CustomModelResolver(tempDir.toString(), new JsonFeatureCollection()).resolveAll(List.of(profile));

        assertNotNull(result.get(0).getCustomModel());
    }

    @Test
    void multipleCustomModelFiles_areMerged() throws IOException {
        Path file = tempDir.resolve("extra.json");
        Files.writeString(file, "{\"distance_influence\":50}");

        Profile profile = new Profile("car").setWeighting("custom")
                .setCustomModel(null);
        profile.getHints().putObject("custom_model_files", List.of("car.json", "extra.json"));

        List<Profile> result = new CustomModelResolver(tempDir.toString(), new JsonFeatureCollection()).resolveAll(List.of(profile));

        assertNotNull(result.get(0).getCustomModel());
        assertEquals(50, result.get(0).getCustomModel().getDistanceInfluence());
    }

    @Test
    void inlineCustomModelAndFiles_throwsException() {
        Profile profile = new Profile("car").setWeighting("custom");
        profile.getHints().putObject(CustomModel.KEY, new CustomModel());
        profile.getHints().putObject("custom_model_files", List.of("car.json"));

        assertThrows(IllegalArgumentException.class, () -> sut.resolveAll(List.of(profile)));
    }

    @Test
    void oldSingularCustomModelFile_throwsException() {
        Profile profile = new Profile("car").setWeighting("custom")
                .setCustomModel(null);
        profile.getHints().putObject("custom_model_file", "car.json");

        assertThrows(IllegalArgumentException.class, () -> sut.resolveAll(List.of(profile)));
    }

    @Test
    void missingCustomModelAndFiles_throwsException() {
        Profile profile = new Profile("car").setWeighting("custom")
                .setCustomModel(null);

        assertThrows(IllegalArgumentException.class, () -> sut.resolveAll(List.of(profile)));
    }

    @Test
    void fileNameWithPathSeparator_throwsException() {
        Profile profile = new Profile("car").setWeighting("custom")
                .setCustomModel(null);
        profile.getHints().putObject("custom_model_files", List.of("subdir/car.json"));

        assertThrows(IllegalArgumentException.class, () -> sut.resolveAll(List.of(profile)));
    }

    @Test
    void nonJsonFile_throwsException() {
        Profile profile = new Profile("car").setWeighting("custom")
                .setCustomModel(null);
        profile.getHints().putObject("custom_model_files", List.of("car.yaml"));

        assertThrows(IllegalArgumentException.class, () -> sut.resolveAll(List.of(profile)));
    }

    @Test
    void builtInFileNameConflictsWithExternalFile_throwsException() throws IOException {
        Path file = tempDir.resolve("car.json");
        Files.writeString(file, "{\"speed\":[]}");

        Profile profile = new Profile("car").setWeighting("custom")
                .setCustomModel(null);
        profile.getHints().putObject("custom_model_files", List.of("car.json"));

        assertThrows(RuntimeException.class,
                () -> new CustomModelResolver(tempDir.toString(), new JsonFeatureCollection()).resolveAll(List.of(profile)));
    }

}
