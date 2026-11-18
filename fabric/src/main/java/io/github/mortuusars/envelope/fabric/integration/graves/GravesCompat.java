package io.github.mortuusars.envelope.fabric.integration.graves;

import eu.pb4.graves.event.PlayerGraveItemAddedEvent;
import io.github.mortuusars.envelope.world.item.component.SealLock;
import net.minecraft.world.InteractionResult;

/**
 * Port (GGL), not in upstream: Universal Graves collects the inventory into the grave before vanilla drops it,
 * so the soulbound keep in InventoryMixin never sees those items. Leaving them out of the grave keeps them in
 * their slots, where the normal death drop keeps them on the player.
 * Only loaded when Universal Graves is present.
 */
public final class GravesCompat {
    private GravesCompat() {
    }

    public static void init() {
        PlayerGraveItemAddedEvent.EVENT.register((player, stack) ->
              SealLock.isKeptOnDeath(player, stack) ? InteractionResult.FAIL : InteractionResult.PASS);
    }
}
