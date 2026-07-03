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
package com.graphhopper.routing.weighting.custom.generate;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.util.CustomModel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.graphhopper.json.Statement.*;
import static com.graphhopper.json.Statement.Op.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * GOLDEN-SOURCE test for the stage-5 Kotlin source generator: the generator's output for
 * representative custom models is asserted against sources checked into the repository, so
 * any drift of the emitted code is visible in review. The two most important goldens ARE the
 * pre-generated classes under src/test/kotlin — the very classes that
 * {@link RegistryBackendDifferentialTest} compiles and proves bit-identical to the Janino
 * back-end. That closes the loop: golden = compiled = differentially verified.
 *
 * On mismatch the actual output is written to target/stage5-actual/ for diffing (and for
 * intentionally regenerating the goldens).
 */
public class SourceGeneratorTest {

    static final String PACKAGE = "com.graphhopper.routing.weighting.custom.generate";
    static final Path TEST_KOTLIN_DIR = Path.of("src/test/kotlin/com/graphhopper/routing/weighting/custom/generate");

    final EncodingManager em = Stage5Fixtures.createEncodingManager();

    @Test
    public void carModelMatchesPreGeneratedClass() {
        String actual = CustomWeightingSourceGenerator.generate(
                Stage5Fixtures.carModel(), em, PACKAGE, "GeneratedCarCustomWeighting");
        assertMatchesGolden(TEST_KOTLIN_DIR.resolve("GeneratedCarCustomWeighting.kt"), actual);
    }

    @Test
    public void kitchenSinkModelMatchesPreGeneratedClass() {
        String actual = CustomWeightingSourceGenerator.generate(
                Stage5Fixtures.kitchenSinkModel(), em, PACKAGE, "GeneratedKitchenSinkCustomWeighting");
        assertMatchesGolden(TEST_KOTLIN_DIR.resolve("GeneratedKitchenSinkCustomWeighting.kt"), actual);
    }

    @Test
    public void generationIsDeterministic() {
        String first = CustomWeightingSourceGenerator.generate(
                Stage5Fixtures.kitchenSinkModel(), em, PACKAGE, "GeneratedKitchenSinkCustomWeighting");
        String second = CustomWeightingSourceGenerator.generate(
                Stage5Fixtures.kitchenSinkModel(), em, PACKAGE, "GeneratedKitchenSinkCustomWeighting");
        assertEquals(first, second);
    }

    @Test
    public void rejectsWhatJaninoRejects() {
        // the generator must fail at BUILD time for models the server-side backends reject;
        // messages mirror ClosureBackend/CustomModelParser
        assertGenerationFails(base().addToPriority(If("road_class == 2", MULTIPLY, "0.5")),
                "incomparable operand types");
        assertGenerationFails(base().addToPriority(If("unknown_ev > 3", MULTIPLY, "0.5")),
                "'unknown_ev' not available");
        assertGenerationFails(base().addToPriority(If("max_speed >> 2 == 1", MULTIPLY, "0.5")),
                "shift operator");
        assertGenerationFails(base().addToPriority(If("road_class == PRIMARY", ADD, "5")),
                "'priority' statement must not have the operation 'add'");
        assertGenerationFails(base().addToSpeed(If("true", LIMIT, "max_speed * car_average_speed")),
                "only a single EncodedValue");
        assertGenerationFails(base().addToSpeed(If("true", LIMIT, "max_speed - 500")),
                "negative weight");
        assertGenerationFails(base().addToPriority(If("in_area_missing", MULTIPLY, "0.5")),
                "wasn't found");
        assertGenerationFails(base().addToTurnPenalty(If("car_access", MULTIPLY, "2")),
                "must have the operation 'add'");
        CustomModel noSpeed = new CustomModel();
        noSpeed.addToPriority(If("car_access", MULTIPLY, "0.5"));
        assertGenerationFails(noSpeed, "At least one initial statement under 'speed' is required");
        CustomModel conditionalSpeedOnly = new CustomModel();
        conditionalSpeedOnly.addToSpeed(If("car_access", LIMIT, "30"));
        assertGenerationFails(conditionalSpeedOnly, "The first group needs");
    }

    static CustomModel base() {
        CustomModel m = new CustomModel();
        m.addToSpeed(If("true", LIMIT, "car_average_speed"));
        return m;
    }

    void assertGenerationFails(CustomModel model, String expectedMessagePart) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> CustomWeightingSourceGenerator.generate(model, em, PACKAGE, "GeneratedRejected"));
        assertTrue(ex.getMessage().contains(expectedMessagePart),
                () -> "expected message containing '" + expectedMessagePart + "' but was: " + ex.getMessage());
    }

    static void assertMatchesGolden(Path golden, String actual) {
        try {
            String expected = Files.exists(golden)
                    ? Files.readString(golden, StandardCharsets.UTF_8).replace("\r\n", "\n")
                    : null;
            if (!actual.equals(expected)) {
                Path out = Path.of("target/stage5-actual").resolve(golden.getFileName());
                Files.createDirectories(out.getParent());
                Files.writeString(out, actual, StandardCharsets.UTF_8);
                fail("generated source differs from golden " + golden + "\nactual written to " + out
                        + "\n--- diff the two files; update the golden ONLY after reviewing the change ---");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
