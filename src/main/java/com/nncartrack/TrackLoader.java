package com.nncartrack;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TrackLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TrackLoader() {
    }

    public static TrackDefinition load(String trackPath) {
        Path path = Path.of(trackPath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("Track file not found: " + path.toAbsolutePath());
        }

        try {
            TrackDefinition track = MAPPER.readValue(Files.newInputStream(path), TrackDefinition.class);
            track.validate(path.toString());
            return track;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load track file " + path.toAbsolutePath(), e);
        }
    }
}
