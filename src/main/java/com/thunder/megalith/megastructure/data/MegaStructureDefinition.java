package com.thunder.megalith.megastructure.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thunder.megalith.megastructure.terrain.TerrainMode;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Data model for a Megalith structure definition loaded from datapack JSON.
 *
 * JSON location: data/<modid>/megalith_structure/<name>.json
 */
public record MegaStructureDefinition(
        ResourceLocation id,
        ResourceLocation template,
        List<ResourceLocation> pieces,
        boolean oncePerWorld,
        ResourceKey<Level> dimension,
        int minDistanceFromSpawn,
        int maxDistanceFromSpawn,
        Optional<String> biomeTag,
        boolean avoidWater,
        boolean avoidVillages,
        TerrainMode terrainMode,
        int scanRadius,
        int foundationDepth,
        boolean chunkSafe,
        boolean placeInPhases,
        int maxBlocksPerTick
) {

    public static final Codec<ResourceKey<Level>> DIMENSION_CODEC =
            ResourceLocation.CODEC.xmap(
                    rl -> ResourceKey.create(Registries.DIMENSION, rl),
                    ResourceKey::location
            );

    public static final Codec<MegaStructureDefinition> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("id").forGetter(MegaStructureDefinition::id),
                    ResourceLocation.CODEC.fieldOf("template").forGetter(MegaStructureDefinition::template),
                    ResourceLocation.CODEC.listOf().optionalFieldOf("pieces", List.of()).forGetter(MegaStructureDefinition::pieces),
                    Codec.BOOL.optionalFieldOf("oncePerWorld", true).forGetter(MegaStructureDefinition::oncePerWorld),
                    DIMENSION_CODEC.optionalFieldOf("dimension",
                                    ResourceKey.create(Registries.DIMENSION, ResourceLocation.withDefaultNamespace("overworld")))
                            .forGetter(MegaStructureDefinition::dimension),
                    Codec.INT.optionalFieldOf("minDistanceFromSpawn", 0).forGetter(MegaStructureDefinition::minDistanceFromSpawn),
                    Codec.INT.optionalFieldOf("maxDistanceFromSpawn", 10000).forGetter(MegaStructureDefinition::maxDistanceFromSpawn),
                    Codec.STRING.optionalFieldOf("biomeTag").forGetter(MegaStructureDefinition::biomeTag),
                    Codec.BOOL.optionalFieldOf("avoidWater", false).forGetter(MegaStructureDefinition::avoidWater),
                    Codec.BOOL.optionalFieldOf("avoidVillages", false).forGetter(MegaStructureDefinition::avoidVillages),
                    TerrainMode.CODEC.optionalFieldOf("terrainMode", TerrainMode.NONE).forGetter(MegaStructureDefinition::terrainMode),
                    Codec.INT.optionalFieldOf("scanRadius", 32).forGetter(MegaStructureDefinition::scanRadius),
                    Codec.INT.optionalFieldOf("foundationDepth", 4).forGetter(MegaStructureDefinition::foundationDepth),
                    Codec.BOOL.optionalFieldOf("chunkSafe", true).forGetter(MegaStructureDefinition::chunkSafe),
                    Codec.BOOL.optionalFieldOf("placeInPhases", false).forGetter(MegaStructureDefinition::placeInPhases),
                    Codec.INT.optionalFieldOf("maxBlocksPerTick", 2000).forGetter(MegaStructureDefinition::maxBlocksPerTick)
            ).apply(instance, MegaStructureDefinition::new)
    );
}