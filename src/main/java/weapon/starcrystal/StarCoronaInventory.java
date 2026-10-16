package weapon.starcrystal;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;

public class StarCoronaInventory extends ItemStackHandler {
    public static final String ITEMS_KEY = "StarTitles";

    private final ItemStack host;

    public StarCoronaInventory(ItemStack host) {
        super(StarCrystalSlots.TOTAL_SLOTS);
        this.host = host;
        load();
    }

    public static StarCoronaInventory of(ItemStack host) {
        return new StarCoronaInventory(host);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (slot == StarCrystalSlots.CENTER_SLOT) {
            return StarCrystalSlots.isSoulHeart(stack);
        }
        int title = StarCrystalSlots.getTitleId(stack);
        if (title <= 0) return false;
        for (int i = 0; i < StarCrystalSlots.TITLE_SLOTS; i++) {
            if (i == slot) continue;
            if (StarCrystalSlots.getTitleId(getStackInSlot(i)) == title) return false;
        }
        return true;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    protected void onContentsChanged(int slot) {
        save();
    }

    public boolean hasSoulHeart() {
        return !getStackInSlot(StarCrystalSlots.CENTER_SLOT).isEmpty();
    }

    public boolean hasTitle(int titleId) {
        for (int i = 0; i < StarCrystalSlots.TITLE_SLOTS; i++) {
            if (StarCrystalSlots.getTitleId(getStackInSlot(i)) == titleId) return true;
        }
        return false;
    }

    /** Returns true only when every distinct title from 1 through 13 is installed. */
    public boolean hasAllTitles() {
        boolean[] found = new boolean[StarCrystalSlots.TITLE_SLOTS + 1];
        for (int i = 0; i < StarCrystalSlots.TITLE_SLOTS; i++) {
            int title = StarCrystalSlots.getTitleId(getStackInSlot(i));
            if (title > 0 && title < found.length) found[title] = true;
        }
        for (int title = 1; title <= StarCrystalSlots.TITLE_SLOTS; title++) {
            if (!found[title]) return false;
        }
        return true;
    }

    public int titleCount() {
        int n = 0;
        for (int i = 0; i < StarCrystalSlots.TITLE_SLOTS; i++) {
            if (StarCrystalSlots.getTitleId(getStackInSlot(i)) > 0) n++;
        }
        return n;
    }

    public void load() {
        CompoundTag tag = host.getTag();
        if (tag == null || !tag.contains(ITEMS_KEY, Tag.TAG_LIST)) return;
        ListTag list = tag.getList(ITEMS_KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < getSlots(); i++) {
            stacks.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < list.size(); i++) {
            CompoundTag slotTag = list.getCompound(i);
            int slot = slotTag.getByte("Slot") & 255;
            if (slot < getSlots()) {
                stacks.set(slot, ItemStack.of(slotTag));
            }
        }
    }

    public void save() {
        ListTag list = new ListTag();
        for (int i = 0; i < getSlots(); i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack.isEmpty()) continue;
            CompoundTag slotTag = new CompoundTag();
            slotTag.putByte("Slot", (byte) i);
            stack.save(slotTag);
            list.add(slotTag);
        }
        host.getOrCreateTag().put(ITEMS_KEY, list);
    }
}
