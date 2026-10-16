package com.enchantmentSkill.mixin;

import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 修复 Goety Apostle.teleportTowards 的异步传送崩溃：
 * MobsConfig.ApostleDelayedTeleport=true 时，CompletableFuture.supplyAsync 在
 * ForkJoin 线程里调用线程绑定的 LegacyRandomSource（level.getRandom()），触发
 * "Accessing LegacyRandomSource from multiple threads" 崩溃。
 * 把 lambda 内的随机数调用重定向为 ThreadLocalRandom（线程安全），保留异步传送。
 * 注意：不能写 remap=false，否则 nextDouble/nextInt 不会映射成 SRG 名（生产环境失效）。
 */
@Mixin(targets = "com/Polarice3/Goety/common/entities/boss/Apostle")
public abstract class ApostleTeleportThreadSafeMixin {

    @Redirect(
            method = "lambda$teleportTowards$6",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextDouble()D"),
            require = 0
    )
    private static double soulSword$safeNextDouble(RandomSource random) {
        return ThreadLocalRandom.current().nextDouble();
    }

    @Redirect(
            method = "lambda$teleportTowards$6",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"),
            require = 0
    )
    private static int soulSword$safeNextInt(RandomSource random, int bound) {
        return ThreadLocalRandom.current().nextInt(bound);
    }
}
