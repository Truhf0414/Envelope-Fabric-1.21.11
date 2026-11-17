package io.github.mortuusars.envelope.world;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import io.github.mortuusars.envelope.world.mail.address.type.PlayerAddress;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class KnownPlayers extends SavedData {
    public static final Codec<KnownPlayers> CODEC = Codec.unboundedMap(Codec.STRING, PlayerData.CODEC)
          .xmap(KnownPlayers::new, KnownPlayers::getData);

    protected final Map<String, PlayerData> data;
    protected @Nullable Set<PlayerAddress> addresses;
    protected @Nullable Map<PlayerAddress, BlockAddress> defaultAddresses;

    public KnownPlayers(Map<String, PlayerData> data) {
        this.data = new HashMap<>(data); // Make sure it's modifiable
    }

    public KnownPlayers() {
        this(Collections.emptyMap());
    }

    protected Map<String, PlayerData> getData() {
        return data;
    }

    // --

    public void add(Player player) {
        if (player.getScoreboardName().equalsIgnoreCase("replay viewer")) {
            // Flashback mod uses space in the player name, which causes an error.
            // A mod about pigeons should not validate player names by the mojang's rules.
            return;
        }
        data.computeIfAbsent(player.getScoreboardName(), name -> new PlayerData(player.getGameProfile()));
        setDirty();
    }

    public void remove(String name) {
        if (data.remove(name) != null) {
            setDirty();
        }
    }

    public void clear() {
        data.clear();
        setDirty();
    }

    // --

    public Optional<PlayerData> getDataOf(String playerName) {
        return Optional.ofNullable(data.get(playerName));
    }

    public Optional<PlayerData> getDataOf(UUID uuid) {
        return data.values().stream().filter(d -> d.profile.id().equals(uuid)).findFirst();
    }

    // --

    public Optional<BlockAddress> getDefaultAddressOf(Player player) {
        return getDataOf(player.getScoreboardName()).flatMap(PlayerData::getDefaultAddress);
    }

    public Optional<BlockAddress> getDefaultAddressOf(PlayerAddress playerAddress) {
        return Optional.ofNullable(getDefaultAddresses().get(playerAddress));
    }

    public void setDefaultAddress(Player player, BlockAddress address) {
        update(player, data -> data.setDefaultAddress(address));
    }

    public void renameDefaultAddress(BlockAddress oldAddress, BlockAddress newAddress) {
        getData().values().forEach(data -> {
            if (data.getDefaultAddress().map(a -> a.equals(oldAddress)).orElse(false)) {
                data.setDefaultAddress(newAddress);
            }
        });
        setDirty();
    }

    public void removeDefaultAddress(BlockAddress address) {
        getData().values().forEach(data -> {
            if (data.getDefaultAddress().map(a -> a.equals(address)).orElse(false)) {
                data.setDefaultAddress(null);
            }
        });
        setDirty();
    }

    // --

    public void update(Player player, Consumer<PlayerData> updater) {
        data.compute(player.getScoreboardName(), (name, data) -> {
            if (data == null) {
                data = new PlayerData(player.getGameProfile());
            }
            updater.accept(data);
            return data;
        });
        setDirty();
    }

    // --

    public @NotNull Set<PlayerAddress> getAllAddresses() {
        if (addresses == null) {
            addresses = data.keySet().stream()
                  .map(PlayerAddress::new)
                  .collect(Collectors.toSet());
        }
        return addresses;
    }

    public Map<PlayerAddress, BlockAddress> getDefaultAddresses() {
        if (defaultAddresses == null) {
            defaultAddresses = new HashMap<>();
            getData().forEach((name, data) ->
                  data.getDefaultAddress().ifPresent(defaultAddress -> defaultAddresses.put(data.getAddress(), defaultAddress)));
        }
        return defaultAddresses;
    }

    @Override
    public void setDirty() {
        super.setDirty();
        resetCache();
    }

    protected void resetCache() {
        addresses = null;
        defaultAddresses = null;
    }

    // -- Save / Load

    public static KnownPlayers get(ServerLevel level, String name) {
        return level.getDataStorage().computeIfAbsent(type(name));
    }

    // 1.21.11: SavedData.Factory and SavedData#save were replaced by SavedDataType. DimensionDataStorage now encodes
    // and decodes CODEC itself (with registry-aware ops) and logs parse failures. DataFixTypes stays null like upstream.
    private static SavedDataType<KnownPlayers> type(String name) {
        return new SavedDataType<>(name, KnownPlayers::new, CODEC, null);
    }

    // --

    public static class PlayerData {
        // 1.21.11: ExtraCodecs.GAME_PROFILE_WITHOUT_PROPERTIES was removed. Its vanilla replacement (STORED_GAME_PROFILE)
        // also stores skin properties, so this keeps the old shape: id (int array) + validated name, the same fields
        // ExtraCodecs#gameProfileCodec writes, minus "properties".
        private static final Codec<GameProfile> PROFILE_CODEC = RecordCodecBuilder.create(i -> i.group(
              UUIDUtil.CODEC.fieldOf("id").forGetter(GameProfile::id),
              ExtraCodecs.PLAYER_NAME.fieldOf("name").forGetter(GameProfile::name)
        ).apply(i, GameProfile::new));

        public static final Codec<PlayerData> CODEC = RecordCodecBuilder.create(i -> i.group(
              PROFILE_CODEC.fieldOf("profile").forGetter(PlayerData::getProfile),
              BlockAddress.STRING_CODEC.optionalFieldOf("default_address").forGetter(PlayerData::getDefaultAddress)
        ).apply(i, PlayerData::new));

        protected final GameProfile profile;
        protected final PlayerAddress address;
        protected Optional<BlockAddress> defaultAddress;

        public PlayerData(GameProfile profile, Optional<BlockAddress> defaultAddress) {
            this.profile = profile;
            this.address = new PlayerAddress(profile.name());
            this.defaultAddress = defaultAddress;
        }

        public PlayerData(GameProfile profile) {
            this(profile, Optional.empty());
        }

        public GameProfile getProfile() {
            return profile;
        }

        public PlayerAddress getAddress() {
            return address;
        }

        public Optional<BlockAddress> getDefaultAddress() {
            return defaultAddress;
        }

        public void setDefaultAddress(@Nullable BlockAddress address) {
            this.defaultAddress = Optional.ofNullable(address);
        }
    }
}
