package com.thunder.megalith.megastructure.placement;

import com.thunder.megalith.megastructure.data.MegaStructureDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Represents a pending structure placement broken into phases (one piece per phase).
 *
 * Each tick the manager calls {@link #tick(ServerLevel)} until {@link #isComplete()}.
 */
public class MegalithPlacementTask {

    private final ResourceLocation structureId;
    private final MegaStructureDefinition definition;
    private final BlockPos origin;
    private final Deque<ResourceLocation> remainingPieces;
    private boolean complete = false;

    public MegalithPlacementTask(MegaStructureDefinition definition, BlockPos origin) {
        this.structureId   = definition.id();
        this.definition    = definition;
        this.origin        = origin;
        this.remainingPieces = new ArrayDeque<>();

        // Queue: main template first, then additional pieces
        remainingPieces.add(definition.template());
        remainingPieces.addAll(definition.pieces());
    }

    /**
     * Process the next piece (or a block batch for the current piece if per-block mode is added).
     *
     * @return true if this tick accomplished work, false if already complete.
     */
    public boolean tick(ServerLevel level) {
        if (complete) return false;

        if (remainingPieces.isEmpty()) {
            complete = true;
            return false;
        }

        ResourceLocation nextPiece = remainingPieces.poll();
        // Pieces are placed at the same origin; real offset logic can be added here.
        // TODO: implement per-piece relative offsets (read from definition or template metadata)
        MegalithTemplatePlacer.placePiece(level, nextPiece, origin, definition.maxBlocksPerTick());

        if (remainingPieces.isEmpty()) {
            complete = true;
        }
        return true;
    }

    public boolean isComplete()            { return complete; }
    public ResourceLocation getStructureId() { return structureId; }
    public BlockPos getOrigin()            { return origin; }
    public MegaStructureDefinition getDefinition() { return definition; }
}