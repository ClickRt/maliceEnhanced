package com.enchantmentSkill.advancements;

import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 手动发放成就。
 * <p>
 * 这些成就的判定条件无法用原版触发器表达（凋灵的最终死亡走 setRemoved、
 * 无垠长魂之剑走仪式产出），因此 JSON 里用 minecraft:impossible 占位，
 * 由对应逻辑在服务端直接 award。
 */
public final class ModAdvancements {

    private static final String NS = "malice_enhanced";

    /** 撕裂噩梦：击杀未定义凋灵 */
    public static final ResourceLocation TEAR_NIGHTMARE =
            ResourceLocation.fromNamespaceAndPath(NS, "wither/tear_nightmare");
    /** 灵魂支配：从灵魂仪式中锻出无垠长魂之剑 */
    public static final ResourceLocation SOUL_DOMINION =
            ResourceLocation.fromNamespaceAndPath(NS, "wither/soul_dominion");

    private ModAdvancements() {
    }

    /** 发放成就；成就未加载（数据包未启用）时静默跳过 */
    public static void award(ServerPlayer player, ResourceLocation id) {
        if (player == null || id == null) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        Advancement advancement = server.getAdvancements().getAdvancement(id);
        if (advancement == null) return;
        player.getAdvancements().award(advancement, "unlocked");
    }
}
