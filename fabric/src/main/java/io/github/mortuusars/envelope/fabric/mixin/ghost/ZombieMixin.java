package io.github.mortuusars.envelope.fabric.mixin.ghost;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.mortuusars.envelope.world.entity.Pigeon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Zombie.class)
public class ZombieMixin {
    // 1.21.11: the villager -> zombie villager conversion moved out of killedEntity into convertVillagerToZombieVillager,
    // and finalizeSpawn now runs inside a convertTo callback lambda. The new method is only called from killedEntity,
    // so hooking its return keeps the old trigger (a zombie killing a villager).
    @Inject(method = "convertVillagerToZombieVillager", at = @At("RETURN"))
    private void onVillagerConverted(ServerLevel level, Villager villager, CallbackInfoReturnable<Boolean> cir,
                                     @Local ZombieVillager zombieVillager) {
        if (zombieVillager != null) {
            Pigeon.trySpawnGhost(level, zombieVillager);
        }
    }
}
