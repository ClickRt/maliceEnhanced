package com.enchantmentSkill.mixin;

import com.Polarice3.Goety.common.blocks.entities.ArcaBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.CursedCageBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.crafting.RitualRecipe;
import com.Polarice3.Goety.utils.SEHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 灵魂仪式的诅咒牢笼"绝对扣魂"。
 * RevelationFix 的 GoetyClassNodeProcessor 会在运行时用 ASM 把
 * SEHelper.decreaseSESouls 替换为带 SOUL_DECREASE_EFFICIENCY 属性折扣的实现，
 * 导致穿"灵魂折扣"装备的玩家仪式消耗被打折。
 * 当上方祭坛正在运行 soul_ritual 配方时，直接调用能力值
 * ISoulEnergy.decreaseSE（未被打补丁），保证 5000/s × 20s = 100,000 绝对消耗。
 * 其余路径（牢笼内 ITotem 物品的 NBT 扣减）本就是绝对数值，不拦截。
 */
@Mixin(CursedCageBlockEntity.class)
public abstract class CursedCageSoulRitualMixin {

    @Inject(method = "decreaseSouls", at = @At("HEAD"), cancellable = true, remap = false)
    private void extended$soulRitualAbsoluteDrain(int souls, CallbackInfo ci) {
        CursedCageBlockEntity cage = (CursedCageBlockEntity) (Object) this;
        Level level = cage.getLevel();
        if (level == null || level.isClientSide || souls <= 0) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(cage.getBlockPos().above());
        if (!(blockEntity instanceof DarkAltarBlockEntity altar)) {
            return;
        }
        RitualRecipe recipe = altar.getCurrentRitualRecipe();
        if (recipe == null || !"soul_ritual".equals(recipe.getCraftType())) {
            return;
        }
        Player owner = cage.getOwner();
        if (owner != null && SEHelper.getSEActive(owner)) {
            SEHelper.getCapability(owner).decreaseSE(souls);
            SEHelper.sendSEUpdatePacket(owner);
            if (SEHelper.getArcaBlock(owner) != null) {
                BlockEntity arca = level.getBlockEntity(SEHelper.getArcaBlock(owner));
                if (arca instanceof ArcaBlockEntity arcaBlockEntity) {
                    // Arca 粒子反馈,与 Goety 原版牢笼扣魂表现一致
                    arcaBlockEntity.generateParticles();
                    cage.generateParticles();
                }
            }
            ci.cancel();
        }
    }
}
