package weapon.starcrystal;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import weapon.WeaponRegistries;

public class StarCoronaCrystalMenu extends AbstractContainerMenu {
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 236;
    /** 玩家背包左上角（相对面板） */
    public static final int INV_X = 8;
    public static final int INV_Y = 152;
    /** 快捷栏相对面板的 Y */
    public static final int HOTBAR_Y = INV_Y + 58;

    /** 13 个头衔之星槽位，沿法阵外围均匀分布 */
    private static final int[][] RING = {
            {79, 25}, {99, 30}, {115, 44}, {123, 64}, {120, 85},
            {108, 102}, {90, 112}, {68, 112}, {50, 102}, {38, 85},
            {35, 64}, {43, 44}, {59, 30}
    };
    /** 法阵中央：灵魂之心槽位 */
    private static final int[] CENTER = {79, 69};

    private final ItemStack host;
    private final StarCoronaInventory inventory;

    public static StarCoronaCrystalMenu createClient(int id, Inventory playerInv, FriendlyByteBuf buf) {
        boolean main = buf.readBoolean();
        ItemStack stack = main ? playerInv.player.getMainHandItem() : playerInv.player.getOffhandItem();
        return new StarCoronaCrystalMenu(id, playerInv, stack);
    }

    public StarCoronaCrystalMenu(int id, Inventory playerInv, ItemStack host) {
        super(WeaponRegistries.STAR_CORONA_CRYSTAL.get(), id);
        this.host = host;
        this.inventory = StarCoronaInventory.of(host);

        for (int i = 0; i < StarCrystalSlots.TITLE_SLOTS; i++) {
            addSlot(new SlotItemHandler(inventory, i, RING[i][0], RING[i][1]));
        }
        addSlot(new SlotItemHandler(inventory, StarCrystalSlots.CENTER_SLOT, CENTER[0], CENTER[1]));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int containerSlots = StarCrystalSlots.TOTAL_SLOTS;
        if (index < containerSlots) {
            if (!moveItemStackTo(stack, containerSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (StarCrystalSlots.isSoulHeart(stack)) {
            if (!moveItemStackTo(stack, StarCrystalSlots.CENTER_SLOT, containerSlots, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, StarCrystalSlots.TITLE_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(host.getItem() instanceof StarCoronaCrystalItem)) return false;
        return player.getMainHandItem() == host || player.getOffhandItem() == host;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        inventory.save();
    }
}
