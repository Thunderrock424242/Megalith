package com.thunder.megalith.megastructure.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.thunder.megalith.Megalith;
import com.thunder.megalith.megastructure.data.MegaStructureDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads all Megalith structure definitions from:
 *   data/<namespace>/megalith_structure/<name>.json
 *
 * Registered as a server reload listener so definitions refresh on /reload.
 */
public class MegalithStructureLoader extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FOLDER = "megalith_structure";
    private static final MegalithStructureLoader INSTANCE = new MegalithStructureLoader();

    private Map<ResourceLocation, MegaStructureDefinition> definitions = Collections.emptyMap();

    private MegalithStructureLoader() {
        super(GSON, FOLDER);
    }

    public static MegalithStructureLoader getInstance() {
        return INSTANCE;
    }

    // ── ReloadListener ───────────────────────────────────────────────────────

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, MegaStructureDefinition> loaded = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : objects.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try {
                MegaStructureDefinition def = MegaStructureDefinition.CODEC
                        .parse(JsonOps.INSTANCE, entry.getValue())
                        .resultOrPartial(err -> Megalith.LOGGER.error("[Megalith] Error parsing structure '{}': {}", fileId, err))
                        .orElse(null);
                if (def != null) {
                    loaded.put(def.id(), def);
                    Megalith.LOGGER.debug("[Megalith] Loaded structure definition: {}", def.id());
                }
            } catch (Exception e) {
                Megalith.LOGGER.error("[Megalith] Failed to load structure '{}': {}", fileId, e.getMessage());
            }
        }

        definitions = Collections.unmodifiableMap(loaded);
        Megalith.LOGGER.info("[Megalith] Loaded {} structure definition(s).", definitions.size());
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public Optional<MegaStructureDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Collection<MegaStructureDefinition> getAll() {
        return definitions.values();
    }

    public boolean contains(ResourceLocation id) {
        return definitions.containsKey(id);
    }
}