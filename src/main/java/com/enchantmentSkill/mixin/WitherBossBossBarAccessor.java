package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.server.level.ServerBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 WitherBoss 的私有 bossEvent（ServerBossEvent），
 * 用于每 tick 更新血条名称实现动态渐变（动态渐变必须服务端每 tick setName 广播）。
 */
@Mixin(WitherBoss.class)
public interface WitherBossBossBarAccessor {

    @Accessor("bossEvent")
    ServerBossEvent shatter$getBossEvent();
}
