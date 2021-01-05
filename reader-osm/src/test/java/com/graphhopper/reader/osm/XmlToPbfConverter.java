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

package com.graphhopper.reader.osm;

import crosby.binary.osmosis.OsmosisSerializer;
import org.openstreetmap.osmosis.osmbinary.file.BlockOutputStream;
import org.openstreetmap.osmosis.xml.common.CompressionMethod;
import org.openstreetmap.osmosis.xml.v0_6.XmlReader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

/**
 * Converts OSM files from xml to pbf. To be used in tests.
 */
public class XmlToPbfConverter {

    private XmlToPbfConverter() {
    }

    public static String xmlToPbf(String path, String outputDir) {
        Path source = Paths.get(path).normalize().toAbsolutePath();
        String sourceFilename = source.getFileName().toString();
        Path target = Paths.get(outputDir).resolve(sourceFilename + ".converted.pbf");
        try {
            convert(source.toFile(), target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target.toString();
    }

    public static String classPathXmlToPbf(String osmFile, String outputDir) {
        Path target;
        try {
            File file = new File(XmlToPbfConverter.class.getResource(osmFile).getFile());
            target = Paths.get(outputDir).resolve(osmFile + ".converted.pbf");
            convert(file, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return target.toString();
    }

    private static void convert(File source, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target, CREATE, TRUNCATE_EXISTING);
             BufferedOutputStream bufferedOut = new BufferedOutputStream(out)) {
            BlockOutputStream blockOut = new BlockOutputStream(bufferedOut);
            OsmosisSerializer serializer = new OsmosisSerializer(blockOut);
            XmlReader reader = new XmlReader(source, false, CompressionMethod.None);
            reader.setSink(serializer);
            reader.run();
        }
    }
}