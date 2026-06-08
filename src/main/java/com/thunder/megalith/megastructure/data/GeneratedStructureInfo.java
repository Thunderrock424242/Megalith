package com.thunder.megalith.megastructure.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Snapshot of a structure that has been generated (or is in progress) in a world.
 */
public class GeneratedStructureInfo {

    private final ResourceLocation id;
    private final ResourceKey<Level> dimension;
    private final BlockPos pos;
    private boolean complete;

    public GeneratedStructureInfo(ResourceLocation id, ResourceKey<Level> dimension, BlockPos pos, boolean complete) {
        this.id = id;
        this.dimension = dimension;
        this.pos = pos;
        this.complete = complete;
    }

    public ResourceLocation getId() { return id; }
    public ResourceKey<Level> getDimension() { return dimension; }
    public BlockPos getPos() { return pos; }
    public boolean isComplete() { return complete; }
    public void setComplete(boolean complete) { this.complete = complete; }

    @Override
    public String toString() {
        return "GeneratedStructureInfo{id=" + id + ", dim=" + dimension.location() +
                ", pos=" + pos + ", complete=" + complete + "}";
    }
}