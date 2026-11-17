package io.github.mortuusars.envelope.mixin.death;

import com.mojang.authlib.GameProfile;
import io.github.mortuusars.envelope.world.item.component.SealLock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin extends Player {
    public ServerPlayerMixin(Level level, GameProfile gameProfile) { // 1.21.11: Player(Level, GameProfile)
        super(level, gameProfile);
    }

    @Inject(method = "die", at = @At("RETURN"))
    private void onDie(DamageSource damageSource, CallbackInfo ci) {
        SealLock.unlockAllFrom(getScoreboardName(), (ServerLevel) level()); // 1.21.11: serverLevel() removed; ServerPlayer#level() is the ServerLevel
    }
}
