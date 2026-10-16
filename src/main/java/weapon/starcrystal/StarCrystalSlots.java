package weapon.starcrystal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import wither_title.WitherTitleHelper;

public final class StarCrystalSlots {
    public static final String SLOT_ID = "star_crystal";
    public static final int TITLE_SLOTS = 13;
    /** 法阵中央：放置灵魂之心（NBT 下界之星）的展示槽 */
    public static final int CENTER_SLOT = TITLE_SLOTS;
    public static final int TOTAL_SLOTS = TITLE_SLOTS + 1;

    private StarCrystalSlots() {
    }

    /** 灵魂之心：带 undefined_wither NBT 的下界之星 */
    public static boolean isSoulHeart(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.NETHER_STAR)) return false;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(WitherTitleHelper.UNDEFINED_NBT_KEY);
    }

    public static int getTitleId(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.NETHER_STAR)) return 0;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(WitherTitleHelper.STAR_NBT_KEY)) return 0;
        int id = tag.getInt(WitherTitleHelper.STAR_NBT_KEY);
        if (id < WitherTitleHelper.MIN_TITLE || id > WitherTitleHelper.MAX_TITLE) return 0;
        return id;
    }

    public static boolean isTitleStar(ItemStack stack) {
        return getTitleId(stack) > 0;
    }
}
