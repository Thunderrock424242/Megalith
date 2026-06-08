package com.thunder.megalith.megastructure.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

import java.util.Optional;

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
            RecordCodecBuilder.<MegalithStructurePlacement>mapCodec(instance -> instance.group(
                    Vec3i.offsetCodec(16).optionalFieldOf("locate_offset", Vec3i.ZERO)
                            .forGetter(MegalithStructurePlacement::locateOffset),
                    FrequencyReductionMethod.CODEC
                            .optionalFieldOf("frequency_reduction_method", FrequencyReductionMethod.DEFAULT)
                            .forGetter(MegalithStructurePlacement::frequencyReductionMethod),
                    Codec.floatRange(0.0F, 1.0F).optionalFieldOf("frequency", 1.0F)
                            .forGetter(MegalithStructurePlacement::frequency),
                    ExtraCodecs.NON_NEGATIVE_INT.fieldOf("salt")
                            .forGetter(MegalithStructurePlacement::salt),
                    StructurePlacement.ExclusionZone.CODEC.optionalFieldOf("exclusion_zone")
                            .forGetter(MegalithStructurePlacement::exclusionZone),
                    Codec.intRange(0, 4096).optionalFieldOf("spacing", 32)
                            .forGetter(p -> p.spacing),
                    Codec.intRange(0, 4096).optionalFieldOf("separation", 8)
                            .forGetter(p -> p.separation),
                    RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR)
                            .forGetter(p -> p.spreadType),
                    Codec.INT.optionalFieldOf("min_distance_from_spawn_chunks", 0)
                            .forGetter(p -> p.minDistanceFromSpawnChunks),
                    Codec.INT.optionalFieldOf("max_distance_from_spawn_chunks", 10000)
                            .forGetter(p -> p.maxDistanceFromSpawnChunks)
            ).apply(instance, MegalithStructurePlacement::new)
            ).validate(MegalithStructurePlacement::validate);

    private final int spacing;
    private final int separation;
    private final RandomSpreadType spreadType;
    private final int minDistanceFromSpawnChunks;
    private final int maxDistanceFromSpawnChunks;

    public MegalithStructurePlacement(Vec3i locateOffset,
                                      FrequencyReductionMethod reductionMethod,
                                      float frequency, int salt,
                                      Optional<StructurePlacement.ExclusionZone> exclusionZone,
                                      int spacing, int separation,
                                      RandomSpreadType spreadType,
                                      int minDistanceFromSpawnChunks,
                                      int maxDistanceFromSpawnChunks) {
        super(locateOffset, reductionMethod, frequency, salt, exclusionZone);
        this.spacing = spacing;
        this.separation = separation;
        this.spreadType = spreadType;
        this.minDistanceFromSpawnChunks = minDistanceFromSpawnChunks;
        this.maxDistanceFromSpawnChunks = maxDistanceFromSpawnChunks;
    }

    private static DataResult<MegalithStructurePlacement> validate(MegalithStructurePlacement placement) {
        if (placement.spacing <= placement.separation) {
            return DataResult.error(() -> "Spacing has to be larger than separation");
        }
        if (placement.minDistanceFromSpawnChunks > placement.maxDistanceFromSpawnChunks) {
            return DataResult.error(() -> "Minimum distance cannot be greater than maximum distance");
        }
        return DataResult.success(placement);
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

        int gridX = Math.floorDiv(chunkX, spacing);
        int gridZ = Math.floorDiv(chunkZ, spacing);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(state.getLevelSeed(), gridX, gridZ, salt());
        int offsetBound = spacing - separation;
        int candidateX = gridX * spacing + spreadType.evaluate(random, offsetBound);
        int candidateZ = gridZ * spacing + spreadType.evaluate(random, offsetBound);
        return candidateX == chunkX && candidateZ == chunkZ;
    }

    @Override
    public StructurePlacementType<?> type() {
        return MegalithWorldgenRegistry.MEGALITH_PLACEMENT_TYPE.get();
    }

    // ── Getters (needed by CODEC) ─────────────────────────────────────────────

    @Override
    protected Vec3i locateOffset() { return super.locateOffset(); }
    @Override
    protected FrequencyReductionMethod frequencyReductionMethod() { return super.frequencyReductionMethod(); }
    @Override
    protected float frequency() { return super.frequency(); }
    @Override
    protected int salt() { return super.salt(); }
    @Override
    protected Optional<StructurePlacement.ExclusionZone> exclusionZone() { return super.exclusionZone(); }
}
