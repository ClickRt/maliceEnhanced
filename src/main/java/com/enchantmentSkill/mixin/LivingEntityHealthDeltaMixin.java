package com.enchantmentSkill.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wither_title.WitherTitleHelper;
import com.enchantmentSkill.health.HealthDeltaBridge;
import com.enchantmentSkill.health.HealthDeltaStorage;

/**
 * Generic shadow health channel used by the Undefined Wither's attacks.
 *
 * The channel is deliberately orthogonal to DATA_HEALTH: getHealth() reads
 * min(originalHealth, maxHealth + delta), so a normal LivingEntity target can
 * be damaged even when its setHealth()/hurt() path has a per-hit clamp.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHealthDeltaMixin {

    private static final String DELTA_TAG = "malice_enhanced:generic_health_delta";

    @Inject(method = "getHealth", at = @At("RETURN"), cancellable = true)
    private void shatter$foldHealthDelta(CallbackInfoReturnable<Float> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!HealthDeltaBridge.useOwnStorage(entity)) return;
        float health = cir.getReturnValue();
        if (!Float.isFinite(health)) {
            cir.setReturnValue(0.0F);
            return;
        }
        float delta = HealthDeltaStorage.get(entity);
        if (delta == 0.0F) return;
        float effective = Math.min(health, entity.getMaxHealth() + delta);
        cir.setReturnValue(Math.max(0.0F, effective));
    }

    @Inject(method = "isDeadOrDying", at = @At("RETURN"), cancellable = true)
    private void shatter$foldDeadState(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!HealthDeltaBridge.useOwnStorage(entity)) return;
        if (getEffectiveHealth(entity) <= 0.0F) cir.setReturnValue(true);
    }

    @Inject(method = "isAlive", at = @At("RETURN"), cancellable = true)
    private void shatter$foldAliveState(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (!HealthDeltaBridge.useOwnStorage(entity)) return;
        if (getEffectiveHealth(entity) <= 0.0F) cir.setReturnValue(false);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void shatter$readHealthDelta(CompoundTag tag, CallbackInfo ci) {
        if (!tag.contains(DELTA_TAG)) return;
        float value = tag.getFloat(DELTA_TAG);
        if (Float.isFinite(value)) HealthDeltaStorage.set((LivingEntity) (Object) this, value);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void shatter$writeHealthDelta(CompoundTag tag, CallbackInfo ci) {
        tag.putFloat(DELTA_TAG, HealthDeltaStorage.get((LivingEntity) (Object) this));
    }

    private static float getEffectiveHealth(LivingEntity entity) {
        float health = entity.getHealth();
        if (!Float.isFinite(health)) return 0.0F;
        return Math.max(0.0F, Math.min(health, entity.getMaxHealth() + HealthDeltaStorage.get(entity)));
    }
}
