package weapon;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import weapon.starcrystal.StarCoronaCrystalMenu;

public class WeaponRegistries {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "malice_enhanced");
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, "malice_enhanced");

    public static final RegistryObject<MobEffect> RESISTANCE_BLOCKADE = EFFECTS.register("resistance_blockade", ResistanceBlockadeEffect::new);
    public static final RegistryObject<MenuType<StarCoronaCrystalMenu>> STAR_CORONA_CRYSTAL = MENUS.register(
            "star_corona_crystal", () -> IForgeMenuType.create(StarCoronaCrystalMenu::createClient));

    public static void init(IEventBus eventBus) {
        WeaponItems.ITEMS.register(eventBus);
        WeaponEntities.init(eventBus);
        WeaponSounds.SOUNDS.register(eventBus);
        ModCreativeTab.TABS.register(eventBus);
        EFFECTS.register(eventBus);
        MENUS.register(eventBus);
    }
}