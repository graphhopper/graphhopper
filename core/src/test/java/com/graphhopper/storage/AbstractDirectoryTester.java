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
package com.graphhopper.storage;

import com.graphhopper.util.Helper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Peter Karich
 */
public abstract class AbstractDirectoryTester {
    protected String location = "./target/tmp/dir";
    private DataAccess da;

    abstract Directory createDir();

    @AfterEach
    public void tearDown() {
        if (da != null)
            da.close();
        Helper.removeDir(new File(location));
    }

    @BeforeEach
    public void setUp() {
        Helper.removeDir(new File(location));
    }

    @Test
    public void testNoDuplicates() {
        Directory dir = createDir();
        DataAccess da1 = dir.create("testing");
        assertThrows(IllegalStateException.class, () -> dir.create("testing"));
        da1.close();
    }

    @Test
    public void testNoErrorForDACreate() {
        Directory dir = createDir();
        da = dir.find("testing");
        da.create(100);
        da.flush();
    }
}
