package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(WitherBoss.class)
public interface WitherBossAccessor {
    @Accessor("nextHeadUpdate")
    int[] getNextHeadUpdate();

    @Invoker("getAlternativeTarget")
    int shatter$getAlternativeTarget(int head);
}
