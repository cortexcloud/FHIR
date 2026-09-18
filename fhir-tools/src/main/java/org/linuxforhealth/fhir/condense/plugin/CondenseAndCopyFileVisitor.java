/*
 * (C) Copyright IBM Corp. 2021, 2026
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.linuxforhealth.fhir.condense.plugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;
import jakarta.json.JsonWriter;
import jakarta.json.JsonWriterFactory;

import org.eclipse.parsson.api.JsonConfig;

/**
 * A FileVisitor that strips non-meaningful whitespace from JSON files as it copies
 * them to a new directory.
 */
public class CondenseAndCopyFileVisitor extends SimpleFileVisitor<Path> {
    // The checked-in FHIR specification bundles include trusted JSON files larger
    // than Parsson's 15-million-character runtime default. Keep a bounded limit
    // for this build-only tool without weakening the server parser configuration.
    static final int MAX_PARSING_LIMIT = 100_000_000;

    final JsonReaderFactory jsonReaderFactory = Json.createReaderFactory(
            Map.of(JsonConfig.MAX_PARSING_LIMIT, MAX_PARSING_LIMIT));
    final JsonWriterFactory jsonWriterFactory = Json.createWriterFactory(null);
    final Path resourcesDir;
    final Path outputDir;

    int count = 0;

    public CondenseAndCopyFileVisitor(Path resourcesDir, Path outputDirectory) {
        this.resourcesDir = resourcesDir;
        this.outputDir = outputDirectory;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (file.toString().endsWith(".json")) {
            String relativePath = resourcesDir.relativize(file).toString();
            Path target = Paths.get(outputDir.toString(), relativePath);

            JsonObject jsonObject = null;
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                JsonReader jsonReader = jsonReaderFactory.createReader(reader);
                jsonObject = jsonReader.readObject();
            }
            try (BufferedWriter writer = Files.newBufferedWriter(target)) {
                JsonWriter jsonWriter = jsonWriterFactory.createWriter(writer);
                jsonWriter.write(jsonObject);
            }
            count++;
        }
        return FileVisitResult.CONTINUE;
    }

    /**
     * @return the number of processed files
     */
    public int getCount() {
        return count;
    }
}
