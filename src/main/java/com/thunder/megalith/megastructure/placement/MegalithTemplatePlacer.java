package com.thunder.megalith.megastructure.placement;

import com.thunder.megalith.Megalith;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.Optional;

/**
 * Places a Minecraft structure template (.nbt) by ResourceLocation.
 *
 * Rotation and mirroring are set to NONE by default; extend {@link StructurePlaceSettings}
 * to add more complex transforms.
 */
public class MegalithTemplatePlacer {

    /**
     * Load and place a single structure piece.
     *
     * @param level          Target server level
     * @param templateId     ResourceLocation of the .nbt template (e.g. examplemod:my_struct/core)
     * @param origin         Bottom-NW corner of placement
     * @param maxBlocksHint  Not yet enforced per-block; reserved for future per-block decomposition
     */
    public static void placePiece(ServerLevel level, ResourceLocation templateId,
                                  BlockPos origin, int maxBlocksHint) {
        // TODO: verify StructureTemplateManager API in NeoForge 1.21.1
        StructureTemplateManager manager = level.getStructureManager();
        Optional<StructureTemplate> opt = manager.get(templateId);

        if (opt.isEmpty()) {
            Megalith.LOGGER.warn("[Megalith] Template not found: {}", templateId);
            return;
        }

        StructureTemplate template = opt.get();

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setMirror(Mirror.NONE)
                .setIgnoreEntities(false);

        // TODO: per-block phased placement — decompose placeInWorld into block batches
        //       tracked across ticks to respect maxBlocksPerTick.
        boolean placed = template.placeInWorld(level, origin, origin, settings, level.getRandom(), 3);
        if (placed) {
            Megalith.LOGGER.debug("[Megalith] Placed piece '{}' at {}.", templateId, origin);
        } else {
            Megalith.LOGGER.warn("[Megalith] Failed to place piece '{}' at {}.", templateId, origin);
        }
    }
}