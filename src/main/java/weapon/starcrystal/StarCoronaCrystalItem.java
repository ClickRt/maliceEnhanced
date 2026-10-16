package weapon.starcrystal;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;
import wither_title.WitherTitleHelper;

import java.util.List;

public class StarCoronaCrystalItem extends Item {
    public StarCoronaCrystalItem() {
        super(new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                    (id, inv, p) -> new StarCoronaCrystalMenu(id, inv, stack),
                    stack.getHoverName()), buf -> buf.writeBoolean(hand == InteractionHand.MAIN_HAND));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        StarCoronaInventory inv = StarCoronaInventory.of(stack);
        int filled = inv.titleCount();
        tooltip.add(Component.translatable("item.malice_enhanced.star_corona_crystal.slots", filled, StarCrystalSlots.TITLE_SLOTS)
                .withStyle(ChatFormatting.GRAY));
        if (filled <= 0) {
            tooltip.add(Component.translatable("item.malice_enhanced.star_corona_crystal.empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            for (int i = 0; i < StarCrystalSlots.TITLE_SLOTS; i++) {
                int title = StarCrystalSlots.getTitleId(inv.getStackInSlot(i));
                if (title <= 0) continue;
                tooltip.add(Component.translatable("wither_title.malice_enhanced.star_tooltip",
                                WitherTitleHelper.getTitleName(title))
                        .withStyle(ChatFormatting.DARK_PURPLE));
                tooltip.add(Component.translatable("wither_title.malice_enhanced." + title + ".desc")
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        boolean soulHeart = inv.hasSoulHeart();
        tooltip.add(Component.translatable(soulHeart
                        ? "item.malice_enhanced.star_corona_crystal.soul_heart"
                        : "item.malice_enhanced.star_corona_crystal.no_soul_heart")
                .withStyle(soulHeart ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY));
        if (soulHeart) {
            tooltip.add(Component.translatable("item.malice_enhanced.star_corona_crystal.soul_heart.desc")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (inv.hasAllTitles()) {
            tooltip.add(Component.translatable("item.malice_enhanced.star_corona_crystal.complete")
                    .withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("item.malice_enhanced.star_corona_crystal.ascension")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(StarCoronaInventory.ITEMS_KEY)) return false;
        return StarCoronaInventory.of(stack).hasAllTitles();
    }
}
