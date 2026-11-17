package io.github.mortuusars.envelope.world.mail;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.Encoder;
import io.github.mortuusars.envelope.util.PersistentData;
import io.github.mortuusars.envelope.lib.mortaar.Platform;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class CustomMailSavedData extends SavedData {
    // 1.21.11: SavedData#save is gone and DimensionDataStorage encodes CODEC directly, so the encoder first writes
    // dirty typed data into the tag (what upstream's save() did), using the ops the storage passes in.
    public static final Codec<CustomMailSavedData> CODEC = Codec.of(
          new Encoder<>() {
              @Override
              public <T> DataResult<T> encode(CustomMailSavedData input, DynamicOps<T> ops, T prefix) {
                  input.writeDirtyTypedData(ops);
                  return CompoundTag.CODEC.encode(input.getData(), ops, prefix);
              }
          },
          CompoundTag.CODEC.map(CustomMailSavedData::new));
    public static final Logger LOGGER = LogUtils.getLogger();

    protected final Map<PersistentData.Type<?>, PersistentData> typedData = new HashMap<>();
    protected final CompoundTag data;

    public CustomMailSavedData(CompoundTag data) {
        this.data = data;
    }

    public CustomMailSavedData() {
        this.data = new CompoundTag();
    }

    public CompoundTag getData() {
        return data;
    }

    /**
     * Gets the tag by ID.<br>
     * Don't forget to call {@link #setDirty()} or {@link #set(Identifier, CompoundTag)} to ensure that changes will be saved.
     * @param location ID of the data.
     */
    public CompoundTag get(Identifier location) {
        return getData().getCompoundOrEmpty(location.toString()); // 1.21.11: getCompound returns Optional
    }

    public void set(Identifier location, CompoundTag data) {
        getData().put(location.toString(), data);
        setDirty();
    }

    @SuppressWarnings("unchecked")
    public <T extends PersistentData> T getOrCreateTyped(PersistentData.Type<T> type) {
        return (T) typedData.computeIfAbsent(type, t -> {
            String key = t.id().toString();
            if (data.contains(key)) {
                RegistryAccess registryAccess = Platform.getCurrentServerOrThrow().registryAccess();
                return t.codec().decode(registryAccess.createSerializationContext(NbtOps.INSTANCE), data.getCompoundOrEmpty(key))
                      .ifError(e -> LOGGER.error("Cannot decode typed data '{}': {}", t, e))
                      .result()
                      .map(p -> (PersistentData) p.getFirst())
                      .orElse(t.constructor().get());
            }

            return t.constructor().get();
        });
    }

    // -- Save / Load

    // 1.21.11: SavedData.Factory was replaced by SavedDataType; DataFixTypes stays null like upstream.
    public static final SavedDataType<CustomMailSavedData> TYPE =
          new SavedDataType<>("envelope_mail_data", CustomMailSavedData::new, CODEC, null);

    public static CustomMailSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    @Override
    public boolean isDirty() {
        return super.isDirty() || typedData.values().stream().anyMatch(PersistentData::isDirty);
    }

    protected <O> void writeDirtyTypedData(DynamicOps<O> ops) {
        for (Map.Entry<PersistentData.Type<?>, PersistentData> typedData : typedData.entrySet()) {
            if (typedData.getValue().isDirty()) {
                try {
                    O encoded = encodeTypedData(typedData.getKey(), typedData.getValue(), ops);
                    data.put(typedData.getKey().id().toString(), ops.convertTo(NbtOps.INSTANCE, encoded));
                    typedData.getValue().setDirty(false);
                } catch (Exception e) {
                    LOGGER.error("Cannot encode typed data '{}':", typedData.getKey(), e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends PersistentData, O> O encodeTypedData(PersistentData.Type<T> type, PersistentData data, DynamicOps<O> ops) {
        return type.codec().encodeStart(ops, (T)data).getOrThrow();
    }
}
