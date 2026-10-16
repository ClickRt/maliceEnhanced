package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapon.starcrystal.StarCoronaCrystalEvents;

/**
 * Reasserts Soul Heart flight after the player's own tick. Other mods can
 * write PlayerAbilities directly during that tick without sending a packet,
 * so the level-tick guard alone would leave a short server/client window.
 * The requested state is tracked separately so the player's double-jump
 * toggle can still turn active flight off.
 */
@Mixin(Player.class)
public abstract class SoulHeartFlightMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void malice$enforceSoulHeartFlight(CallbackInfo ci) {
        StarCoronaCrystalEvents.enforceSoulHeartFlight((Player) (Object) this);
    }
}
