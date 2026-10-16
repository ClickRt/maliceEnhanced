package com.enchantmentSkill.mixin;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import weapon.SoulSwordEventHandler;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * 修复亚波伦"末日终结"阶段把玩家属性重置为白值，导致无垠长魂之剑加成失效的实战 bug。
 *
 * RevelationFix 的 PlayerTickrateExecutor.playerTick 在 Doom 阶段会给玩家所有属性打上
 * alwaysBase 标记（AttributeInstanceMixin.calculateValue 只按基础值结算，忽略一切修饰器），
 * 于是：
 *   - 铁魔法 MAX_MANA(+1000) 失效 → 法力回到白值 100，默认 800 费的黑洞放不出来；
 *   - 剑提供的护甲 +40 / 韧性 +20 / 移速 +20% / 击退抗性，以及铁魔法
 *     SPELL_POWER / CAST_TIME_REDUCTION / COOLDOWN_REDUCTION / MANA_REGEN 全部丢失。
 *
 * 这里在 playerTick 末尾，把所有"携带无垠长魂之剑修饰器 UUID"的属性重新置回 alwaysBase=false，
 * 让无垠长魂之剑自身的加成在 Doom 阶段照常生效；其他属性（和 Doom 惩罚）保持不变。
 */
@Mixin(targets = "com/mega/revelationfix/common/apollyon/common/PlayerTickrateExecutor", remap = false)
public abstract class SoulSwordAttributeKeepMixin {

    @Unique
    private static final UUID[] SOUL_SWORD_MODIFIER_UUIDS = {
            // SoulSwordEventHandler 常驻加成
            UUID.fromString("b1c2d3e4-f5a6-7890-bcde-f12345678901"), // ARMOR +40
            UUID.fromString("c1d2e3f4-a5b6-7890-cdef-123456789012"), // ARMOR_TOUGHNESS +20
            UUID.fromString("d1e2f3a4-b5c6-7890-defa-123456789013"), // MOVEMENT_SPEED +20%
            UUID.fromString("f1a2b3c4-d5e6-7890-fabc-123456789015"), // KNOCKBACK_RESISTANCE +10（格挡时）
            // IronSpellsCompat 铁魔法施法属性
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567891"), // MAX_MANA +1000
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567892"), // COOLDOWN_REDUCTION 0.8
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567893"), // SPELL_POWER x10
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567894"), // CAST_TIME_REDUCTION 0.5
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567895")  // MANA_REGEN x1
    };

    @Unique
    private static Method shatter$setAlwaysBase;

    @Inject(method = "playerTick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At("TAIL"))
    private static void soulSword$keepModifiers(Player player, CallbackInfo ci) {
        if (player == null || player.level().isClientSide) return;
        if (!SoulSwordEventHandler.hasSoulSword(player)) return;

        for (AttributeInstance instance : ((AttributeMapAccessor) player.getAttributes())
                .soulSword$getAttributesMap().values()) {
            if (hasSoulSwordModifier(instance)) {
                setAlwaysBase(instance, false);
            }
        }
    }

    @Unique
    private static boolean hasSoulSwordModifier(AttributeInstance instance) {
        for (UUID uuid : SOUL_SWORD_MODIFIER_UUIDS) {
            if (instance.getModifier(uuid) != null) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static void setAlwaysBase(AttributeInstance instance, boolean value) {
        try {
            if (shatter$setAlwaysBase == null) {
                shatter$setAlwaysBase = Class.forName("net.minecraft.world.entity.ai.attributes.AttributeInstance")
                        .getMethod("revelationfix$setAlwaysBase", boolean.class);
            }
            shatter$setAlwaysBase.invoke(instance, value);
        } catch (Throwable t) {
            // RevelationFix 未加载或方法名变化：忽略，Doom 惩罚照旧
        }
    }
}
