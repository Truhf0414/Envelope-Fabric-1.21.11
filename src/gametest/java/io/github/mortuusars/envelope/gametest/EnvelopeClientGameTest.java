package io.github.mortuusars.envelope.gametest;

import io.github.mortuusars.envelope.lib.mortaar.bugger.Bugger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Port tests (1.21.11): drives a real client through a singleplayer world.
 * Run with {@code gradlew runClientGameTest}. Results are written to {@code <run dir>/envelope_port_results/}.
 */
public class EnvelopeClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        Path results = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("envelope_port_results"));

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientWorld().waitForChunksRender();

            runBuggerTests(singleplayer, results);

            context.takeScreenshot("envelope_world_loaded");
        }
    }

    /** Runs every registered Bugger test (same as {@code /mortaar debug tests}) with the singleplayer player. */
    private static void runBuggerTests(TestSingleplayerContext singleplayer, Path results) {
        List<String> lines = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            List<String> output = new ArrayList<>();
            Bugger.runTests(player, message -> output.add(message.getString()));
            return output;
        });

        write(results.resolve("bugger_tests.txt"), lines);

        boolean passed = lines.stream().anyMatch(line -> line.contains("All tests are passed!"));
        if (!passed) {
            throw new AssertionError("Bugger tests failed:\n" + String.join("\n", lines));
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
}
