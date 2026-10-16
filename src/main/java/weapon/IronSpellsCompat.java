package weapon;

import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public final class IronSpellsCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("IronSpellsCompat");

    private static final String MOD_ID = "irons_spellbooks";
    private static final String ISPELL_CONTAINER = "io.redspace.ironsspellbooks.api.spells.ISpellContainer";
    private static final String ABSTRACT_SPELL = "io.redspace.ironsspellbooks.api.spells.AbstractSpell";
    private static final String SPELL_REGISTRY = "io.redspace.ironsspellbooks.api.registry.SpellRegistry";
    private static final String ATTRIBUTE_REGISTRY = "io.redspace.ironsspellbooks.api.registry.AttributeRegistry";

    private static final UUID MAX_MANA_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567891");
    private static final UUID COOLDOWN_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567892");
    private static final UUID SPELL_POWER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567893");
    private static final UUID CAST_TIME_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567894");
    private static final UUID MANA_REGEN_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567895");

    private IronSpellsCompat() {
    }

    public static boolean isLoaded() {
        try {
            ModList modList = ModList.get();
            return modList != null && modList.isLoaded(MOD_ID);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void initializeSpellContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isLoaded()) return;
        try {
            Class<?> containerApi = Class.forName(ISPELL_CONTAINER);
            Method isContainer = containerApi.getMethod("isSpellContainer", ItemStack.class);
            if (Boolean.TRUE.equals(isContainer.invoke(null, stack))) return;

            Object container = containerApi.getMethod("create", int.class, boolean.class, boolean.class)
                    .invoke(null, 1, true, false);
            Object blackHole = getBlackHoleSpell();
            if (blackHole == null) return;

            Class<?> abstractSpell = Class.forName(ABSTRACT_SPELL);
            container.getClass().getMethod("addSpell", abstractSpell, int.class, boolean.class, ItemStack.class)
                    .invoke(container, blackHole, 6, true, null);
            containerApi.getMethod("save", ItemStack.class).invoke(container, stack);
        } catch (Exception e) {
            LOGGER.warn("[IronSpellsCompat] failed to imbue soul_sword with black_hole: {}", e.toString());
        }
    }

    public static void addIronAttributes(Multimap<Attribute, AttributeModifier> modifiers) {
        if (!isLoaded()) return;
        try {
            Class<?> registry = Class.forName(ATTRIBUTE_REGISTRY);
            modifiers.put(getAttribute(registry, "MAX_MANA"),
                    new AttributeModifier(MAX_MANA_UUID, "SoulSword max mana", 1000.0, AttributeModifier.Operation.ADDITION));
            modifiers.put(getAttribute(registry, "COOLDOWN_REDUCTION"),
                    new AttributeModifier(COOLDOWN_UUID, "SoulSword cooldown", 0.8, AttributeModifier.Operation.MULTIPLY_BASE));
            modifiers.put(getAttribute(registry, "CAST_TIME_REDUCTION"),
                    new AttributeModifier(CAST_TIME_UUID, "SoulSword cast time", 0.5, AttributeModifier.Operation.MULTIPLY_BASE));
            modifiers.put(getAttribute(registry, "MANA_REGEN"),
                    new AttributeModifier(MANA_REGEN_UUID, "SoulSword mana regen", 1.0, AttributeModifier.Operation.MULTIPLY_BASE));
            modifiers.put(getAttribute(registry, "SPELL_POWER"),
                    new AttributeModifier(SPELL_POWER_UUID, "SoulSword spell power", 10.0, AttributeModifier.Operation.MULTIPLY_BASE));
        } catch (Exception e) {
            LOGGER.warn("[IronSpellsCompat] failed to add soul_sword spell attributes: {}", e.toString());
        }
    }

    public static boolean isSoulSwordCast(Player player) {
        if (!isLoaded() || player == null) return false;
        if (SoulSwordEventHandler.hasSoulSwordInHands(player)) return true;
        try {
            Class<?> magicDataClass = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Object magicData = findCompatibleMethod(magicDataClass, "getPlayerMagicData", player)
                    .invoke(null, player);
            ItemStack stack = (ItemStack) magicDataClass.getMethod("getPlayerCastingItem")
                    .invoke(magicData);
            if (isSoulSword(stack)) return true;

            String slot = (String) magicDataClass.getMethod("getCastingEquipmentSlot")
                    .invoke(magicData);
            if ("mainhand".equals(slot) || "main_hand".equals(slot)) {
                return isSoulSword(player.getMainHandItem());
            }
            if ("offhand".equals(slot) || "off_hand".equals(slot)) {
                return isSoulSword(player.getOffhandItem());
            }
            if (slot == null || slot.isEmpty()) {
                return SoulSwordEventHandler.hasSoulSwordInHands(player);
            }
        } catch (Exception e) {
            LOGGER.warn("[IronSpellsCompat] isSoulSwordCast failed: {}", e.toString());
            return SoulSwordEventHandler.hasSoulSwordInHands(player);
        }
        return false;
    }

    public static String getCastingSlot(Player player) {
        if (!isLoaded() || player == null) return "";
        try {
            Class<?> magicDataClass = Class.forName("io.redspace.ironsspellbooks.api.magic.MagicData");
            Object magicData = findCompatibleMethod(magicDataClass, "getPlayerMagicData", player)
                    .invoke(null, player);
            String slot = (String) magicDataClass.getMethod("getCastingEquipmentSlot")
                    .invoke(magicData);
            return slot == null ? "" : slot;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 无垠长魂之剑的 +1000% 法术强度（SPELL_POWER 属性，MULTIPLY_BASE 10.0）只在剑持有期间生效。
     * 黑洞等长施法法术在 onCast 时才快照施法者属性，若施放瞬间剑已脱手，加成就会丢失。
     * 这里返回“补回剑加成后 / 当前”的倍率，供 BlackHoleMixin 修正快照伤害。
     */
    public static float getSoulSwordSpellPowerScale(Player player) {
        if (!isLoaded() || player == null) return 1.0F;
        try {
            Class<?> registry = Class.forName(ATTRIBUTE_REGISTRY);
            Attribute attribute = getAttribute(registry, "SPELL_POWER");
            AttributeInstance inst = player.getAttribute(attribute);
            if (inst == null) return 1.0F;
            double current = inst.getValue();
            // 剑的加成 = MULTIPLY_BASE 10.0 × 基础值，加到当前值上即为“持剑时”的属性值
            double boosted = current + inst.getBaseValue() * 10.0D;
            return current <= 0.0D ? 1.0F : (float) (boosted / current);
        } catch (Exception e) {
            return 1.0F;
        }
    }

    private static boolean isSoulSword(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof SoulSwordItem;
    }

    /**
     * 按方法名 + 参数类型兼容的方式查找方法，兼容 Iron's Spells 不同版本间
     * getPlayerMagicData(Player) / getPlayerMagicData(LivingEntity) 的签名变化。
     */
    private static Method findCompatibleMethod(Class<?> clazz, String name, Object arg)
            throws NoSuchMethodException {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].isAssignableFrom(arg.getClass())) {
                return m;
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "." + name);
    }

    private static Object getBlackHoleSpell() throws Exception {
        Class<?> registry = Class.forName(SPELL_REGISTRY);
        Field field = registry.getField("BLACK_HOLE_SPELL");
        Object holder = field.get(null);
        if (holder == null) return null;
        return holder.getClass().getMethod("get").invoke(holder);
    }

    private static Attribute getAttribute(Class<?> registry, String fieldName) throws Exception {
        Object holder = registry.getField(fieldName).get(null);
        return (Attribute) holder.getClass().getMethod("get").invoke(holder);
    }
}
