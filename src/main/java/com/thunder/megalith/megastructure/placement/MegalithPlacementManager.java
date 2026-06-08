package com.thunder.megalith.megastructure.placement;

import com.thunder.megalith.Megalith;
import com.thunder.megalith.megastructure.data.MegalithSavedData;
import com.thunder.megalith.megastructure.data.MegaStructureDefinition;
import com.thunder.megalith.megastructure.terrain.TerrainAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Singleton that coordinates all active placement queues.
 *
 * One queue exists per ServerLevel key. Ticked by the Megalith mod class
 * on every {@code ServerTickEvent.Post}.
 */
public class MegalithPlacementManager {

    private static final MegalithPlacementManager INSTANCE = new MegalithPlacementManager();
    /** level dimension key string → queue */
    private final Map<String, MegalithPlacementQueue> queues = new HashMap<>();

    private MegalithPlacementManager() {}

    public static MegalithPlacementManager getInstance() { return INSTANCE; }

    // ── Server tick ───────────────────────────────────────────────────────────

    public void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            String key = level.dimension().location().toString();
            MegalithPlacementQueue queue = queues.get(key);
            if (queue == null || queue.isEmpty()) continue;

            queue.tick(level, finishedTask -> {
                MegalithSavedData data = MegalithSavedData.get(level);
                data.markComplete(finishedTask.getStructureId());
                Megalith.LOGGER.info("[Megalith] Structure '{}' placement complete at {}.",
                        finishedTask.getStructureId(), finishedTask.getOrigin());
            });
        }
    }

    // ── Enqueue ───────────────────────────────────────────────────────────────

    /**
     * Queue placement of a structure at the given origin.
     * Applies terrain adaptation before queueing, then registers the structure as generated.
     */
    public boolean enqueue(ServerLevel level, MegaStructureDefinition def, BlockPos origin) {
        String key = level.dimension().location().toString();

        // Apply terrain before any blocks are placed
        TerrainAdapter.apply(level, origin,
                def.scanRadius() / 2, def.scanRadius() / 2,
                def.foundationDepth(), def.terrainMode());

        MegalithSavedData.get(level).markGenerated(def.id(), level.dimension(), origin);

        MegalithPlacementTask task = new MegalithPlacementTask(def, origin);

        if (def.placeInPhases()) {
            queues.computeIfAbsent(key, k -> new MegalithPlacementQueue()).enqueue(task);
            Megalith.LOGGER.info("[Megalith] Queued phased placement for '{}' at {}.", def.id(), origin);
        } else {
            // Immediate placement — process all pieces now
            while (!task.isComplete()) {
                task.tick(level);
            }
            MegalithSavedData.get(level).markComplete(def.id());
            Megalith.LOGGER.info("[Megalith] Immediate placement done for '{}' at {}.", def.id(), origin);
        }
        return true;
    }

    public boolean hasActiveTask(ServerLevel level, ResourceLocation id) {
        String key = level.dimension().location().toString();
        MegalithPlacementQueue q = queues.get(key);
        return q != null && q.hasTaskFor(id);
    }
}