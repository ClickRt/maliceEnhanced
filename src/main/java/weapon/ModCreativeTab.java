package weapon;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "malice_enhanced");

    public static final RegistryObject<CreativeModeTab> MALICE_ENHANCED_TAB = TABS.register(
            "malice_enhanced_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.malice_enhanced"))
                    .icon(() -> new ItemStack(WeaponItems.SOUL_SWORD.get()))
                    .displayItems((params, output) -> {
                        // 创造栏里的无垠长魂之剑也直接注入黑洞法术，
                        // 否则它只是个空白 ItemStack，UI 上不会显示注入法术
                        ItemStack sword = new ItemStack(WeaponItems.SOUL_SWORD.get());
                        IronSpellsCompat.initializeSpellContainer(sword);
                        output.accept(sword);
                        output.accept(WeaponItems.SOUL_BOW.get());
                        output.accept(WeaponItems.STAR_CORONA_CRYSTAL.get());
                    })
                    .build()
    );
}


