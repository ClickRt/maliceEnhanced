package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weapon.SoulSwordEventHandler;

/**
 * Iron's Spells cancels long casts when the caster takes damage.
 * Holding a soul_sword anywhere in the inventory makes that spell uninterruptible.
 */
@Mixin(targets = "io/redspace/ironsspellbooks/api/spells/AbstractSpell", remap = false)
public abstract class IronSpellCastInterruptMixin {
    @Inject(method = "canBeInterrupted(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void soulSword$keepCasting(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && SoulSwordEventHandler.hasSoulSwordInHands(player)) {
            cir.setReturnValue(false);
        }
    }
}
