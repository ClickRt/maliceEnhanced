package com.enchantmentSkill.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapon.StarFantasyHadesBan;

import java.lang.reflect.Field;

@Mixin(targets = "com.starfantasy.goety.combat.ApollyonPageantController", remap = false)
public abstract class StarFantasyHadesRespawnMixin {

    @Inject(method = "ensureBoundHades", at = @At("HEAD"), cancellable = true, require = 0)
    private void malice$blockEnsure(ServerLevel level, CallbackInfo ci) {
        if (malice$bossBanned()) {
            ci.cancel();
        }
    }

    @Inject(method = "spawnBoundHades(Lnet/minecraft/server/level/ServerLevel;Z)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void malice$blockSpawnBound(ServerLevel level, boolean rising, CallbackInfo ci) {
        if (malice$bossBanned()) {
            ci.cancel();
        }
    }

    private boolean malice$bossBanned() {
        try {
            Field field = this.getClass().getDeclaredField("boss");
            field.setAccessible(true);
            Object boss = field.get(this);
            return boss instanceof Entity entity && StarFantasyHadesBan.isOwnerBanned(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
