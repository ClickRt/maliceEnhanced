package com.enchantmentSkill.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weapon.SoulSwordItem;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtDebugMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("SoulSwordHurt");

    private static boolean isSoulSwordAttack(DamageSource source) {
        if (source == null) return false;
        if (!(source.getEntity() instanceof Player p)) return false;
        ItemStack main = p.getMainHandItem();
        ItemStack off = p.getOffhandItem();
        return main.getItem() instanceof SoulSwordItem || off.getItem() instanceof SoulSwordItem;
    }

    @Inject(method = "hurt", at = @At("HEAD"))
    private void soulSword$hurtHead(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!isSoulSwordAttack(source)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel)) return;
        LOGGER.info("HURT HEAD target={} src={} invulTo={} invulTime={} dead={} hp={}",
                self.getType(), source, self.isInvulnerableTo(source), self.invulnerableTime, self.isDeadOrDying(), self.getHealth());
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void soulSword$hurtReturn(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (!isSoulSwordAttack(source)) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self.level() instanceof ServerLevel)) return;
        LOGGER.info("HURT RETURN target={} result={} hp={}",
                self.getType(), cir.getReturnValue(), self.getHealth());
    }
}
