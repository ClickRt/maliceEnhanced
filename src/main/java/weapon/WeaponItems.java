package weapon;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import weapon.starcrystal.StarCoronaCrystalItem;

public class WeaponItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "malice_enhanced");

    public static final RegistryObject<Item> SOUL_SWORD = ITEMS.register("soul_sword", SoulSwordItem::new);
    public static final RegistryObject<Item> SOUL_BOW = ITEMS.register(
            "soul_bow",
            () -> new SoulBowItem(new Item.Properties().fireResistant().durability(-1))
    );
    public static final RegistryObject<Item> STAR_CORONA_CRYSTAL = ITEMS.register(
            "star_corona_crystal", StarCoronaCrystalItem::new);
}
