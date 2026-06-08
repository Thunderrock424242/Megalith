package com.thunder.megalith.megastructure.placement;

import com.thunder.megalith.Megalith;
import com.thunder.megalith.megastructure.data.MegaStructureDefinition;
import com.thunder.megalith.megastructure.terrain.TerrainScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;
import java.util.Random;

/**
 * Searches for a valid placement position for a {@link MegaStructureDefinition}.
 *
 * Searches outward from world spawn within [minDistance, maxDistance].
 * Designed to be extensible — add more checks as needed.
 */
public class MegalithPlacementFinder {

    private static final int MAX_ATTEMPTS = 64;
    private static final Random RAND = new Random();

    /**
     * Find a valid position for the given structure definition in the given level.
     *
     * @return An Optional containing the bottom-centre BlockPos, or empty if none found.
     */
    public static Optional<BlockPos> find(ServerLevel level, MegaStructureDefinition def) {
        BlockPos spawn = level.getSharedSpawnPos();
        int minDist = def.minDistanceFromSpawn();
        int maxDist = def.maxDistanceFromSpawn();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            // Pick a random angle and distance
            double angle = RAND.nextDouble() * 2 * Math.PI;
            int dist = minDist + RAND.nextInt(Math.max(1, maxDist - minDist));

            int cx = spawn.getX() + (int) (Math.cos(angle) * dist);
            int cz = spawn.getZ() + (int) (Math.sin(angle) * dist);

            // TODO: verify getHeight Heightmap usage for surface Y in NeoForge 1.21.1
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz);
            BlockPos candidate = new BlockPos(cx, surfaceY, cz);

            if (!isValidPosition(level, candidate, def)) continue;

            Megalith.LOGGER.debug("[Megalith] Found placement position for '{}' at {}", def.id(), candidate);
            return Optional.of(candidate);
        }

        Megalith.LOGGER.warn("[Megalith] Could not find valid position for '{}' after {} attempts.", def.id(), MAX_ATTEMPTS);
        return Optional.empty();
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private static boolean isValidPosition(ServerLevel level, BlockPos pos, MegaStructureDefinition def) {
        // Dimension check — caller should already be in the right level,
        // but guard against cross-dimension calls.
        if (!level.dimension().equals(def.dimension())) return false;

        // Water avoidance
        if (def.avoidWater()) {
            if (level.getBlockState(pos.below()).getBlock() == Blocks.WATER) return false;
            if (level.getBlockState(pos.below()).getBlock() == Blocks.LAVA)  return false;
        }

        // Terrain steep-ness check
        TerrainScanner.ScanResult scan = TerrainScanner.scan(level, pos, def.scanRadius() / 4, 20);
        if (scan.isTooSteep()) return false;
        if (def.avoidWater() && scan.hasWater()) return false;

        // Biome tag check
        if (def.biomeTag().isPresent()) {
            String tagStr = def.biomeTag().get();
            // TODO: replace with proper tag lookup via level.registryAccess() in NeoForge 1.21.1
            // For now, accept any position if a biome tag is specified (tag check is a future enhancement)
            Megalith.LOGGER.debug("[Megalith] Biome tag '{}' check skipped (not yet fully implemented).", tagStr);
        }

        // Village avoidance — placeholder; proper POI/structure scan can be added here
        if (def.avoidVillages()) {
            // TODO: implement village proximity check using level.structureManager()
        }

        return true;
    }
}