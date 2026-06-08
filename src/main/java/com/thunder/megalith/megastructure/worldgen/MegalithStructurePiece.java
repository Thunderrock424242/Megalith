package com.thunder.megalith.megastructure.worldgen;

import com.thunder.megalith.Megalith;
import com.thunder.megalith.megastructure.data.MegalithSavedData;
import com.thunder.megalith.megastructure.data.MegaStructureDefinition;
import com.thunder.megalith.megastructure.datapack.MegalithStructureLoader;
import com.thunder.megalith.megastructure.placement.MegalithPlacementManager;
import com.thunder.megalith.megastructure.terrain.TerrainAdapter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

import java.util.Optional;

/**
 * Vanilla {@link StructurePiece} that acts as a lightweight marker committed
 * during worldgen. Its {@link #postProcess} method fires once the chunk is
 * fully generated, at which point it:
 *
 * <ol>
 *   <li>Enforces once-per-world via {@link MegalithSavedData}.</li>
 *   <li>Applies terrain adaptation (now we have a real {@link ServerLevelAccessor}).</li>
 *   <li>Hands off to {@link MegalithPlacementManager} for phased block placement.</li>
 * </ol>
 *
 * Because actual block placement is deferred, this piece intentionally does
 * very little work inside {@code postProcess} itself — just scheduling.
 */
public class MegalithStructurePiece extends StructurePiece {

    // Registered lazily — see MegalithWorldgenRegistry
    // TODO: register a real StructurePieceType via DeferredRegister in MegalithWorldgenRegistry
    //       and replace this placeholder reference.
    public static StructurePieceType TYPE;

    private final ResourceLocation megalithId;
    private final BlockPos origin;

    // ── Constructor (worldgen path) ───────────────────────────────────────────

    public MegalithStructurePiece(MegaStructureDefinition def, BlockPos origin) {
        super(requireType(), 0, makeBoundingBox(origin, def.scanRadius()));
        this.megalithId = def.id();
        this.origin     = origin;
    }

    // ── Constructor (deserialisation path) ───────────────────────────────────

    public MegalithStructurePiece(StructurePieceSerializationContext ctx, CompoundTag tag) {
        super(requireType(), tag);
        this.megalithId = ResourceLocation.parse(tag.getString("megalithId"));
        this.origin     = new BlockPos(tag.getInt("ox"), tag.getInt("oy"), tag.getInt("oz"));
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext ctx, CompoundTag tag) {
        tag.putString("megalithId", megalithId.toString());
        tag.putInt("ox", origin.getX());
        tag.putInt("oy", origin.getY());
        tag.putInt("oz", origin.getZ());
    }

    // ── Post-process (fires during chunk decoration) ──────────────────────────

    /**
     * Called by vanilla once the chunk containing this piece is decorated.
     * This is the earliest point where we have a real level reference and can
     * safely queue block placement.
     */
    @Override
    public void postProcess(net.minecraft.world.level.WorldGenLevel level,
                            StructureManager structureManager,
                            net.minecraft.world.level.chunk.ChunkGenerator generator,
                            net.minecraft.util.RandomSource random,
                            BoundingBox box, ChunkPos chunkPos,
                            BlockPos pieceOffset) {

        Optional<MegaStructureDefinition> defOpt = MegalithStructureLoader.getInstance().get(megalithId);
        if (defOpt.isEmpty()) {
            Megalith.LOGGER.warn("[Megalith] postProcess: definition missing for '{}'", megalithId);
            return;
        }
        MegaStructureDefinition def = defOpt.get();

        // WorldGenLevel wraps ServerLevel during decoration — cast is safe here
        // TODO: verify cast safety in NeoForge 1.21.1 decoration phase
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            Megalith.LOGGER.warn("[Megalith] postProcess: level is not a ServerLevel for '{}'", megalithId);
            return;
        }

        // Once-per-world guard — now we have a real ServerLevel
        MegalithSavedData savedData = MegalithSavedData.get(serverLevel);
        if (def.oncePerWorld() && savedData.hasGenerated(megalithId)) {
            Megalith.LOGGER.debug("[Megalith] Skipping '{}' — already generated.", megalithId);
            return;
        }

        // Apply terrain adaptation synchronously (we're in the decoration phase,
        // chunk is loaded but not yet sent to clients — safe to modify)
        TerrainAdapter.apply(serverLevel, origin,
                def.scanRadius() / 2, def.scanRadius() / 2,
                def.foundationDepth(), def.terrainMode());

        // Hand off to the placement manager — marks as generated and queues pieces
        MegalithPlacementManager.getInstance().enqueue(serverLevel, def, origin);

        Megalith.LOGGER.info("[Megalith] Queued '{}' for placement at {} (chunk {}).",
                megalithId, origin, chunkPos);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BoundingBox makeBoundingBox(BlockPos origin, int radius) {
        return new BoundingBox(
                origin.getX() - radius, origin.getY() - 32, origin.getZ() - radius,
                origin.getX() + radius, origin.getY() + 64, origin.getZ() + radius
        );
    }

    private static StructurePieceType requireType() {
        // TYPE is set by MegalithWorldgenRegistry after DeferredRegister fires
        if (TYPE == null) throw new IllegalStateException(
                "[Megalith] MegalithStructurePiece.TYPE not yet registered.");
        return TYPE;
    }
}
