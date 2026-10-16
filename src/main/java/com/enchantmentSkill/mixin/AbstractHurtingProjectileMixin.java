package com.enchantmentSkill.mixin;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wither_title.WitherTitleHelper;

@Mixin(AbstractHurtingProjectile.class)
public abstract class AbstractHurtingProjectileMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void witherTitle$seekBeforeMove(CallbackInfo ci) {
        if (!((Object) this instanceof WitherSkull skull)) return;
        Entity target = WitherTitleHelper.getSeekingTarget(skull);
        if (target == null || !target.isAlive()) return;
        Vec3 targetVec = target.position().add(0, target.getBbHeight() * 0.5D, 0)
                .subtract(skull.position());
        if (targetVec.lengthSqr() <= 1.0E-4D) return;
        if (WitherTitleHelper.isSeekingSkull(skull)) {
            Vec3 dir = targetVec.normalize().scale(0.1D);
            skull.xPower = dir.x;
            skull.yPower = dir.y;
            skull.zPower = dir.z;
            if (skull.getDeltaMovement().lengthSqr() < 1.0E-4D) {
                skull.setDeltaMovement(dir);
            }
            return;
        }
        Vec3 courseVec = new Vec3(skull.xPower, skull.yPower, skull.zPower);
        double courseLen = courseVec.length();
        if (courseLen <= 1.0E-4D) return;
        double targetLen = targetVec.length();
        double totalLen = Math.sqrt(courseLen * courseLen + targetLen * targetLen);
        Vec3 newPower = courseVec.scale(courseLen / totalLen)
                .add(targetVec.scale(courseLen / totalLen));
        skull.xPower = newPower.x;
        skull.yPower = newPower.y;
        skull.zPower = newPower.z;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void witherTitle$skullTick(CallbackInfo ci) {
        if (!((Object) this instanceof WitherSkull skull)) return;
        if (!(skull.level() instanceof ServerLevel serverLevel)) return;
        CompoundTag data = skull.getPersistentData();
        boolean seeking = WitherTitleHelper.isSeekingSkull(skull);
        boolean title7 = !seeking && skull.getOwner() instanceof WitherBoss owner
                && (WitherTitleHelper.getTitleId(owner) == 7 || WitherTitleHelper.isUndefinedWither(owner));
        if (title7 && skull.tickCount >= 400) {
            skull.discard();
            return;
        }
        if (skull.tickCount >= 1 && data.getBoolean(WitherTitleHelper.FLAME_SKULL_KEY)) {
            RandomSource random = serverLevel.getRandom();
            for (int i = 0; i < 2; i++) {
                double theta = random.nextDouble() * Math.PI * 2.0D;
                double phi = Math.acos(2.0D * random.nextDouble() - 1.0D);
                double px = 0.6D * Math.sin(phi) * Math.cos(theta);
                double py = 0.6D * Math.sin(phi) * Math.sin(theta);
                double pz = 0.6D * Math.cos(phi);
                serverLevel.sendParticles(ParticleTypes.FLAME,
                        skull.getX() + px, skull.getY() + 0.15D + py, skull.getZ() + pz,
                        1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        if (seeking && skull.tickCount >= 1) {
            Vec3 motion = skull.getDeltaMovement();
            double mx = motion.x;
            double my = motion.y;
            double mz = motion.z;
            double mLen = Math.sqrt(mx * mx + my * my + mz * mz);
            double bx = mLen > 1.0E-4D ? -mx / mLen * 0.35D : 0.0D;
            double by = mLen > 1.0E-4D ? -my / mLen * 0.35D : 0.0D;
            double bz = mLen > 1.0E-4D ? -mz / mLen * 0.35D : 0.0D;
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    skull.getX() + bx, skull.getY() + 0.15D + by, skull.getZ() + bz,
                    3, 0.04D, 0.04D, 0.04D, 0.01D);
        }
    }
}