package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weapon.StarFantasyHadesBan;

@Mixin(targets = "com.starfantasy.goety.entity.HadesEntity")
public abstract class StarFantasyHadesEntityMixin {

    @Inject(method = "spawn(Lcom/starfantasy/goety/entity/ApollyonEntity;Lnet/minecraft/world/phys/Vec3;Z)Lcom/starfantasy/goety/entity/HadesEntity;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void malice$blockBannedSpawn(@Coerce Object apollyon, @Coerce Object pos, boolean rising,
                                                CallbackInfoReturnable<Object> cir) {
        if (apollyon instanceof Entity owner && StarFantasyHadesBan.isOwnerBanned(owner)) {
            cir.setReturnValue(null);
        }
    }
}
