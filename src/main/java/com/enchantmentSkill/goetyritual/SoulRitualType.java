package com.enchantmentSkill.goetyritual;

import com.Polarice3.Goety.api.ritual.IRitualType;
import com.Polarice3.Goety.common.blocks.entities.DarkAltarBlockEntity;
import com.Polarice3.Goety.common.blocks.entities.RitualBlockEntity;
import com.Polarice3.Goety.common.ritual.Ritual;
import com.enchantmentSkill.advancements.ModAdvancements;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 灵魂仪式（Soul Ritual）：无垠长魂之剑专属仪式类型。
 * 结构要求（黑暗祭坛周围 Ritual.RANGE 范围内统计）：
 * 8 个种植凋灵玫瑰的花盆、16 个幽匿块、4 个灵魂火、12 个哭泣黑曜石、
 * 1 个铁砧、1 个锻造台、1 个高炉、1 个炼药锅、4 个灵魂沙，且必须位于下界。
 */
public class SoulRitualType implements IRitualType {
    public static final String NAME = "soul_ritual";

    private static final int POTTED_WITHER_ROSES = 8;
    private static final int SCULK_BLOCKS = 16;
    private static final int SOUL_FIRES = 4;
    private static final int CRYING_OBSIDIANS = 12;
    private static final int ANVILS = 1;
    private static final int SMITHING_TABLES = 1;
    private static final int BLAST_FURNACES = 1;
    private static final int CAULDRONS = 1;
    private static final int SOUL_SANDS = 4;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public ItemStack getJeiIcon() {
        return new ItemStack(Items.SOUL_SAND);
    }

    @Override
    public boolean getRequirement(RitualBlockEntity tileEntity, @Nullable Player player, BlockPos pos, Level level) {
        if (level.dimension() != Level.NETHER) {
            sendMessage(player, Component.translatable("info.goety.ritual.structure.nether"));
            return false;
        }

        int range = Ritual.RANGE;
        int pottedWitherRoses = 0;
        int sculk = 0;
        int soulFire = 0;
        int cryingObsidian = 0;
        int anvils = 0;
        int smithingTables = 0;
        int blastFurnaces = 0;
        int cauldrons = 0;
        int soulSand = 0;

        for (BlockPos blockPos : BlockPos.betweenClosed(pos.offset(-range, -range, -range), pos.offset(range, range, range))) {
            BlockState state = level.getBlockState(blockPos);
            if (state.getBlock() instanceof FlowerPotBlock flowerPot && flowerPot.getContent() == Blocks.WITHER_ROSE) {
                ++pottedWitherRoses;
            } else if (state.getBlock() instanceof SculkBlock) {
                ++sculk;
            } else if (state.is(Blocks.SOUL_FIRE)) {
                ++soulFire;
            } else if (state.is(Blocks.CRYING_OBSIDIAN)) {
                ++cryingObsidian;
            } else if (state.getBlock() instanceof AnvilBlock) {
                ++anvils;
            } else if (state.getBlock() instanceof SmithingTableBlock) {
                ++smithingTables;
            } else if (state.getBlock() instanceof BlastFurnaceBlock) {
                ++blastFurnaces;
            } else if (state.getBlock() instanceof CauldronBlock) {
                ++cauldrons;
            } else if (state.is(Blocks.SOUL_SAND)) {
                ++soulSand;
            }
        }

        if (pottedWitherRoses < POTTED_WITHER_ROSES) {
            return fail(player, Blocks.WITHER_ROSE);
        }
        if (sculk < SCULK_BLOCKS) {
            return fail(player, Blocks.SCULK);
        }
        if (soulFire < SOUL_FIRES) {
            return fail(player, Blocks.SOUL_FIRE);
        }
        if (cryingObsidian < CRYING_OBSIDIANS) {
            return fail(player, Blocks.CRYING_OBSIDIAN);
        }
        if (anvils < ANVILS) {
            return fail(player, Blocks.ANVIL);
        }
        if (smithingTables < SMITHING_TABLES) {
            return fail(player, Blocks.SMITHING_TABLE);
        }
        if (blastFurnaces < BLAST_FURNACES) {
            return fail(player, Blocks.BLAST_FURNACE);
        }
        if (cauldrons < CAULDRONS) {
            return fail(player, Blocks.CAULDRON);
        }
        if (soulSand < SOUL_SANDS) {
            return fail(player, Blocks.SOUL_SAND);
        }
        return true;
    }

    private static boolean fail(@Nullable Player player, Block block) {
        sendMessage(player, Component.translatable("info.goety.ritual.structure.noBlocks", block.getName()));
        return false;
    }

    /** 锻出无垠长魂之剑时发「灵魂支配」成就（源质扣减已由 CursedCageSoulRitualMixin 保证为绝对数值） */
    @Override
    public void onFinishRitual(Level world, BlockPos darkAltarPos, DarkAltarBlockEntity tileEntity,
                               Player castingPlayer, ItemStack activationItem) {
        if (!(castingPlayer instanceof ServerPlayer serverPlayer)) return;
        if (activationItem.is(Items.NETHER_STAR)) {
            ModAdvancements.award(serverPlayer, ModAdvancements.SOUL_DOMINION);
        }
    }

    private static void sendMessage(@Nullable Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }
}
