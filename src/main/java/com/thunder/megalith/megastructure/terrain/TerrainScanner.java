package com.thunder.megalith.megastructure.terrain;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Samples terrain height and composition around a candidate position.
 */
public class TerrainScanner {

    /**
     * Result of a terrain scan.
     */
    public record ScanResult(
            int minHeight,
            int maxHeight,
            int avgHeight,
            boolean hasWater,
            boolean isTooSteep
    ) {
        /** Vertical range of the scanned area. */
        public int heightVariance() { return maxHeight - minHeight; }
    }

    /**
     * Scans an area of (2*radius+1)^2 columns centred on origin.
     *
     * @param level     The server level to sample
     * @param origin    Centre of the scan, only X/Z are used
     * @param radius    Sample radius in blocks
     * @param steepness Maximum tolerated height variance before flagging as steep
     */
    public static ScanResult scan(ServerLevel level, BlockPos origin, int radius, int steepness) {
        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;
        long totalH = 0;
        int samples = 0;
        boolean water = false;

        for (int dx = -radius; dx <= radius; dx += 4) {
            for (int dz = -radius; dz <= radius; dz += 4) {
                // TODO: verify Heightmap.Types.WORLD_SURFACE_WG is the correct heightmap for placement checks
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, origin.getX() + dx, origin.getZ() + dz);
                minH = Math.min(minH, h);
                maxH = Math.max(maxH, h);
                totalH += h;
                samples++;

                BlockPos surface = new BlockPos(origin.getX() + dx, h - 1, origin.getZ() + dz);
                if (level.getBlockState(surface).getBlock() == Blocks.WATER
                        || level.getBlockState(surface).getBlock() == Blocks.LAVA) {
                    water = true;
                }
            }
        }

        int avg = samples == 0 ? 64 : (int) (totalH / samples);
        boolean tooSteep = (maxH - minH) > steepness;
        return new ScanResult(minH, maxH, avg, water, tooSteep);
    }
}