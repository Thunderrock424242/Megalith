package com.thunder.megalith.megastructure.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.megalith.megastructure.data.GeneratedStructureInfo;
import com.thunder.megalith.megastructure.data.MegaStructureDefinition;
import com.thunder.megalith.megastructure.datapack.MegalithStructureLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Registers all /megalith debug commands via Brigadier.
 *
 * Requires permission level 2 (ops).
 */
public class MegalithCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext ctx) {
        dispatcher.register(
                Commands.literal("megalith")
                        .requires(src -> src.hasPermission(2))

                        // /megalith locate <id>
                        .then(Commands.literal("locate")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(MegalithCommands::executeLocate)))

                        // /megalith forceplace <id>
                        .then(Commands.literal("forceplace")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(MegalithCommands::executeForcePlace)))

                        // /megalith reset <id>
                        .then(Commands.literal("reset")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(MegalithCommands::executeReset)))

                        // /megalith list
                        .then(Commands.literal("list")
                                .executes(MegalithCommands::executeList))

                        // /megalith validate <id>
                        .then(Commands.literal("validate")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(MegalithCommands::executeValidate)))
        );
    }

    // ── locate ────────────────────────────────────────────────────────────────

    private static int executeLocate(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = parseId(ctx);
        if (id == null) { ctx.getSource().sendFailure(Component.literal("Invalid id format.")); return 0; }

        ServerLevel level = ctx.getSource().getLevel();
        Optional<BlockPos> pos = MegalithStructures.getGeneratedPos(level, id);

        if (pos.isPresent()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[Megalith] '" + id + "' generated at " + formatPos(pos.get())), false);
        } else {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[Megalith] '" + id + "' has not been generated yet."), false);
        }
        return 1;
    }

    // ── forceplace ────────────────────────────────────────────────────────────

    private static int executeForcePlace(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = parseId(ctx);
        if (id == null) { ctx.getSource().sendFailure(Component.literal("Invalid id format.")); return 0; }

        ServerLevel level = ctx.getSource().getLevel();

        if (!MegalithStructureLoader.getInstance().contains(id)) {
            ctx.getSource().sendFailure(Component.literal("[Megalith] No definition found for '" + id + "'."));
            return 0;
        }

        boolean queued = MegalithStructures.queuePlacement(level, id);
        if (queued) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "[Megalith] Placement queued for '" + id + "'."), true);
        } else {
            ctx.getSource().sendFailure(Component.literal(
                    "[Megalith] Could not queue placement for '" + id + "'. Check logs."));
        }
        return queued ? 1 : 0;
    }

    // ── reset ─────────────────────────────────────────────────────────────────

    private static int executeReset(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = parseId(ctx);
        if (id == null) { ctx.getSource().sendFailure(Component.literal("Invalid id format.")); return 0; }

        ServerLevel level = ctx.getSource().getLevel();
        MegalithStructures.resetGenerated(level, id);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "[Megalith] Reset generation state for '" + id + "'."), true);
        return 1;
    }

    // ── list ──────────────────────────────────────────────────────────────────

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        Collection<MegaStructureDefinition> defs = MegalithStructures.getDefinitions();
        if (defs.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[Megalith] No structure definitions loaded."), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder("[Megalith] Loaded structures:\n");
        for (MegaStructureDefinition def : defs) {
            Map<ResourceLocation, GeneratedStructureInfo> all =
                    com.thunder.megalith.megastructure.data.MegalithSavedData
                            .get(ctx.getSource().getLevel())
                            .getAllGenerated();
            boolean generated = all.containsKey(def.id());
            sb.append("  - ").append(def.id())
                    .append(generated ? " [GENERATED]" : " [not generated]")
                    .append("\n");
        }
        String msg = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    // ── validate ──────────────────────────────────────────────────────────────

    private static int executeValidate(CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = parseId(ctx);
        if (id == null) { ctx.getSource().sendFailure(Component.literal("Invalid id format.")); return 0; }

        Optional<MegaStructureDefinition> defOpt = MegalithStructures.getDefinition(id);
        if (defOpt.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("[Megalith] No definition found for '" + id + "'."));
            return 0;
        }

        MegaStructureDefinition def = defOpt.get();
        ServerLevel level = ctx.getSource().getLevel();

        // TODO: validate each piece template exists on disk via StructureTemplateManager
        StringBuilder sb = new StringBuilder("[Megalith] Validation for '" + id + "':\n");
        sb.append("  Template: ").append(def.template()).append("\n");
        sb.append("  Pieces:   ").append(def.pieces().size()).append("\n");
        sb.append("  Once-per-world: ").append(def.oncePerWorld()).append("\n");
        sb.append("  Dimension:      ").append(def.dimension().location()).append("\n");
        sb.append("  Terrain mode:   ").append(def.terrainMode()).append("\n");
        sb.append("  Chunk-safe:     ").append(def.chunkSafe()).append("\n");
        String result = sb.toString();

        ctx.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ResourceLocation parseId(CommandContext<CommandSourceStack> ctx) {
        String raw = StringArgumentType.getString(ctx, "id");
        try {
            return ResourceLocation.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }
}