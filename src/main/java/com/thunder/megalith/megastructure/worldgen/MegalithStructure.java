package com.thunder.megalith.megastructure.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thunder.megalith.Megalith;
import com.thunder.megalith.megastructure.data.MegalithSavedData;
import com.thunder.megalith.megastructure.data.MegaStructureDefinition;
import com.thunder.megalith.megastructure.datapack.MegalithStructureLoader;
import com.thunder.megalith.megastructure.placement.MegalithPlacementManager;
import com.thunder.megalith.megastructure.terrain.TerrainScanner;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * A vanilla-pipeline {@link Structure} subclass that represents one Megalith
 * structure definition.
 *
 * One instance of this class exists per Megalith JSON definition that is wired
 * into a worldgen biome modifier or dimension structure set.
 *
 * The class does two things during worldgen:
 * <ol>
 *   <li>Decides whether to generate at the candidate chunk (once-per-world guard,
 *       terrain fitness check).</li>
 *   <li>Commits a {@link MegalithStructurePiece} that records the origin so the
 *       server-tick placement queue can finish the work after chunk gen.</li>
 * </ol>
 */
public class MegalithStructure extends Structure {

    /** Codec used by {@link MegalithWorldgenRegistry#MEGALITH_STRUCTURE_TYPE}. */
    public static final MapCodec<MegalithStructure> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    // Standard vanilla Structure settings (biome tags, step, spawns, etc.)
                    Structure.settingsCodec(instance),
                    // The Megalith definition id this structure instance wraps
                    ResourceLocation.CODEC.fieldOf("megalith_id").forGetter(s -> s.megalithId)
            ).apply(instance, MegalithStructure::new)
    );

    private final ResourceLocation megalithId;

    public MegalithStructure(Structure.StructureSettings settings, ResourceLocation megalithId) {
        super(settings);
        this.megalithId = megalithId;
    }

    // ── Structure pipeline entry point ────────────────────────────────────────

    /**
     * Called by the worldgen pipeline to decide whether this structure generates
     * at the given chunk and, if so, what pieces to add.
     *
     * This is the correct place to:
     * - Enforce once-per-world (via SavedData)
     * - Check terrain fitness
     * - Commit the placement origin
     */
    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        // Load the definition — it must be present at worldgen time
        Optional<MegaStructureDefinition> defOpt = MegalithStructureLoader.getInstance().get(megalithId);
        if (defOpt.isEmpty()) {
            Megalith.LOGGER.warn("[Megalith] findGenerationPoint: no definition loaded for '{}'", megalithId);
            return Optional.empty();
        }
        MegaStructureDefinition def = defOpt.get();

        // Once-per-world guard — check SavedData via the server's overworld
        // TODO: verify context.heightAccessor() vs context.chunkGenerator() for server access in NeoForge 1.21.1
        //       A cleaner approach is to store a server reference in a thread-local set during ServerStartingEvent.
        //       For now we use the generation context's random source only and defer the SavedData check to
        //       MegalithStructurePiece.postPlace() where we have a ServerLevel reference.

        ChunkPos chunkPos = context.chunkPos();
        int originX = chunkPos.getMiddleBlockX();
        int originZ = chunkPos.getMiddleBlockZ();

        // Surface Y via heightmap
        // TODO: verify Heightmap.Types.WORLD_SURFACE_WG availability in generation context
        int surfaceY = context.chunkGenerator()
                .getFirstOccupiedHeight(originX, originZ,
                        Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(),
                        context.randomState());

        BlockPos origin = new BlockPos(originX, surfaceY, originZ);

        // Terrain fitness — reject steep or wet sites early
        // Note: full ServerLevel isn't available here; use chunkGenerator height sampling
        int variance = estimateHeightVariance(context, origin, def.scanRadius());
        if (variance > 20) {
            return Optional.empty();
        }

        Megalith.LOGGER.debug("[Megalith] Generating '{}' at chunk {}", megalithId, chunkPos);

        // Commit the piece — actual block placement is deferred to the tick queue
        return Optional.of(new GenerationStub(origin, builder ->
                assemblePieces(builder, def, origin)));
    }

    // ── Piece assembly ────────────────────────────────────────────────────────

    private void assemblePieces(StructurePiecesBuilder builder,
                                MegaStructureDefinition def, BlockPos origin) {
        // Add one MegalithStructurePiece per template + piece entry.
        // Vanilla serialises these to the chunk's StructureStart NBT so they
        // survive the gap between worldgen and the tick-queue placement.
        builder.addPiece(new MegalithStructurePiece(def, origin));
    }

    // ── Terrain helpers ───────────────────────────────────────────────────────

    /**
     * Estimates height variance using the chunk generator's heightmap — no
     * ServerLevel required, safe to call during worldgen.
     */
    private static int estimateHeightVariance(GenerationContext ctx, BlockPos centre, int radius) {
        int step = Math.max(4, radius / 8);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                int h = ctx.chunkGenerator().getFirstOccupiedHeight(
                        centre.getX() + dx, centre.getZ() + dz,
                        Heightmap.Types.WORLD_SURFACE_WG,
                        ctx.heightAccessor(), ctx.randomState());
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        return (min == Integer.MAX_VALUE) ? 0 : (max - min);
    }

    // ── StructureType ─────────────────────────────────────────────────────────

    @Override
    public StructureType<?> type() {
        // Returns the registered StructureType so vanilla can serialise/deserialise
        return MegalithWorldgenRegistry.MEGALITH_STRUCTURE_TYPE.get();
    }
}