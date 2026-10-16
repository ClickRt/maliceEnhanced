package com.enchantmentSkill.mixin;

import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapon.starcrystal.StarCoronaCrystalEvents;

/** Keeps the server-side requested flight state in sync with double-jump. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class SoulHeartFlightPacketMixin {
    @Shadow
    private ServerPlayer player;

    @Inject(method = "handlePlayerAbilities", at = @At("TAIL"))
    private void malice$recordFlightRequest(ServerboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        StarCoronaCrystalEvents.recordSoulHeartFlightRequest(player, packet.isFlying());
    }
}
