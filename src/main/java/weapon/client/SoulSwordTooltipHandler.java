package weapon.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import weapon.SoulSwordItem;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SoulSwordTooltipHandler {
    private static final int[] RAINBOW = {
            0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xFF55FF
    };

    private SoulSwordTooltipHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof SoulSwordItem)) {
            return;
        }

        List<Component> tooltip = event.getToolTip();
        for (int i = 0; i < tooltip.size(); i++) {
            String s = tooltip.get(i).getString();
            if (s.length() > 25 && (s.contains("攻击伤害") || s.contains("Attack Damage"))) {
                tooltip.set(i, Component.translatable("attribute.modifier.equals.0",
                                rainbow("e*10^24"),
                                Component.translatable("attribute.name.generic.attack_damage"))
                        .withStyle(ChatFormatting.DARK_GREEN));
                break;
            }
        }

        if (Screen.hasShiftDown()) {
            return;
        }

        List<Component> compact = new ArrayList<>();
        boolean seenMainHand = false;
        boolean addedHint = false;
        for (Component line : tooltip) {
            if (isAdvancedLine(line)) {
                if (!addedHint) {
                    compact.add(shiftHint());
                    addedHint = true;
                }
                compact.add(line);
                continue;
            }
            if (isMainHandHeader(line)) {
                seenMainHand = true;
                compact.add(line);
                continue;
            }
            if (!seenMainHand) {
                compact.add(line);
                continue;
            }
            if (isAttackDamageLine(line) || isAttackSpeedLine(line)) {
                compact.add(line);
            }
        }
        if (!addedHint) {
            compact.add(shiftHint());
        }
        tooltip.clear();
        tooltip.addAll(compact);
    }

    private static Component shiftHint() {
        return Component.translatable("item.malice_enhanced.soul_sword.shift_more",
                        Component.literal("Shift").withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    private static boolean isMainHandHeader(Component line) {
        return containsKey(line, "item.modifiers.mainhand")
                || line.getString().contains("在主手时")
                || line.getString().contains("When in Main Hand");
    }

    private static boolean isAttackDamageLine(Component line) {
        return containsKey(line, "attribute.name.generic.attack_damage")
                || line.getString().contains("攻击伤害")
                || line.getString().contains("Attack Damage");
    }

    private static boolean isAttackSpeedLine(Component line) {
        return containsKey(line, "attribute.name.generic.attack_speed")
                || line.getString().contains("攻击速度")
                || line.getString().contains("Attack Speed");
    }

    private static boolean isAdvancedLine(Component line) {
        String plain = line.getString();
        return containsKey(line, "item.nbt_tags")
                || containsKey(line, "item.durability")
                || plain.contains("malice_enhanced:soul_sword")
                || plain.startsWith("NBT:");
    }

    private static boolean containsKey(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents contents && key.equals(contents.getKey())) {
            return true;
        }
        for (Component sibling : component.getSiblings()) {
            if (containsKey(sibling, key)) {
                return true;
            }
        }
        return false;
    }

    private static Component rainbow(String text) {
        long tick = System.currentTimeMillis() / 100;
        MutableComponent result = Component.literal("");
        for (int i = 0; i < text.length(); i++) {
            int color = RAINBOW[(int) ((i + tick) % RAINBOW.length)];
            result.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }
        return result;
    }
}
