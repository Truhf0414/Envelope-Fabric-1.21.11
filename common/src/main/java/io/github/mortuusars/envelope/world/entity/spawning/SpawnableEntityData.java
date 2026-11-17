package io.github.mortuusars.envelope.world.entity.spawning;

import com.google.common.base.Preconditions;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

public record SpawnableEntityData(CustomData data) {
    public static final Codec<SpawnableEntityData> CODEC =
                CustomData.CODEC.validate(data -> {
                    if (data.isEmpty()) return DataResult.error(() -> "Entity data cannot be empty.");
                    return DataResult.success(data);
                })
          .xmap(SpawnableEntityData::new, SpawnableEntityData::data);

    private static final Logger LOGGER = LogUtils.getLogger();

    // 1.21.11: CustomData#getUnsafe was removed; copyTag() is the only accessor.
    public SpawnableEntityData {
        Preconditions.checkArgument(!data.isEmpty(), "Entity data cannot be empty.");
        Preconditions.checkState(data.copyTag().getString("id").isPresent(),
              "Entity tag does not contain an 'id': " + data.getClass());
    }

    public static SpawnableEntityData of(Entity entity, List<String> ignoredTags) {
        Preconditions.checkArgument(!entity.isRemoved(),
              "Cannot create SpawnableEntityData: entity '" + entity + "' is removed.");
        Preconditions.checkArgument(!entity.isPassenger(),
              "Cannot create SpawnableEntityData: entity '" + entity + "' is passenger.");
        Preconditions.checkArgument(entity.getType().canSerialize(),
              "Cannot create SpawnableEntityData: entity '" + entity + "' type is not serializable.");

        CompoundTag tag;
        // 1.21.11: Entity#save writes to a ValueOutput; same pattern as vanilla BeehiveBlockEntity.Occupant#of
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
            if (!entity.save(output)) {
                LOGGER.error("Failed to save entity '{}' to a tag. Entity is passenger, " +
                      "about to be removed or entity type is not serializable.", entity);
            }
            tag = output.buildResult();
        }

        ignoredTags.forEach(tag::remove);
        return new SpawnableEntityData(CustomData.of(tag));
    }

    // --

    public @Nullable Entity createEntity(ServerLevel level) {
        // 1.21.11 signature, as BeehiveBlockEntity.Occupant#createEntity
        @Nullable Entity entity = EntityType.loadEntityRecursive(data.copyTag(), level, EntitySpawnReason.LOAD, EntityProcessor.NOP);
        if (entity == null) {
            LOGGER.error("Failed to create spawnable entity. Tag: {}", data);
        }
        return entity;
    }

    public Optional<EntityType<?>> getEntityType() {
        // 1.21.11: EntityType#by(CompoundTag) now takes a ValueInput; reading the id directly is equivalent.
        return data.copyTag().getString("id").flatMap(EntityType::byString);
    }
}