package com.enchantmentSkill.mixin;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import weapon.IronSpellsCompat;

/**
 * 只针对“无垠长魂之剑发起的蓝色黑洞”调整半径公式，其他铁魔法原版黑洞不受影响：
 *
 * 原版: radius = (2*等级 + 4) + 3*(法术强度 - 1)
 * 改为: radius = (2*等级 + 4) + 3*(floor(法术强度 / 2) - 3)
 *
 * 法术强度≈11（无垠长魂之剑）: 22，比原版（16）略大，释放高度适中。
 */
@Mixin(targets = "io/redspace/ironsspellbooks/spells/ender/BlackHoleSpell", remap = false)
public abstract class BlackHoleSpellMixin {

    @Inject(method = "getRadius(ILnet/minecraft/world/entity/LivingEntity;)F",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void shatter$resizeBlackHoleRadius(int spellLevel, LivingEntity entity,
                                               CallbackInfoReturnable<Float> cir) {
        // 只有无垠长魂之剑发起的施法（与黑洞变蓝同一判定）才套用新公式；
        // 卷轴/法术书/其他来源的黑洞保持原版半径与释放高度。
        if (!(entity instanceof Player player)) return;
        if (!IronSpellsCompat.isSoulSwordCast(player)) return;

        float power = ((AbstractSpell) (Object) this).getSpellPower(spellLevel, player);
        float radius = (float) (2 * spellLevel + 4) + 3.0F * ((float) Math.floor(power / 2.0D) - 3.0F);
        cir.setReturnValue(radius);
    }
}
