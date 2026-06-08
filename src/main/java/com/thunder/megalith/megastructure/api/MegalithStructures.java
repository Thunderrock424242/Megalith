package com.thunder.megalith.megastructure.api;

import com.thunder.megalith.megastructure.data.MegalithSavedData;
import com.thunder.megalith.megastructure.data.MegaStructureDefinition;
import com.thunder.megalith.megastructure.datapack.MegalithStructureLoader;
import com.thunder.megalith.megastructure.placement.MegalithPlacementFinder;
import com.thunder.megalith.megastructure.placement.MegalithPlacementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.Optional;

/**
 * Primary public API for the Megalith library.
 * <p>
 * Other mods should interact with Megalith exclusively through this class.
 *
 * <pre>{@code
 * // Check if a structure has generated
 * boolean done = MegalithStructures.hasGenerated(serverLevel, ResourceLocation.parse("mymod:my_structure"));
 *
 * // Queue placement (finds position automatically)
 * MegalithStructures.queuePlacement(serverLevel, ResourceLocation.parse("mymod:my_structure"));
 *
 * // Queue placement at a specific position
 * MegalithStructures.queuePlacement(serverLevel, ResourceLocation.parse("mymod:my_structure"), myPos);
 * }</pre>
 */
public final class MegalithStructures {

    private MegalithStructures() {}

    // ── Definition lookup ─────────────────────────────────────────────────────

    /**
     * Get a loaded structure definition by its id.
     */
    public static Optional<MegaStructureDefinition> getDefinition(ResourceLocation id) {
        return MegalithStructureLoader.getInstance().get(id);
    }

    /**
     * Get all loaded structure definitions.
     */
    public static Collection<MegaStructureDefinition> getDefinitions() {
        return MegalithStructureLoader.getInstance().getAll();
    }

    // ── Generation state ──────────────────────────────────────────────────────

    /**
     * Returns true if the structure has been (or is being) generated in this world.
     */
    public static boolean hasGenerated(ServerLevel level, ResourceLocation id) {
        return MegalithSavedData.get(level).hasGenerated(id);
    }

    /**
     * Manually mark a structure as generated at a specific position.
     * Useful for custom generation hooks.
     */
    public static void markGenerated(ServerLevel level, ResourceLocation id, BlockPos pos) {
        MegalithSavedData.get(level).markGenerated(id, level.dimension(), pos);
    }

    /**
     * Returns the position where this structure was generated, if available.
     */
    public static Optional<BlockPos> getGeneratedPos(ServerLevel level, ResourceLocation id) {
        return MegalithSavedData.get(level).getGeneratedPos(id);
    }

    /**
     * Clear the generated state so the structure can be generated again.
     */
    public static void resetGenerated(ServerLevel level, ResourceLocation id) {
        MegalithSavedData.get(level).reset(id);
    }

    // ── Placement ─────────────────────────────────────────────────────────────

    /**
     * Find a valid position automatically and queue placement.
     *
     * Respects {@code oncePerWorld} — returns false if already generated.
     *
     * @return true if successfully queued, false otherwise.
     */
    public static boolean queuePlacement(ServerLevel level, ResourceLocation id) {
        Optional<MegaStructureDefinition> defOpt = getDefinition(id);
        if (defOpt.isEmpty()) return false;

        MegaStructureDefinition def = defOpt.get();

        if (def.oncePerWorld() && hasGenerated(level, id)) return false;

        Optional<BlockPos> pos = MegalithPlacementFinder.find(level, def);
        if (pos.isEmpty()) return false;

        return MegalithPlacementManager.getInstance().enqueue(level, def, pos.get());
    }

    /**
     * Queue placement at a caller-specified position.
     *
     * Respects {@code oncePerWorld} — returns false if already generated.
     *
     * @return true if successfully queued, false otherwise.
     */
    public static boolean queuePlacement(ServerLevel level, ResourceLocation id, BlockPos pos) {
        Optional<MegaStructureDefinition> defOpt = getDefinition(id);
        if (defOpt.isEmpty()) return false;

        MegaStructureDefinition def = defOpt.get();
        if (def.oncePerWorld() && hasGenerated(level, id)) return false;

        return MegalithPlacementManager.getInstance().enqueue(level, def, pos);
    }
}