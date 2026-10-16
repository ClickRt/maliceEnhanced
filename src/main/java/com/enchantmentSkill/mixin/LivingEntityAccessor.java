package com.enchantmentSkill.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("lastHurtByPlayerTime")
    void shatter$setLastHurtByPlayerTime(int time);

    @Accessor("dead")
    void shatter$setDead(boolean dead);

    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> shatter$getDataHealthId() {
        throw new AssertionError();
    }

    @Accessor("deathTime")
    void shatter$setDeathTime(int deathTime);

    @Invoker("dropAllDeathLoot")
    void shatter$dropAllDeathLoot(DamageSource source);
}
