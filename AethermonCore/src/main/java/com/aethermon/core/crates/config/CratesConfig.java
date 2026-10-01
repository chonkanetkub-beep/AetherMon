package com.aethermon.core.crates.config;

import com.aethermon.core.AethermonCore;
import com.aethermon.core.crates.model.Crate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CratesConfig {
    public List<Crate> crates = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static CratesConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir()
            .resolve("aethermoncore")
            .resolve("crates.json");
        try {
            if (!Files.exists(path)) {
                AethermonCore.LOGGER.warn("[Crates] crates.json not found at {}, using empty defaults", path);
                return new CratesConfig();
            }
            return GSON.fromJson(Files.readString(path), CratesConfig.class);
        } catch (IOException e) {
            AethermonCore.LOGGER.error("[Crates] Failed to load crates.json: {}", e.getMessage());
            return new CratesConfig();
        }
    }

    public Optional<Crate> getCrate(String crateId) {
        if (crates == null) return Optional.empty();
        return crates.stream().filter(c -> c.id.equalsIgnoreCase(crateId)).findFirst();
    }
}
