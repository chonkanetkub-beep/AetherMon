package com.aethermon.core.homes.api;

import com.aethermon.core.homes.model.Home;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service managing player homes.
 */
public interface HomeService {

    CompletableFuture<List<Home>> getHomes(UUID playerId);

    CompletableFuture<Optional<Home>> getHome(UUID playerId, String name);

    CompletableFuture<Boolean> setHome(Home home);

    CompletableFuture<Boolean> deleteHome(UUID playerId, String name);

    CompletableFuture<Integer> getHomeCount(UUID playerId);

    int getMaxHomes(ServerPlayerEntity player);
}
