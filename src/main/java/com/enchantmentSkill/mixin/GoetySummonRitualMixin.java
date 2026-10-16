package com.enchantmentSkill.mixin;

import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.crafting.RitualRecipe;
import com.Polarice3.Goety.common.ritual.SummonRitual;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wither_title.SummonUndefinedWither;
import wither_title.WitherTitleEntities;

/**
 * 祭坛召唤 ? 凋灵 ? 时，将 WitherBoss 替换为 SummonUndefinedWither 动画实体。
 * 类似 Goety 的 SummonApostle 机制：
 * 1. 先召唤一个不可见的动画实体（SummonUndefinedWither）
 * 2. 该实体播放 10 秒粒子旋转动画（灵魂沙 + 灵魂火 + 哭泣黑曜石粒子）
 * 3. 动画结束后，生成真正的未定义凋灵并添加 undefined_wither 标签
 * <p>
 * 注意：forge:nbt 已确保激活物品是13号头衔下界之星，无需重复检查 NBT。
 */
@Mixin(SummonRitual.class)
public abstract class GoetySummonRitualMixin {

    @Inject(method = "createSummonedEntity", at = @At("RETURN"), remap = false, cancellable = true)
    private void extended$onCreateSummonedEntity(EntityType<?> entityType, Level world,
                                                  BlockPos goldenBowlPosition, DarkAltarBlockEntity tileEntity,
                                                  Player castingPlayer, CallbackInfoReturnable<Entity> cir) {
        Entity entity = cir.getReturnValue();
        if (!(entity instanceof WitherBoss)) return;

        // 确认配方是召唤凋灵的
        RitualRecipe recipe = ((SummonRitual) (Object) this).getRecipe();
        if (recipe == null || recipe.getEntityToSummon() != EntityType.WITHER) return;

        // 替换为召唤动画实体（类似 SummonApostle 的机制）
        // 它不是 LivingEntity，因此 finish() 会走 else 分支设置位置并 spawnEntity
        // 该实体将播放 10 秒粒子动画，然后生成真正的 ? 凋灵 ?
        cir.setReturnValue(new SummonUndefinedWither(WitherTitleEntities.SUMMON_UNDEFINED_WITHER.get(), world));
    }
}