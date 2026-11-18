package io.github.mortuusars.envelope.mixin.death;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.mortuusars.envelope.Envelope;
import io.github.mortuusars.envelope.world.item.component.SealLock;
import io.github.mortuusars.envelope.world.level.saveddata.SealLocks;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.EnumMap;
import java.util.Map;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow
    @Final
    public Player player;

    @Unique
    private @Nullable ItemStack envelope$keptItem = null;

    // 1.21.11: Inventory#dropAll now drops the 36 main slots itself and hands armor/offhand to
    // EntityEquipment#dropAll (1.21.1 looped over main + armor + offhand). Both paths keep soulbound-locked items.
    @WrapOperation(method = "dropAll", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    public @Nullable ItemEntity onDropAllDrop(Player instance, ItemStack droppedItem, boolean dropAround, boolean includeThrowerName, Operation<ItemEntity> original) {
        if (envelope$keepOnDeath(instance, droppedItem)) {
            envelope$keptItem = droppedItem;
            return null;
        }
        return original.call(instance, droppedItem, dropAround, includeThrowerName);
    }

    // 1.21.11: the slot is cleared through NonNullList#set (was List#set)
    @SuppressWarnings("unchecked")
    @WrapOperation(method = "dropAll", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/NonNullList;set(ILjava/lang/Object;)Ljava/lang/Object;"))
    private <E> E onDropAllSet(NonNullList<E> instance, int i, E e, Operation<E> original) {
        if (envelope$keptItem != null) {
            e = (E) envelope$keptItem;
            envelope$keptItem = null;
        }

        return original.call(instance, i, e);
    }

    // EntityEquipment#dropAll drops every slot and then clears all of them, so kept items are taken out before and put back after.
    @WrapOperation(method = "dropAll", at = @At(value = "INVOKE",
          target = "Lnet/minecraft/world/entity/EntityEquipment;dropAll(Lnet/minecraft/world/entity/LivingEntity;)V"))
    private void onDropAllEquipment(EntityEquipment equipment, LivingEntity entity, Operation<Void> original) {
        Map<EquipmentSlot, ItemStack> kept = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = equipment.get(slot);
            if (!stack.isEmpty() && envelope$keepOnDeath(player, stack)) {
                kept.put(slot, stack);
                equipment.set(slot, ItemStack.EMPTY);
            }
        }

        original.call(equipment, entity);
        kept.forEach(equipment::set);
    }

    @Unique
    private static boolean envelope$keepOnDeath(Player instance, ItemStack droppedItem) {
        if (SealLock.isKeptOnDeath(instance, droppedItem)) {
            Vec3 pos = instance.position();
            instance.level().playSound(null, pos.x, pos.y, pos.z, Envelope.SoundEvents.SOULBOUND_SEAL_STAMP_UNLOCK.get(), SoundSource.PLAYERS,
                  1f, instance.getRandom().nextFloat() * 0.3f + 0.85f);
            if (instance.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SCULK_SOUL, pos.x, pos.y, pos.z, 5, 0.3, 0.3, 0.3, 0);
            }

            CustomData customData = droppedItem.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                  .update(tag -> tag.putBoolean("envelope_keep_in_inventory_on_transfer", true));
            droppedItem.set(DataComponents.CUSTOM_DATA, customData);
            return true;
        }
        return false;
    }
}
