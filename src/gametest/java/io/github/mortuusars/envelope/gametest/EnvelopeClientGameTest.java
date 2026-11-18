package io.github.mortuusars.envelope.gametest;

import io.github.mortuusars.envelope.Envelope;
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

        // -Denvelope.test.externalServer=host:port : only join a dedicated server that runs in another process
        // (gradlew prodServer) and check what needs two real sides. See ExternalServer.
        String external = System.getProperty("envelope.test.externalServer");
        if (external != null) {
            ExternalServer.run(context, external, results, problems);
            write(results.resolve("problems.txt"), problems.isEmpty() ? List.of("none") : problems);
            if (!problems.isEmpty()) {
                throw new AssertionError(problems.size() + " problem(s):\n" + String.join("\n", problems));
            }
            return;
        }

        TestWorldSave save;
        Map<String, String> before;

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();

            runBuggerTests(singleplayer, results, problems);
            checkRecipeSync(context, singleplayer, results, problems);
            smoke(context, singleplayer, results, problems);
            checkAssets(context, results, problems);
            DataCounts.check(singleplayer, results, problems, DATA_AND_ASSETS_COMPLETE);

            EntityShots.run(context, singleplayer);
            ScreenShots.run(context, singleplayer, problems);
            ContainerClicks.run(context, singleplayer, results, problems);
            MailServices.run(context, singleplayer, results, problems);

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

        dedicatedServer(context, results, problems);

        write(results.resolve("problems.txt"), problems.isEmpty() ? List.of("none") : problems);
        if (!problems.isEmpty()) {
            throw new AssertionError(problems.size() + " problem(s):\n" + String.join("\n", problems));
        }
    }

    /**
     * Two sides for real: a dedicated server in its own thread and this client connected to it over the network
     * stack. Checks what singleplayer cannot: the server starts without client classes, the join handshake accepts
     * Envelope's registries and packets, recipes arrive through the sync, and a menu opened by the server shows up.
     */
    private static void dedicatedServer(ClientGameTestContext context, Path results, List<String> problems) {
        List<String> lines = new ArrayList<>();
        try (net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext server = context.worldBuilder().createServer()) {
            lines.add("OK    dedicated server started");
            try (net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection connection = server.connect()) {
                connection.getClientWorld().waitForChunksRender();
                lines.add("OK    client joined and rendered chunks");

                int onServer = server.computeOnServer(s -> (int) s.getRecipeManager().getRecipes().stream()
                      .filter(holder -> holder.value().getType() == Envelope.RecipeTypes.MAILING.get()).count());
                int onClient = context.computeOnClient(client ->
                      ((FabricRecipeManager) client.level.recipeAccess()).getSynchronizedRecipes()
                            .getAllOfType(Envelope.RecipeTypes.MAILING.get()).size());
                lines.add((onServer == onClient && onServer > 0 ? "OK    " : "FAIL  ") + "mailing recipes: server " + onServer + ", client " + onClient);
                if (onServer != onClient || onServer == 0) {
                    problems.add("dedicated server: mailing recipes server " + onServer + ", client " + onClient);
                }

                // A server-opened menu with Envelope's own packets: place a mailbox with mail and open it for the player
                server.runOnServer(s -> {
                    ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
                    ServerLevel level = player.level();
                    s.getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
                    player.setGameMode(GameType.CREATIVE);
                    player.teleportTo(level, player.getX(), player.getY(), player.getZ(), java.util.Set.of(), 0, 0, true);
                    // At eye height, two blocks ahead (the player now looks straight south); cleared around it
                    BlockPos pos = player.blockPosition().offset(0, 1, 2);
                    level.setBlock(pos.north(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    level.setBlock(pos, Envelope.Blocks.MAILBOX.get().defaultBlockState(), 3);
                    io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity mailbox =
                          (io.github.mortuusars.envelope.world.block.mailbox.MailboxBlockEntity) level.getBlockEntity(pos);
                    mailbox.setAddress(new io.github.mortuusars.envelope.world.mail.address.type.BlockAddress("Dedicated server mailbox"));
                    mailbox.addMail(new ItemStack(Envelope.Items.LETTER.get()));
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                });
                context.waitTicks(20);
                // Right click the block like a player: the open-menu packet and the mail list travel over the connection
                context.getInput().pressKey(options -> options.keyUse);
                context.waitForScreen(io.github.mortuusars.envelope.client.gui.screen.MailboxScreen.class);
                context.waitTicks(10);
                context.takeScreenshot("dedicated_server_mailbox");
                lines.add("OK    mailbox menu opened by the server is shown on the client");
                context.runOnClient(client -> { if (client.screen != null) client.screen.onClose(); });
                context.waitTicks(5);

                // The test command through the real command dispatcher, as an operator on a dedicated server
                List<String> output = server.computeOnServer(s -> {
                    ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
                    List<String> out = new ArrayList<>();
                    net.minecraft.commands.CommandSource capture = new net.minecraft.commands.CommandSource() {
                        @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { out.add(message.getString()); }
                        @Override public boolean acceptsSuccess() { return true; }
                        @Override public boolean acceptsFailure() { return true; }
                        @Override public boolean shouldInformAdmins() { return false; }
                    };
                    s.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSource(capture), "mortaar debug tests");
                    return out;
                });
                boolean passed = output.stream().anyMatch(line -> line.contains("All tests are passed!"));
                lines.add((passed ? "OK    " : "FAIL  ") + "/mortaar debug tests as an operator on the dedicated server: " + String.join(" | ", output));
                if (!passed) {
                    problems.add("dedicated server: bugger tests -> " + String.join(" | ", output));
                }
            }
        } catch (Throwable e) {
            lines.add("FAIL  " + e);
            problems.add("dedicated server: " + e);
        }
        write(results.resolve("dedicated_server.txt"), lines);
    }

    /** Runs every registered Bugger test with {@code /mortaar debug tests}, executed as the singleplayer player. */
    private static void runBuggerTests(TestSingleplayerContext singleplayer, Path results, List<String> problems) {
        List<String> lines = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            List<String> output = new ArrayList<>();
            // Through the real command, as the player and with the player's own permissions, so the command
            // registration and its permission check are exercised too. Feedback goes to this capturing source.
            net.minecraft.commands.CommandSource capture = new net.minecraft.commands.CommandSource() {
                @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { output.add(message.getString()); }
                @Override public boolean acceptsSuccess() { return true; }
                @Override public boolean acceptsFailure() { return true; }
                @Override public boolean shouldInformAdmins() { return false; }
            };
            // 1) With the player's own permissions. The test world has cheats off, so like any admin command this is
            //    expected to be refused; what the game answers is recorded either way.
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSource(capture), "mortaar debug tests");
            List<String> asPlayer = new ArrayList<>(output);
            output.clear();
            // 2) As the same player with operator permissions (what "cheats on" or /op gives): must run the tests.
            server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSource(capture)
                  .withPermission(net.minecraft.server.permissions.PermissionSet.ALL_PERMISSIONS), "mortaar debug tests");
            if (output.isEmpty()) {
                output.add("The command '/mortaar debug tests' produced no output even with operator permissions.");
            }
            output.add("");
            output.add("(same command as the player without operator permissions answered: " + String.join(" | ", asPlayer) + ")");
            return output;
        });

        write(results.resolve("bugger_tests.txt"), lines);

        boolean passed = lines.stream().anyMatch(line -> line.contains("All tests are passed!"));
        boolean counted = lines.stream().anyMatch(line -> line.matches("Running \\d{2,} bugger tests\\."));
        boolean failures = lines.stream().anyMatch(line -> line.startsWith("Failed:") || line.startsWith("Passed:"));
        if (!passed || !counted || failures) {
            problems.add("Bugger tests failed:\n" + String.join("\n", lines));
        }
        // The same command without operator permissions must be refused (it is an admin command)
        if (lines.stream().noneMatch(line -> line.startsWith("(same command as the player without operator permissions answered: Unknown or incomplete command"))) {
            problems.add("Bugger tests: the command was not refused for a player without operator permissions:\n" + String.join("\n", lines));
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
        // Only Envelope's own serializers are opted into the sync (EnvelopeFabric). Envelope's plain shaped and
        // shapeless recipes use vanilla serializers, which are not Envelope's to synchronize.
        int envelopeOnServer = singleplayer.getServer().computeOnServer(server ->
              (int) server.getRecipeManager().getRecipes().stream()
                    .filter(holder -> holder.id().identifier().getNamespace().equals(Envelope.ID))
                    .filter(holder -> net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER
                          .getKey(holder.value().getSerializer()).getNamespace().equals(Envelope.ID)).count());
        int onClient = context.computeOnClient(client ->
              ((FabricRecipeManager) client.level.recipeAccess()).getSynchronizedRecipes()
                    .getAllOfType(Envelope.RecipeTypes.MAILING.get()).size());
        int envelopeOnClient = context.computeOnClient(client ->
              (int) ((FabricRecipeManager) client.level.recipeAccess()).getSynchronizedRecipes().recipes().stream()
                    .filter(holder -> holder.id().identifier().getNamespace().equals(Envelope.ID)).count());

        write(results.resolve("recipe_sync.txt"), List.of(
              "mailing recipes on server: " + onServer,
              "mailing recipes on client (synchronized): " + onClient,
              "envelope recipes with an envelope serializer on server: " + envelopeOnServer,
              "envelope recipes on client (synchronized): " + envelopeOnClient));

        if (onServer != onClient) {
            problems.add("recipe sync: server has " + onServer + " mailing recipes, client received " + onClient);
        }
        // Same recipes, not just as many: ids of the recipes with an Envelope serializer on the server against the
        // ids of every Envelope recipe the client holds
        java.util.Set<String> serverIds = singleplayer.getServer().computeOnServer(server ->
              server.getRecipeManager().getRecipes().stream()
                    .filter(holder -> holder.id().identifier().getNamespace().equals(Envelope.ID))
                    .filter(holder -> net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER
                          .getKey(holder.value().getSerializer()).getNamespace().equals(Envelope.ID))
                    .map(holder -> holder.id().identifier().toString())
                    .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new)));
        java.util.Set<String> clientIds = context.computeOnClient(client ->
              ((FabricRecipeManager) client.level.recipeAccess()).getSynchronizedRecipes().recipes().stream()
                    .filter(holder -> holder.id().identifier().getNamespace().equals(Envelope.ID))
                    .map(holder -> holder.id().identifier().toString())
                    .collect(java.util.stream.Collectors.toCollection(java.util.TreeSet::new)));
        if (!serverIds.equals(clientIds)) {
            java.util.Set<String> missing = new java.util.TreeSet<>(serverIds);
            missing.removeAll(clientIds);
            java.util.Set<String> extra = new java.util.TreeSet<>(clientIds);
            extra.removeAll(serverIds);
            problems.add("recipe sync: ids differ. Not on the client: " + missing + ". Only on the client: " + extra);
        }
        if (envelopeOnServer != envelopeOnClient) {
            problems.add("recipe sync: server has " + envelopeOnServer + " envelope recipes, client received " + envelopeOnClient);
        }
        if (DATA_AND_ASSETS_COMPLETE && onServer == 0) {
            // Phase 4 (same switch as the item models): once the recipe JSON is converted there must be mailing recipes,
            // otherwise "0 on both sides" would pass without testing the sync of the type JEI needs.
            problems.add("recipe sync: no mailing recipes are loaded, the mailing comparison proves nothing");
        }
        if (envelopeOnServer == 0) {
            problems.add("recipe sync: no envelope recipes are loaded on the server, the comparison proves nothing");
        }
    }

    /** On since phase 4: every item must have a model, every data file must load, mailing recipes must exist. */
    static final boolean DATA_AND_ASSETS_COMPLETE = true;

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
            int blocks = 0, states = 0, missingStates = 0, missingBlockNames = 0;
            for (net.minecraft.world.level.block.Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
                net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
                if (!id.getNamespace().equals(Envelope.ID)) continue;
                blocks++;
                boolean hasName = net.minecraft.client.resources.language.I18n.exists(block.getDescriptionId());
                if (!hasName) missingBlockNames++;
                int missingHere = 0;
                for (net.minecraft.world.level.block.state.BlockState state : block.getStateDefinition().getPossibleStates()) {
                    states++;
                    if (client.getModelManager().getBlockModelShaper().getBlockModel(state) == client.getModelManager().getMissingBlockStateModel()) {
                        missingHere++;
                    }
                }
                missingStates += missingHere;
                output.add((missingHere == 0 ? "model ok  " : "NO MODEL for " + missingHere + " states  ") + (hasName ? "name ok  " : "NO NAME  ") + "block " + id);
            }
            output.add(0, "blocks: " + blocks + " (" + states + " block states), states without model: " + missingStates + ", without name: " + missingBlockNames);
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
        if (DATA_AND_ASSETS_COMPLETE && !summary.contains("without item model: 0,")) {
            problems.add("assets: some items have no item model -> " + summary);
        }
        String blockSummary = lines.get(1);
        if (blockSummary.startsWith("blocks: 0") || !blockSummary.endsWith("states without model: 0, without name: 0")) {
            problems.add("assets: block models or names -> " + blockSummary);
        }
    }

    /** Basic "does it run at all" actions on the server side: a command, spawning and ticking entities, placing and
     *  removing blocks directly, placing structures. Player interactions are covered by ScreenShots. */
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
        // Phase 4: every Envelope structure must generate when placed (old-format pieces go through the data fixer,
        // and a jigsaw pool or processor that no longer parses only shows up when the structure is built).
        List<String> structures = singleplayer.getServer().computeOnServer(server ->
              server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).listElementIds()
                    .map(key -> key.identifier()).filter(id -> id.getNamespace().equals(Envelope.ID)).map(String::valueOf).sorted().toList());
        if (structures.isEmpty()) {
            problems.add("smoke: no envelope structures are registered, nothing was placed");
        }
        int index = 0;
        for (String structure : structures) {
            // Inside the loaded area around the player (spawn), spread out on a ring so they do not overlap much
            double angle = Math.PI * 2 * index++ / structures.size();
            int x = (int) (Math.cos(angle) * 48);
            int z = (int) (Math.sin(angle) * 48);
            runAndRecord(singleplayer, lines, problems, "place structure " + structure, server -> {
                int[] result = {Integer.MIN_VALUE};
                List<String> feedback = new ArrayList<>();
                net.minecraft.commands.CommandSource capture = new net.minecraft.commands.CommandSource() {
                    @Override public void sendSystemMessage(net.minecraft.network.chat.Component message) { feedback.add(message.getString()); }
                    @Override public boolean acceptsSuccess() { return true; }
                    @Override public boolean acceptsFailure() { return true; }
                    @Override public boolean shouldInformAdmins() { return false; }
                };
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(capture)
                      .withCallback((success, value) -> result[0] = success ? value : -1), "place structure " + structure + " " + x + " -60 " + z);
                if (result[0] <= 0) {
                    throw new IllegalStateException("command result " + result[0] + ", feedback: " + feedback);
                }
            });
        }
        // The dovecote pieces carry pigeons with saved attributes (their ids were renamed in phase 4): the pigeons
        // must exist after placing, with the movement speed stored in the piece (0.4), not the default (0.2).
        runAndRecord(singleplayer, lines, problems, "pigeons from placed dovecotes keep their saved attributes", server -> {
            List<Double> speeds = new ArrayList<>();
            for (Entity entity : server.overworld().getAllEntities()) {
                if (entity instanceof Pigeon pigeon) {
                    speeds.add(pigeon.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
                }
            }
            if (speeds.isEmpty()) throw new IllegalStateException("no pigeons were generated by the placed structures");
            if (speeds.stream().anyMatch(speed -> Math.abs(speed - 0.4) > 1.0E-6)) {
                throw new IllegalStateException("movement speed base values: " + speeds + " (the pieces store 0.4)");
            }
            lines.add("      " + speeds.size() + " pigeons, movement speed base " + speeds.getFirst());
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

            // Every Envelope block in rows in front of the camera (phase 4: models, textures, block states)
            context.runOnClient(client -> client.options.fov().set(70));
            int placed = singleplayer.getServer().computeOnServer(server -> {
                ServerLevel level = server.overworld();
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                List<net.minecraft.world.level.block.Block> blocks = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
                      .filter(block -> net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals(Envelope.ID))
                      .toList();
                int perRow = 7;
                for (int i = 0; i < blocks.size(); i++) {
                    BlockPos pos = player.blockPosition().offset((i % perRow) * 2 - (perRow - 1), 0, 5 + (i / perRow) * 2);
                    level.setBlock(pos, blocks.get(i).defaultBlockState(), 3);
                }
                return blocks.size();
            });
            context.waitTicks(20);
            singleplayer.getClientWorld().waitForChunksRender();
            context.takeScreenshot("blocks_all_" + placed);

            context.runOnClient(client -> client.options.hideGui = false);
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
                if (!level.addFreshEntity(entity)) {
                    throw new IllegalStateException(name + ": the entity was not added to the world");
                }
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
