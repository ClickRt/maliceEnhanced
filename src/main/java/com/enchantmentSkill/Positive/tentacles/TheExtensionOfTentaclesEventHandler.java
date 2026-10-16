package com.enchantmentSkill.positive.tentacles;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * 触手的延伸：把触及范围加成挂到物品本身（而非每 tick 手动改玩家属性）。
 * 这样原版会自动在悬浮提示里渲染 "+X 实体触及范围 / 方块触及范围"，
 * 并在主手装备/卸下时自行增删修饰符，无需 tick 循环与登出清理。
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TheExtensionOfTentaclesEventHandler {
    private static final UUID ENTITY_REACH_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID BLOCK_REACH_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    @SubscribeEvent
    public static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        // 与附魔的 MAINHAND 槽位保持一致，仅主手生效
        if (event.getSlotType() != EquipmentSlot.MAINHAND) return;

        ItemStack stack = event.getItemStack();
        int level = stack.getEnchantmentLevel(TheExtensionOfTentaclesEnchantments.EXTENSION_OF_TENTACLES.get());
        if (level <= 0) return;

        // 每级 +0.5 格
        double bonus = level * 0.5;

        event.addModifier(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                ENTITY_REACH_UUID, "tentacle_entity_reach", bonus,
                AttributeModifier.Operation.ADDITION));
        event.addModifier(ForgeMod.BLOCK_REACH.get(), new AttributeModifier(
                BLOCK_REACH_UUID, "tentacle_block_reach", bonus,
                AttributeModifier.Operation.ADDITION));
    }
}
