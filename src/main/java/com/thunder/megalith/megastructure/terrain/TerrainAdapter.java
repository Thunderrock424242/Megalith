package com.thunder.megalith.megastructure.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Modifies terrain around a placement origin according to the chosen {@link TerrainMode}.
 *
 * All methods operate synchronously — for large areas call from a phased task,
 * not in one tick.
 */
public class TerrainAdapter {

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR   = Blocks.AIR.defaultBlockState();

    /**
     * Apply the given terrain mode to the area before structure placement.
     *
     * @param level         Target server level
     * @param origin        Bottom-centre of the structure footprint
     * @param halfWidth     Half-width of the footprint on X axis
     * @param halfDepth     Half-depth of the footprint on Z axis
     * @param foundDepth    How many blocks deep to extend foundation or carving
     * @param mode          Selected terrain mode
     */
    public static void apply(ServerLevel level, BlockPos origin, int halfWidth, int halfDepth,
                             int foundDepth, TerrainMode mode) {
        switch (mode) {
            case NONE       -> { /* no-op */ }
            case FLAT_PAD   -> flatPad(level, origin, halfWidth, halfDepth);
            case SMART_FOUNDATION -> smartFoundation(level, origin, halfWidth, halfDepth, foundDepth);
            case BURIED     -> { /* placement Y offset is handled by the finder; no extra terrain work */ }
            case CAVE_CARVE -> caveCarve(level, origin, halfWidth, halfDepth, foundDepth);
        }
    }

    // ── Private implementations ───────────────────────────────────────────────

    /** Levels all columns in the footprint to the origin Y, clearing above and filling below. */
    private static void flatPad(ServerLevel level, BlockPos origin, int hw, int hd) {
        int targetY = origin.getY();
        for (int dx = -hw; dx <= hw; dx++) {
            for (int dz = -hd; dz <= hd; dz++) {
                // TODO: verify getHeight Heightmap usage is current in NeoForge 1.21.1
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX() + dx, origin.getZ() + dz);
                // Clear above target Y
                for (int y = targetY; y <= surfaceY; y++) {
                    BlockPos p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, AIR, 3);
                    }
                }
                // Fill below target Y if there's air
                for (int y = targetY - 1; y >= targetY - 4; y--) {
                    BlockPos p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
                    if (level.getBlockState(p).isAir()) {
                        level.setBlock(p, STONE, 3);
                    }
                }
            }
        }
    }

    /** Places stone under any air column within the footprint down to foundationDepth. */
    private static void smartFoundation(ServerLevel level, BlockPos origin, int hw, int hd, int depth) {
        for (int dx = -hw; dx <= hw; dx++) {
            for (int dz = -hd; dz <= hd; dz++) {
                for (int dy = 0; dy >= -depth; dy--) {
                    BlockPos p = new BlockPos(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (level.getBlockState(p).isAir()) {
                        level.setBlock(p, STONE, 3);
                    }
                }
            }
        }
    }

    /** Carves a hollow underground box to make room for buried/cave structures. */
    private static void caveCarve(ServerLevel level, BlockPos origin, int hw, int hd, int depth) {
        for (int dx = -hw; dx <= hw; dx++) {
            for (int dz = -hd; dz <= hd; dz++) {
                for (int dy = 0; dy >= -depth; dy--) {
                    BlockPos p = new BlockPos(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    level.setBlock(p, AIR, 3);
                }
            }
        }
    }
}