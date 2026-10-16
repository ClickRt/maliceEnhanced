package weapon.starcrystal;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.Map;

public final class StarCoronaCurios {
    private StarCoronaCurios() {
    }

    public static boolean isLoaded() {
        // Do not cache this during class initialization: Forge can load this
        // helper before Curios has finished registering its capabilities.
        return ModList.get().isLoaded("curios");
    }

    public static ItemStack findEquipped(LivingEntity entity) {
        if (entity == null) return ItemStack.EMPTY;
        if (isLoaded()) return findInCurios(entity);
        if (entity instanceof Player player) {
            if (player.getMainHandItem().getItem() instanceof StarCoronaCrystalItem) return player.getMainHandItem();
            if (player.getOffhandItem().getItem() instanceof StarCoronaCrystalItem) return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    public static boolean hasTitle(LivingEntity entity, int titleId) {
        ItemStack stack = findEquipped(entity);
        if (stack.isEmpty()) return false;
        return StarCoronaInventory.of(stack).hasTitle(titleId);
    }

    public static boolean hasSoulHeart(LivingEntity entity) {
        ItemStack stack = findEquipped(entity);
        return !stack.isEmpty() && StarCoronaInventory.of(stack).hasSoulHeart();
    }

    /** Returns the number of installed title stars on the equipped Star Corona. */
    public static int getEquippedTitleCount(LivingEntity entity) {
        ItemStack stack = findEquipped(entity);
        return stack.isEmpty() ? 0 : StarCoronaInventory.of(stack).titleCount();
    }

    /** Hidden all-damage multiplier: each installed title reduces damage by 7%. */
    public static float getEquippedTitleDamageFactor(LivingEntity entity) {
        return Math.max(0.0F, 1.0F - getEquippedTitleCount(entity) * 0.07F);
    }

    /** Used by the client tooltip handler to distinguish the Curios copy from an inventory copy. */
    public static boolean isEquippedStack(LivingEntity entity, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ItemStack equipped = findEquipped(entity);
        return !equipped.isEmpty() && equipped == stack;
    }

    @SuppressWarnings("unchecked")
    private static ItemStack findInCurios(LivingEntity entity) {
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getInv = api.getMethod("getCuriosInventory", LivingEntity.class);
            Object lazy = getInv.invoke(null, entity);
            if (!(lazy instanceof LazyOptional<?> opt) || !opt.isPresent()) return ItemStack.EMPTY;
            Object handler = opt.orElse(null);
            if (handler == null) return ItemStack.EMPTY;

            // Prefer the flattened equipped handler. It is the public Curios
            // API and remains available even when a slot implementation does
            // not expose its internal stacks map through reflection.
            try {
                Method getEquipped = handler.getClass().getMethod("getEquippedCurios");
                getEquipped.setAccessible(true);
                Object equipped = getEquipped.invoke(handler);
                Method getSlots = equipped.getClass().getMethod("getSlots");
                Method getStackInSlot = equipped.getClass().getMethod("getStackInSlot", int.class);
                getSlots.setAccessible(true);
                getStackInSlot.setAccessible(true);
                int slots = (Integer) getSlots.invoke(equipped);
                for (int i = 0; i < slots; i++) {
                    Object stackObj = getStackInSlot.invoke(equipped, i);
                    if (stackObj instanceof ItemStack stack && stack.getItem() instanceof StarCoronaCrystalItem) {
                        return stack;
                    }
                }
            } catch (Throwable ignored) {
                // Fall back to the per-identifier map below.
            }

            Method getCurios = handler.getClass().getMethod("getCurios");
            getCurios.setAccessible(true);
            Object mapObj = getCurios.invoke(handler);
            if (!(mapObj instanceof Map<?, ?> map)) return ItemStack.EMPTY;
            // The crystal may be placed in the dedicated slot or in Curios' generic "curio" slot.
            // Scan every equipped handler so its effects do not depend on the slot identifier.
            for (Object stacksHandler : map.values()) {
                if (stacksHandler == null) continue;
                Method getStacks = stacksHandler.getClass().getMethod("getStacks");
                getStacks.setAccessible(true);
                Object dynamic = getStacks.invoke(stacksHandler);
                Method getSlots = dynamic.getClass().getMethod("getSlots");
                getSlots.setAccessible(true);
                int slots = (Integer) getSlots.invoke(dynamic);
                Method getStackInSlot = dynamic.getClass().getMethod("getStackInSlot", int.class);
                getStackInSlot.setAccessible(true);
                for (int i = 0; i < slots; i++) {
                    Object stackObj = getStackInSlot.invoke(dynamic, i);
                    if (stackObj instanceof ItemStack stack && stack.getItem() instanceof StarCoronaCrystalItem) {
                        return stack;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }
}
