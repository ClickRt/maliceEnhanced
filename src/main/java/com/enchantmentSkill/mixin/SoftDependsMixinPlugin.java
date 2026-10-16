package com.enchantmentSkill.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class SoftDependsMixinPlugin implements IMixinConfigPlugin {
    private static final String IRONS_CAST_MIXIN = "com.enchantmentSkill.mixin.IronSpellCastInterruptMixin";
    private static final String BLACK_HOLE_MIXIN = "com.enchantmentSkill.mixin.BlackHoleMixin";
    private static final String BLACK_HOLE_SPELL_MIXIN = "com.enchantmentSkill.mixin.BlackHoleSpellMixin";
    private static final String TIME_STOP_UTILS_MIXIN = "com.enchantmentSkill.mixin.TimeStopUtilsMixin";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        // 注意：这里绝不能 Class.forName 依赖类，也不要在 mixin 选择阶段读取 ModList——
        // 目标类（如 irons 的 AbstractSpell）被提前加载会让其他 mod 的 mixin prepare 崩溃
        // （MixinTargetAlreadyLoadedException）。配置 required=false，目标类缺失时 mixin 会自动跳过。
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
