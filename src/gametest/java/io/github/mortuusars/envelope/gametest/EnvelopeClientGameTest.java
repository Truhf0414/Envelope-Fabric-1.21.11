package io.github.mortuusars.envelope.gametest;

import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.lib.mortaar.bugger.Bugger;
import io.github.mortuusars.envelope.world.entity.CharredPigeon;
import io.github.mortuusars.envelope.world.entity.CourierBat;
import io.github.mortuusars.envelope.world.entity.Pigeon;
import io.github.mortuusars.envelope.world.entity.PigeonVariant;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.recipe.v1.FabricRecipeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Port tests (1.21.11): drives a real client through a singleplayer world.
 * Run with {@code gradlew runClientGameTest}. Results are written to {@code <run dir>/envelope_port_results/},
 * screenshots to {@code <run dir>/screenshots/}.
 */
public class EnvelopeClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Path results = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("envelope_port_results"));
        List<String> problems = new ArrayList<>();

        TestWorldSave save;
        Map<String, String> before;

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();

            runBuggerTests(singleplayer, results, problems);
            checkRecipeSync(context, singleplayer, results, problems);
            smoke(context, singleplayer, results, problems);
            checkAssets(context, results, problems);

            EntityShots.run(context, singleplayer);
            ScreenShots.run(context, singleplayer, problems);

            singleplayer.getServer().runOnServer(WorldState::populate);
            // Let the mail service pick up the new delivery (pending couriers become active on its next tick)
            context.waitTicks(10);
            before = singleplayer.getServer().computeOnServer(WorldState::describe);
            save = singleplayer.getWorldSave();
        }

        try (TestSingleplayerContext reopened = save.open()) {
            reopened.getClientWorld().waitForChunksRender();
            Map<String, String> after = reopened.getServer().computeOnServer(WorldState::describe);

            List<String> lines = new ArrayList<>();
            for (Map.Entry<String, String> entry : before.entrySet()) {
                String now = after.get(entry.getKey());
                boolean same = entry.getValue().equals(now);
                lines.add((same ? "SAME  " : "DIFF  ") + entry.getKey() + "\n   before close: " + entry.getValue() + "\n   after reopen: " + now);
                if (!same) {
                    problems.add("save/reopen: " + entry.getKey() + " changed from [" + entry.getValue() + "] to [" + now + "]");
                }
                if (entry.getValue().isBlank() || entry.getValue().equals("none")) {
                    problems.add("save/reopen: " + entry.getKey() + " was empty before closing, the comparison proves nothing");
                }
            }
            write(results.resolve("save_reopen.txt"), lines);
        }

        write(results.resolve("problems.txt"), problems.isEmpty() ? List.of("none") : problems);
        if (!problems.isEmpty()) {
            throw new AssertionError(problems.size() + " problem(s):\n" + String.join("\n", problems));
        }
    }

    /** Runs every registered Bugger test (same as {@code /mortaar debug tests}) with the singleplayer player. */
    private static void runBuggerTests(TestSingleplayerContext singleplayer, Path results, List<String> problems) {
        List<String> lines = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            List<String> output = new ArrayList<>();
            Bugger.runTests(player, message -> output.add(message.getString()));
            return output;
        });

        write(results.resolve("bugger_tests.txt"), lines);

        if (lines.stream().noneMatch(line -> line.contains("All tests are passed!"))) {
            problems.add("Bugger tests failed:\n" + String.join("\n", lines));
        }
    }

    /**
     * Port decision D-b: mailing recipes reach the client through Fabric's recipe sync.
     * The client must hold as many mailing recipes as the server loaded.
     */
    private static void checkRecipeSync(ClientGameTestContext context, TestSingleplayerContext singleplayer, Path results, List<String> problems) {
        int onServer = singleplayer.getServer().computeOnServer(server ->
              server.getRecipeManager().getRecipes().stream()
                    .filter(holder -> holder.value().getType() == Envelope.RecipeTypes.MAILING.get())
                    .toList().size());
        int envelopeOnServer = singleplayer.getServer().computeOnServer(server ->
              (int) server.getRecipeManager().getRecipes().stream()
                    .filter(holder -> holder.id().identifier().getNamespace().equals(Envelope.ID)).count());
        int onClient = context.computeOnClient(client ->
              ((FabricRecipeManager) client.level.recipeAccess()).getSynchronizedRecipes()
                    .getAllOfType(Envelope.RecipeTypes.MAILING.get()).size());
        int envelopeOnClient = context.computeOnClient(client ->
              (int) ((FabricRecipeManager) client.level.recipeAccess()).getSynchronizedRecipes().recipes().stream()
                    .filter(holder -> holder.id().identifier().getNamespace().equals(Envelope.ID)).count());

        write(results.resolve("recipe_sync.txt"), List.of(
              "mailing recipes on server: " + onServer,
              "mailing recipes on client (synchronized): " + onClient,
              "envelope recipes on server (all types): " + envelopeOnServer,
              "envelope recipes on client (synchronized, all types): " + envelopeOnClient));

        if (onServer != onClient) {
            problems.add("recipe sync: server has " + onServer + " mailing recipes, client received " + onClient);
        }
        if (envelopeOnServer != envelopeOnClient) {
            problems.add("recipe sync: server has " + envelopeOnServer + " envelope recipes, client received " + envelopeOnClient);
        }
        if (envelopeOnServer == 0) {
            problems.add("recipe sync: no envelope recipes are loaded on the server, the comparison proves nothing");
        }
    }

    /** Phase 4 turns this on: until the item definitions exist, every item is expected to have no model. */
    private static final boolean ITEM_MODELS_REQUIRED = false;

    /**
     * Every registered Envelope item must have an item model definition that resolves (1.21.4+: assets/&lt;ns&gt;/items)
     * and a translated name; every Envelope block must have a translated name.
     */
    private static void checkAssets(ClientGameTestContext context, Path results, List<String> problems) {
        List<String> lines = context.computeOnClient(client -> {
            List<String> output = new ArrayList<>();
            int items = 0, missingModels = 0, missingNames = 0;
            for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
                if (!id.getNamespace().equals(Envelope.ID)) continue;
                items++;
                net.minecraft.resources.Identifier modelId = new ItemStack(item).get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
                boolean hasModel = modelId != null
                      && !(client.getModelManager().getItemModel(modelId) instanceof net.minecraft.client.renderer.item.MissingItemModel);
                boolean hasName = net.minecraft.client.resources.language.I18n.exists(item.getDescriptionId());
                if (!hasModel) missingModels++;
                if (!hasName) missingNames++;
                output.add((hasModel ? "model ok  " : "NO MODEL  ") + (hasName ? "name ok  " : "NO NAME  ") + id);
            }
            output.add(0, "items: " + items + ", without item model: " + missingModels + ", without name: " + missingNames);
            return output;
        });
        write(results.resolve("assets.txt"), lines);

        String summary = lines.getFirst();
        if (summary.startsWith("items: 0")) {
            problems.add("assets: no envelope items found, the check looked at nothing");
        }
        if (!summary.endsWith("without name: 0")) {
            problems.add("assets: some items have no translated name -> " + summary);
        }
        if (ITEM_MODELS_REQUIRED && !summary.contains("without item model: 0,")) {
            problems.add("assets: some items have no item model -> " + summary);
        }
    }

    /** Basic "does it run at all" actions: commands, spawning, item use. Any server log error is a problem. */
    private static void smoke(ClientGameTestContext context, TestSingleplayerContext singleplayer, Path results, List<String> problems) {
        List<String> lines = new ArrayList<>();
        runAndRecord(singleplayer, lines, problems, "command: envelope mailbox list", server ->
              server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "envelope mailbox list"));
        runAndRecord(singleplayer, lines, problems, "spawn pigeon, courier bat, charred pigeon", server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            for (var type : List.of(Envelope.EntityTypes.PIGEON.get(), Envelope.EntityTypes.COURIER_BAT.get(), Envelope.EntityTypes.CHARRED_PIGEON.get())) {
                Entity entity = type.create(level, EntitySpawnReason.COMMAND);
                if (entity == null) throw new IllegalStateException(type + " could not be created");
                entity.snapTo(player.getX() + 3, player.getY() + 1, player.getZ() + 3, 0, 0);
                if (!level.addFreshEntity(entity)) throw new IllegalStateException(type + " was not added to the level");
            }
        });
        context.waitTicks(40); // let them tick (AI, mixins on tick paths)
        runAndRecord(singleplayer, lines, problems, "entities survived 40 ticks, then removed", server -> {
            ServerLevel level = server.overworld();
            int count = 0;
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Pigeon || entity instanceof CourierBat || entity instanceof CharredPigeon) {
                    count++;
                    entity.discard();
                }
            }
            if (count != 3) throw new IllegalStateException("expected 3 envelope entities after 40 ticks, found " + count);
        });
        runAndRecord(singleplayer, lines, problems, "place mailbox, package, letter blocks and break them", server -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            BlockPos base = player.blockPosition().offset(4, 0, 0);
            int i = 0;
            for (var block : List.of(Envelope.Blocks.MAILBOX.get(), Envelope.Blocks.PACKAGE.get(), Envelope.Blocks.LETTER.get())) {
                BlockPos pos = base.offset(i++, 0, 0);
                level.setBlock(pos, block.defaultBlockState(), 3);
                if (!level.getBlockState(pos).is(block)) throw new IllegalStateException(block + " was not placed");
                level.destroyBlock(pos, false);
            }
        });
        write(results.resolve("smoke.txt"), lines);
    }

    private static void runAndRecord(TestSingleplayerContext singleplayer, List<String> lines, List<String> problems, String name, Consumer<MinecraftServer> action) {
        String error = singleplayer.getServer().computeOnServer(server -> {
            try {
                action.accept(server);
                return null;
            } catch (Throwable e) {
                return e.toString();
            }
        });
        lines.add((error == null ? "OK    " : "FAIL  ") + name + (error == null ? "" : " -> " + error));
        if (error != null) {
            problems.add("smoke: " + name + " -> " + error);
        }
    }

    static void write(Path file, List<String> lines) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Screenshots of every entity look, one at a time in front of a fixed camera. Compared by eye in phase 3c. */
    static final class EntityShots {
        static void run(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SPECTATOR);
                server.overworld().setDayTime(6000);
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule advance_time false");
                BlockPos ground = player.blockPosition();
                player.teleportTo(server.overworld(), ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, java.util.Set.of(), 0, 10, true);
            });
            context.runOnClient(client -> {
                client.options.hideGui = true;
                client.options.fov().set(30);
            });
            context.waitTicks(10);

            Map<String, ResourceKey<PigeonVariant>> variants = new LinkedHashMap<>();
            variants.put("gray", PigeonVariant.GRAY);
            variants.put("brown", PigeonVariant.BROWN);
            variants.put("white", PigeonVariant.WHITE);
            variants.put("passenger", PigeonVariant.PASSENGER);
            variants.put("charred_variant", PigeonVariant.CHARRED);
            variants.put("archimedes", PigeonVariant.ARCHIMEDES);
            variants.put("ghost", PigeonVariant.GHOST);

            for (Map.Entry<String, ResourceKey<PigeonVariant>> variant : variants.entrySet()) {
                shot(context, singleplayer, "entity_pigeon_" + variant.getKey(), level -> pigeon(level, variant.getValue(), false, false, false));
            }
            shot(context, singleplayer, "entity_pigeon_gray_sitting", level -> pigeon(level, PigeonVariant.GRAY, true, false, false));
            shot(context, singleplayer, "entity_pigeon_gray_backpack", level -> pigeon(level, PigeonVariant.GRAY, false, true, false));
            shot(context, singleplayer, "entity_pigeon_gray_baby", level -> pigeon(level, PigeonVariant.GRAY, false, false, true));
            shot(context, singleplayer, "entity_pigeon_ghost_backpack", level -> pigeon(level, PigeonVariant.GHOST, false, true, false));
            shot(context, singleplayer, "entity_charred_pigeon", level -> {
                CharredPigeon pigeon = Envelope.EntityTypes.CHARRED_PIGEON.get().create(level, EntitySpawnReason.COMMAND);
                return pigeon;
            });
            shot(context, singleplayer, "entity_charred_pigeon_backpack", level -> {
                CharredPigeon pigeon = Envelope.EntityTypes.CHARRED_PIGEON.get().create(level, EntitySpawnReason.COMMAND);
                pigeon.setCarriedMail(new ItemStack(Envelope.Items.LETTER.get()));
                return pigeon;
            });
            shot(context, singleplayer, "entity_courier_bat", level -> Envelope.EntityTypes.COURIER_BAT.get().create(level, EntitySpawnReason.COMMAND));
            shot(context, singleplayer, "entity_courier_bat_backpack", level -> {
                CourierBat bat = Envelope.EntityTypes.COURIER_BAT.get().create(level, EntitySpawnReason.COMMAND);
                bat.setHasMail(true);
                return bat;
            });

            context.runOnClient(client -> {
                client.options.hideGui = false;
                client.options.fov().set(70);
            });
            singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.CREATIVE));
            context.waitTicks(5);
        }

        private static Pigeon pigeon(ServerLevel level, ResourceKey<PigeonVariant> variant, boolean sitting, boolean mail, boolean baby) {
            Pigeon pigeon = Envelope.EntityTypes.PIGEON.get().create(level, EntitySpawnReason.COMMAND);
            pigeon.setVariant(PigeonVariant.get(level.registryAccess(), variant).orElseThrow());
            pigeon.setSitting(sitting);
            pigeon.setHasMail(mail);
            if (baby) pigeon.setBaby(true);
            return pigeon;
        }

        private static void shot(ClientGameTestContext context, TestSingleplayerContext singleplayer, String name, Function<ServerLevel, ? extends Entity> factory) {
            int id = singleplayer.getServer().computeOnServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                Entity entity = factory.apply(level);
                // Three quarter view: 2.2 blocks in front of the camera (which looks south, slightly down), turned 35 degrees
                entity.snapTo(player.getX(), player.getY() + 0.9, player.getZ() + 2.2, 145, 0);
                entity.setYHeadRot(145);
                entity.setYBodyRot(145);
                entity.setNoGravity(true);
                if (entity instanceof Mob mob) {
                    mob.setNoAi(true);
                    mob.setPersistenceRequired();
                }
                level.addFreshEntity(entity);
                return entity.getId();
            });
            context.waitTicks(8);
            context.takeScreenshot(name);
            singleplayer.getServer().runOnServer(server -> {
                Entity entity = server.overworld().getEntity(id);
                if (entity != null) entity.discard();
            });
            context.waitTicks(3);
        }
    }
}
