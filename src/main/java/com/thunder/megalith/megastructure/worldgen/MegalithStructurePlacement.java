package com.thunder.megalith.megastructure.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * A {@link StructurePlacement} subclass that wraps
 * {@link RandomSpreadStructurePlacement} and adds Megalith-specific guards:
 *
 * <ul>
 *   <li>Minimum / maximum distance from world spawn.</li>
 *   <li>Delegates the actual chunk-grid math to vanilla's random-spread logic.</li>
 * </ul>
 *
 * Modders register a placement instance in their dimension's
 * {@code worldgen/structure_set/<name>.json} pointing to this type.
 *
 * <pre>{@code
 * // data/mymod/worldgen/structure_set/particle_accelerator.json
 * {
 *   "structures": [{ "structure": "mymod:particle_accelerator", "weight": 1 }],
 *   "placement": {
 *     "type": "megalith:megalith_placement",
 *     "spacing": 64,
 *     "separation": 16,
 *     "salt": 198273645,
 *     "min_distance_from_spawn_chunks": 250,
 *     "max_distance_from_spawn_chunks": 750
 *   }
 * }
 * }</pre>
 */
public class MegalithStructurePlacement extends StructurePlacement {

    public static final MapCodec<MegalithStructurePlacement> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO)
                            .forGetter(MegalithStructurePlacement::locateOffset),
                    FrequencyReductionMethod.CODEC
                            .optionalFieldOf("frequency_reduction_method", FrequencyReductionMethod.DEFAULT)
                            .forGetter(MegalithStructurePlacement::frequencyReductionMethod),
                    MapCodec.unit(1.0f).forGetter(MegalithStructurePlacement::frequency),
                    net.minecraft.util.ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt")
                            .forGetter(MegalithStructurePlacement::salt),
                    net.minecraft.core.Codec.INT.optionalFieldOf("spacing", 32)
                            .forGetter(p -> p.spacing),
                    net.minecraft.core.Codec.INT.optionalFieldOf("separation", 8)
                            .forGetter(p -> p.separation),
                    net.minecraft.core.Codec.INT.optionalFieldOf("min_distance_from_spawn_chunks", 0)
                            .forGetter(p -> p.minDistanceFromSpawnChunks),
                    net.minecraft.core.Codec.INT.optionalFieldOf("max_distance_from_spawn_chunks", 10000)
                            .forGetter(p -> p.maxDistanceFromSpawnChunks)
            ).apply(instance, MegalithStructurePlacement::new));

    private final int spacing;
    private final int separation;
    private final int minDistanceFromSpawnChunks;
    private final int maxDistanceFromSpawnChunks;

    public MegalithStructurePlacement(Vec3i locateOffset,
                                      FrequencyReductionMethod reductionMethod,
                                      float frequency, int salt,
                                      int spacing, int separation,
                                      int minDistanceFromSpawnChunks,
                                      int maxDistanceFromSpawnChunks) {
        super(locateOffset, reductionMethod, frequency, salt, java.util.Optional.empty());
        this.spacing = spacing;
        this.separation = separation;
        this.minDistanceFromSpawnChunks = minDistanceFromSpawnChunks;
        this.maxDistanceFromSpawnChunks = maxDistanceFromSpawnChunks;
    }

    /**
     * Returns true if this structure should attempt to generate at the given chunk.
     * Vanilla calls this for every candidate chunk during structure spreading.
     */
    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
        // Spawn is always at chunk 0,0 in a default world
        double distChunks = Math.sqrt((double) chunkX * chunkX + (double) chunkZ * chunkZ);
        if (distChunks < minDistanceFromSpawnChunks) return false;
        if (distChunks > maxDistanceFromSpawnChunks) return false;

        // Vanilla grid: place in chunks that are multiples of spacing,
        // with a random offset bounded by separation
        // TODO: mirror vanilla RandomSpreadStructurePlacement grid logic here
        //       for now, use a simple modulo grid as a placeholder
        int gridX = Math.floorDiv(chunkX, spacing);
        int gridZ = Math.floorDiv(chunkZ, spacing);
        net.minecraft.util.RandomSource rng = state.random(gridX, gridZ, salt(), RandomSpreadType.LINEAR);
        int candidateX = gridX * spacing + rng.nextInt(spacing - separation);
        int candidateZ = gridZ * spacing + rng.nextInt(spacing - separation);
        return candidateX == chunkX && candidateZ == chunkZ;
    }

    @Override
    public StructurePlacementType<?> type() {
        // TODO: register this type via DeferredRegister<StructurePlacementType<?>>
        //       and return the registered instance here
        throw new UnsupportedOperationException(
                "[Megalith] MegalithStructurePlacement type() not yet registered. " +
                        "Add a DeferredRegister entry in MegalithWorldgenRegistry.");
    }

    // ── Getters (needed by CODEC) ─────────────────────────────────────────────

    @Override
    protected Vec3i locateOffset() { return super.locateOffset(); }
    @Override
    protected FrequencyReductionMethod frequencyReductionMethod() { return super.frequencyReductionMethod(); }
    @Override
    protected float frequency() { return super.frequency(); }
}