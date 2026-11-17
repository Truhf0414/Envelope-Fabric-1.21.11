package io.github.mortuusars.envelope.gametest;

import io.github.mortuusars.envelope.Envelope;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.recipe.v1.FabricRecipeManager;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Port tests (1.21.11, phase 5): the client joins a dedicated server that runs in ANOTHER process
 * ({@code gradlew prodServer}), so nothing is shared between the two sides except the network connection.
 * The script that starts this (see _notes/tools/two_process_test.py) prepares the server over RCON and tells the
 * client what to expect through files in the client's run directory:
 * <ul>
 *     <li>the client joins and waits for the world</li>
 *     <li>writes how many mailing recipes it received, and their ids ({@code external_recipes.txt})</li>
 *     <li>writes {@code external_joined.txt}, then waits for {@code external_go.txt} (the script has by then placed a
 *     mailbox in front of the player), right clicks and expects the mailbox screen</li>
 * </ul>
 */
final class ExternalServer {
    private ExternalServer() {
    }

    static void run(ClientGameTestContext context, String address, Path results, List<String> problems) {
        List<String> lines = new ArrayList<>();
        try {
            Files.createDirectories(results);
            Files.deleteIfExists(results.resolve("external_go.txt"));
            Files.deleteIfExists(results.resolve("external_joined.txt"));

            context.runOnClient(client -> ConnectScreen.startConnecting(new TitleScreen(), client, ServerAddress.parseString(address),
                  new ServerData("Envelope port test", address, ServerData.Type.OTHER), false, null));
            context.waitFor(client -> client.level != null && client.player != null && client.screen == null, 60 * 20);
            context.waitTicks(40);
            lines.add("OK    joined " + address);

            List<String> ids = context.computeOnClient(client ->
                  ((FabricRecipeManager) client.level.recipeAccess()).getSynchronizedRecipes()
                        .getAllOfType(Envelope.RecipeTypes.MAILING.get()).stream()
                        .map(holder -> holder.id().identifier().toString()).sorted().toList());
            EnvelopeClientGameTest.write(results.resolve("external_recipes.txt"), ids);
            lines.add((ids.isEmpty() ? "FAIL  " : "OK    ") + "mailing recipes received from the server: " + ids.size());
            if (ids.isEmpty()) {
                problems.add("external server: no mailing recipes were received");
            }

            String name = context.computeOnClient(client -> client.player.getGameProfile().name());
            EnvelopeClientGameTest.write(results.resolve("external_joined.txt"), List.of(name));
            context.waitFor(client -> Files.exists(results.resolve("external_go.txt")), 60 * 20);
            context.waitTicks(20);

            context.getInput().pressKey(options -> options.keyUse);
            // A mailbox placed by command has no address yet, so depending on that the server answers with the
            // mailbox menu or with one of Envelope's address screens; either way it is an Envelope screen that only
            // opens when the server's packet arrives and is understood.
            context.waitFor(client -> client.screen != null
                  && client.screen.getClass().getName().startsWith("io.github.mortuusars.envelope.client.gui.screen."), 20 * 20);
            context.waitTicks(10);
            String screen = context.computeOnClient(client -> client.screen.getClass().getSimpleName());
            context.takeScreenshot("external_server_" + screen);
            lines.add("OK    right click on the mailbox opened " + screen + " (sent by the other process's server)");
            context.runOnClient(client -> {
                if (client.screen != null) client.screen.onClose();
            });
            context.waitTicks(10);
        } catch (Throwable e) {
            lines.add("FAIL  " + e);
            problems.add("external server: " + e);
        } finally {
            try {
                context.runOnClient(client -> {
                    if (client.level != null) client.disconnectFromWorld(net.minecraft.network.chat.Component.literal("test finished"));
                });
                context.waitTicks(20);
                // The gametest runner requires every test to end on the title screen
                context.setScreen(TitleScreen::new);
                context.waitTicks(5);
            } catch (Throwable ignored) {
            }
        }
        EnvelopeClientGameTest.write(results.resolve("external_server.txt"), lines);
    }
}
