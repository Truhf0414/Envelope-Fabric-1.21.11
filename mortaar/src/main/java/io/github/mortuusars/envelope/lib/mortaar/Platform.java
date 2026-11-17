package io.github.mortuusars.envelope.lib.mortaar;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class Platform {
    public static boolean isInDevEnv() {
        return io.github.mortuusars.envelope.lib.mortaar.fabric.PlatformImpl.isInDevEnv();
    }

    public static boolean isClient() {
        return io.github.mortuusars.envelope.lib.mortaar.fabric.PlatformImpl.isClient();
    }

    public static boolean isDedicatedServer() {
        return !isClient();
    }

    public static boolean isModLoaded(String modId) {
        return io.github.mortuusars.envelope.lib.mortaar.fabric.PlatformImpl.isModLoaded(modId);
    }

    public static boolean isModLoading(String modId) {
        return io.github.mortuusars.envelope.lib.mortaar.fabric.PlatformImpl.isModLoading(modId);
    }

    public static Path getGameDirectory() {
        return io.github.mortuusars.envelope.lib.mortaar.fabric.PlatformImpl.getGameDirectory();
    }

    public static Path getConfigDirectory() {
        return io.github.mortuusars.envelope.lib.mortaar.fabric.PlatformImpl.getConfigDirectory();
    }

    public static @Nullable MinecraftServer getCurrentServer() {
        return io.github.mortuusars.envelope.lib.mortaar.fabric.PlatformImpl.getCurrentServer();
    }

    public static Optional<MinecraftServer> getServer() {
        return Optional.ofNullable(getCurrentServer());
    }

    public static @NotNull MinecraftServer getCurrentServerOrThrow() {
        return Objects.requireNonNull(getCurrentServer());
    }

    public static void openMenu(ServerPlayer serverPlayer, MenuProvider menuProvider, Consumer<RegistryFriendlyByteBuf> extraDataWriter) {
        io.github.mortuusars.envelope.lib.mortaar.fabric.PlatformImpl.openMenu(serverPlayer, menuProvider, extraDataWriter);
    }
}
