package com.thunder.megalith.megastructure.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Persists which Megalith structures have been generated in a given world/level.
 * Stored in the overworld's saveddata folder as "megalith_structures".
 */
public class MegalithSavedData extends SavedData {

    private static final String DATA_KEY = "megalith_structures";
    private final Map<ResourceLocation, GeneratedStructureInfo> generated = new HashMap<>();

    // ── Factory ──────────────────────────────────────────────────────────────

    public static MegalithSavedData get(ServerLevel level) {
        // SavedData is attached to the overworld so it persists regardless of which
        // dimension triggers the call.
        ServerLevel overworld = level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MegalithSavedData::new, MegalithSavedData::load),
                DATA_KEY
        );
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public boolean hasGenerated(ResourceLocation id) {
        return generated.containsKey(id);
    }

    public void markGenerated(ResourceLocation id, ResourceKey<Level> dimension, BlockPos pos) {
        generated.put(id, new GeneratedStructureInfo(id, dimension, pos, false));
        setDirty();
    }

    public void markComplete(ResourceLocation id) {
        GeneratedStructureInfo info = generated.get(id);
        if (info != null) {
            info.setComplete(true);
            setDirty();
        }
    }

    public void reset(ResourceLocation id) {
        generated.remove(id);
        setDirty();
    }

    public Optional<BlockPos> getGeneratedPos(ResourceLocation id) {
        return Optional.ofNullable(generated.get(id)).map(GeneratedStructureInfo::getPos);
    }

    public Map<ResourceLocation, GeneratedStructureInfo> getAllGenerated() {
        return Collections.unmodifiableMap(generated);
    }

    // ── NBT serialization ────────────────────────────────────────────────────

    public static MegalithSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        MegalithSavedData data = new MegalithSavedData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.parse(entry.getString("id"));
            ResourceLocation dimRl = ResourceLocation.parse(entry.getString("dimension"));
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimRl);
            BlockPos pos = new BlockPos(entry.getInt("x"), entry.getInt("y"), entry.getInt("z"));
            boolean complete = entry.getBoolean("complete");
            data.generated.put(id, new GeneratedStructureInfo(id, dimension, pos, complete));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (GeneratedStructureInfo info : generated.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", info.getId().toString());
            entry.putString("dimension", info.getDimension().location().toString());
            entry.putInt("x", info.getPos().getX());
            entry.putInt("y", info.getPos().getY());
            entry.putInt("z", info.getPos().getZ());
            entry.putBoolean("complete", info.isComplete());
            list.add(entry);
        }
        tag.put("entries", list);
        return tag;
    }
}
