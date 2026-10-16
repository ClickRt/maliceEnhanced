package com.enchantmentSkill.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapon.starcrystal.StarCoronaCrystalEvents;
import weapon.starcrystal.StarCoronaCurios;

/** Captures the state selected by the local player's double-jump toggle. */
@Mixin(LocalPlayer.class)
public abstract class SoulHeartFlightClientMixin {
    @Unique
    private boolean malice$flightBeforeAiStep;
    @Unique
    private boolean malice$flightCapture;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void malice$captureFlightState(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        malice$flightCapture = StarCoronaCurios.hasSoulHeart(player);
        if (malice$flightCapture) {
            malice$flightBeforeAiStep = player.getAbilities().flying;
        }
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void malice$recordFlightToggle(CallbackInfo ci) {
        if (!malice$flightCapture) return;
        LocalPlayer player = (LocalPlayer) (Object) this;
        boolean flying = player.getAbilities().flying;
        if (flying != malice$flightBeforeAiStep) {
            StarCoronaCrystalEvents.recordSoulHeartFlightRequest(player, flying);
        }
        malice$flightCapture = false;
    }
}
