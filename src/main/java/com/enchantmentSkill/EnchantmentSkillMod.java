package com.enchantmentSkill;

import com.enchantmentSkill.Curse.mockingCurse.MockingCurseRegistries;
import com.enchantmentSkill.positive.bloodDebt.BloodDebtRegistries;
import com.enchantmentSkill.positive.luckHit.LuckyHitRegistries;
import com.enchantmentSkill.positive.pureMalice.PureMaliceRegistries;
import com.enchantmentSkill.positive.recoil.RecoilRegistries;
import com.enchantmentSkill.positive.shatter.CustomModRegistries;
import com.enchantmentSkill.positive.tentacles.TheExtensionOfTentaclesRegistries;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weapon.WeaponRegistries;
import wither_title.WitherTitleConfig;
import wither_title.WitherTitleEntities;

@Mod("malice_enhanced")
public class EnchantmentSkillMod {
    private static final Logger LOGGER = LoggerFactory.getLogger("ExtendedEnchantments");

    public EnchantmentSkillMod() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        LOGGER.info("Loading Extended Enchantments...");
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, WitherTitleConfig.SPEC);

        CustomModRegistries.init(bus);
        LuckyHitRegistries.init(bus);
        PureMaliceRegistries.init(bus);
        TheExtensionOfTentaclesRegistries.init(bus);
        BloodDebtRegistries.init(bus);
        RecoilRegistries.init(bus);
        MockingCurseRegistries.init(bus);
        WeaponRegistries.init(bus);
        WitherTitleEntities.init(bus);

        // 仆从实体属性注册（须在实体 DeferredRegister 之后，事件阶段注册）
        bus.addListener(this::onAttributeCreation);

        // 灵魂仪式类型注册：仅在 goety 加载时执行（JEI 初始化前完成）
        if (ModList.get().isLoaded("goety")) {
            com.enchantmentSkill.goetyritual.SoulRitualRegistries.init();
        }

        LOGGER.info("Extended Enchantments loaded successfully!");
    }

    private void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(WitherTitleEntities.UNDEFINED_WITHER_SERVANT.get(),
                wither_title.UndefinedWitherServant.createAttributes().build());
        event.put(WitherTitleEntities.STAR_CORONA_SERVANT_ALLAY.get(),
                weapon.starcrystal.StarCoronaServantAllay.createAttributes().build());
    }
}