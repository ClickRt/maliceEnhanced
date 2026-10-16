package weapon;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ForgeMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weapon.client.SoulSwordClientExtensions;

import java.lang.reflect.Method;
import java.util.function.Consumer;

import static weapon.WeaponRegistries.RESISTANCE_BLOCKADE;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SoulSwordItem extends SwordItem {

    private static final Logger LOGGER = LoggerFactory.getLogger("SoulSword");

    private static boolean hasOdamane(Player player) {
        try {
            Class<?> helper = Class.forName("com.mega.revelationfix.util.entity.ATAHelper2");
            Object result = helper.getMethod("hasOdamane", net.minecraft.world.entity.Entity.class)
                    .invoke(null, player);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            LOGGER.info("[SoulSword] hasOdamane reflection failed: " + e);
            return false;
        }
    }

    private static final Tier SOUL_TIER = new Tier() {
        @Override
        public int getUses() {
            return -1;
        }

        @Override
        public float getSpeed() {
            return 10.0f;
        }

        @Override
        public float getAttackDamageBonus() {
            return 2.718281828e33f;
        }

        @Override
        public int getLevel() {
            return 4;
        }

        @Override
        public int getEnchantmentValue() {
            return 15;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.NETHER_STAR);
        }
    };

    public SoulSwordItem() {
        super(SOUL_TIER, 0, -2.2f, new Properties().fireResistant().durability(-1));
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
        IronSpellsCompat.initializeSpellContainer(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide) {
            stack.getOrCreateTag().putBoolean("Unbreakable", true);
            IronSpellsCompat.initializeSpellContainer(stack);
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.isInvulnerable()) {
            entity.setInvulnerable(true);
        }
        return false;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CUSTOM;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SoulSwordClientExtensions.INSTANCE);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    private static final int CLICK_THRESHOLD_TICKS = 5;

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(itemstack, level.isClientSide());
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        if (level.isClientSide()) return;

        int ticksUsed = getUseDuration(stack) - timeLeft;
        if (ticksUsed < CLICK_THRESHOLD_TICKS) {
            if (player.getCooldowns().isOnCooldown(this)) return;
            fireAOE(level, player);
            player.getCooldowns().addCooldown(this, 5);
        }
    }

    private void fireAOE(Level level, Player player) {
        DamageSource voidSource = new DamageSource(
                level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                        .getHolderOrThrow(SOUL_SWORD_DAMAGE), player);
        AABB box = player.getBoundingBox().inflate(100.0D);
        for (Entity e : level.getEntities(player, box)) {
            if (StarFantasyHadesBan.tryBanAndDiscard(e)) {
                continue;
            }
            if (!(e instanceof LivingEntity target)) continue;
            if (e instanceof Player) continue;
            if (isPlayerServant(e)) continue;
            if (e instanceof TamableAnimal tamed && tamed.isTame()) continue;
            if ("dummmmmmy:target_dummy".equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString())) {
                continue;
            }

            // 外部 Mod 走灵魂直写（其 actuallyHurt 覆盖版吞掉原版伤害）；其余照常
            // 抗性压制：右键AOE命中即施加 8 秒（160 tick），清除已有抗性提升
            target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            target.addEffect(new MobEffectInstance(RESISTANCE_BLOCKADE.get(), 160, 0));

            if (!SoulSwordEventHandler.tryDirectWriteDamage(target, player, 100.0f)) {
                target.hurt(voidSource, 100.0f);
            } else {
                SoulSwordEventHandler.markSoulSwordHealSuppression(target);
            }
        }
        level.playSound(null, player.blockPosition(),
                WeaponSounds.SWING_SWORD.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static boolean isPlayerServant(Entity entity) {
        try {
            Class<?> ownedClass = Class.forName("com.Polarice3.Goety.common.entities.neutral.Owned");
            if (!ownedClass.isInstance(entity)) return false;
            Object owner = ownedClass.getMethod("getTrueOwner").invoke(entity);
            return owner instanceof Player;
        } catch (Exception e) {
            return false;
        }
    }


    @Override
    public boolean canPerformAction(ItemStack stack, net.minecraftforge.common.ToolAction toolAction) {
        return net.minecraftforge.common.ToolActions.DEFAULT_SWORD_ACTIONS.contains(toolAction);
    }

    private static final ResourceKey<DamageType> SOUL_SWORD_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("malice_enhanced", "soul_sword"));

    private static final Map<String, Float> BYPASS_DAMAGE_ENTITIES = Map.of(
            "cataclysm:the_leviathan", 2371828e24f,
            "goety:damned", 2.71828e24f
    );

    private static final Set<String> DIRECT_KILL_ENTITIES = Set.of(
            "jerotesvillage:variant_zsiein"
    );

    private static final Set<String> REMOVE_ON_ATTACK_ENTITIES = Set.of(
            "goety:fire_tornado",
            "goety:obsidian_monolith"
    );

    private static final ResourceKey<DamageType> AVARITIA_INFINITY_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath("avaritia", "infinity"));

    private static final Map<EquipmentSlot, String> INFINITY_ARMOR = Map.of(
            EquipmentSlot.HEAD, "avaritia:infinity_helmet",
            EquipmentSlot.CHEST, "avaritia:infinity_chestplate",
            EquipmentSlot.LEGS, "avaritia:infinity_pants",
            EquipmentSlot.FEET, "avaritia:infinity_boots"
    );

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        if (player.level().isClientSide()) return false;

        String typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        LOGGER.info("[SoulSword] onLeftClickEntity target=" + typeKey + " attackerHasHalo=" + hasOdamane(player));

        if (StarFantasyHadesBan.tryBanAndDiscard(entity)) {
            playAttack(player);
            return true;
        }

        if (REMOVE_ON_ATTACK_ENTITIES.contains(typeKey)) {
            entity.discard();
            playAttack(player);
            return true;
        }

        LivingEntity target = resolveVictim(entity);
        if (target == null) return false;

        // 抗性压制：左键平A命中即施加 8 秒（160 tick），清除已有抗性提升
        // 放在此处而非 onSoulSwordAttack，是因为 外部 Mod 等高优先级实体走
        // tryDirectWriteDamage 后 return true 吞掉原版 hurt()，LivingDamageEvent 不触发
        target.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        target.addEffect(new MobEffectInstance(RESISTANCE_BLOCKADE.get(), 160, 0));

        if (SoulSwordEventHandler.isApollyonForm(target)) {
            killApollyon(target, player);
            playAttack(player);
            return true;
        }

        String targetType = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString();

        if (DIRECT_KILL_ENTITIES.contains(targetType)) {
            if ("jerotesvillage:variant_zsiein".equals(targetType)) {
                directKillVariantZsiein(target, player);
            } else {
                DamageSource voidSource = new DamageSource(
                        player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                                .getHolderOrThrow(SOUL_SWORD_DAMAGE), player);
                directKill(target, player, voidSource);
            }
            playAttack(player);
            return true;
        }

        boolean infinite = isInfiniteArmorUser(target);
        LOGGER.info("[SoulSword] isInfiniteArmorUser=" + infinite + " hp=" + target.getHealth() + "/" + target.getMaxHealth());

        if (infinite) {
            try {
                DamageSource infinitySource = new DamageSource(
                        player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                                .getHolderOrThrow(AVARITIA_INFINITY_DAMAGE), player);
                directKill(target, player, infinitySource);
                playAttack(player);
                return true;
            } catch (Exception e) {
                LOGGER.info("[SoulSword] avaritia infinity damage type NOT found, falling through");
            }
        }

        Float damage = BYPASS_DAMAGE_ENTITIES.get(targetType);

        if (damage != null) {
            DamageSource voidSource = new DamageSource(
                    player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                            .getHolderOrThrow(SOUL_SWORD_DAMAGE), player);
            target.hurt(voidSource, damage);
            playAttack(player);
            return true;
        }

        // 对所有实体均走直写改血，绕过 setHealth 及其所有限伤/无敌拦截
        // （每刀 2% 最大生命 + 20 点绝对真伤，绕过闸门，附带 -50% 治疗压制）。
        // 特殊 Boss 需要吞掉原版攻击避免 2.7e33 伤害反弹，但普通实体不需要，
        // 让面板 2.7e33 伤害也能打出来，兼顾"秒杀普通怪+真伤磨BOSS"。
        if (SoulSwordEventHandler.tryDirectWriteDamage(target, player,
                target.getMaxHealth() * 0.02F + 20.0F)) {
            SoulSwordEventHandler.markSoulSwordHealSuppression(target);
            playAttack(player);
            // 只有 外部 Mod 需要吞原版伤害，其他实体放原版伤害过去（打出面板 e*10^24）
            return SoulSwordEventHandler.isSpecialHealthEntity(target);
        }

        return false;
    }

    private void directKill(LivingEntity victim, Player player, DamageSource source) {
        if (victim.level().isClientSide || victim.isDeadOrDying()) {
            return;
        }
        victim.setNoActionTime(0);
        victim.walkAnimation.setSpeed(1.5F);
        victim.lastHurt = Float.MAX_VALUE;
        victim.lastHurtByPlayerTime = 100;
        victim.lastDamageSource = source;
        victim.lastDamageStamp = victim.level().getGameTime();
        victim.getCombatTracker().recordDamage(source, Float.MAX_VALUE);
        victim.setLastHurtByPlayer(player);
        victim.setHealth(victim.getHealth() - Float.MAX_VALUE);
        victim.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_DAMAGE);
        victim.hurtDuration = 10;
        victim.hurtTime = victim.hurtDuration;
        victim.level().broadcastDamageEvent(victim, source);
        victim.hurtMarked = true;
        if (victim.isDeadOrDying()) {
            directDie(victim, player, source);
        }
    }

    private void directDie(LivingEntity victim, Player player, DamageSource source) {
        if (victim.isRemoved() || !victim.isDeadOrDying()) {
            return;
        }
        victim.dead = true;
        victim.getCombatTracker().recheckStatus();
        if (victim.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            if (player != null) {
                player.killedEntity(serverLevel, victim);
            }
            victim.gameEvent(net.minecraft.world.level.gameevent.GameEvent.ENTITY_DIE);
            victim.dropAllDeathLoot(source);
            victim.level().broadcastEntityEvent(victim, (byte) 3);
        }
        victim.setPose(net.minecraft.world.entity.Pose.DYING);
    }

    private void killApollyon(LivingEntity victim, Player player) {
        if (victim.level().isClientSide() || victim.isRemoved()) {
            return;
        }
        LOGGER.info("[SoulSword] Apollyon one-shot kill");

        // 新旧两版通吃：旧版置零 apollyonHealth 同步字段，新版直写 DATA_HEALTH_ID，
        // 均绕过 setHealth 的每次伤害上限 / 命中冷却。
        zeroApollyonHealth(victim);

        DamageSource source = player.damageSources().playerAttack(player);
        victim.setNoActionTime(0);
        victim.walkAnimation.setSpeed(1.5F);
        victim.lastHurt = Float.MAX_VALUE;
        victim.lastHurtByPlayerTime = 100;
        victim.lastDamageSource = source;
        victim.lastDamageStamp = victim.level().getGameTime();
        victim.setLastHurtByPlayer(player);
        victim.getCombatTracker().recordDamage(source, Float.MAX_VALUE);
        victim.invulnerableTime = 0;
        victim.hurtTime = 0;

        if (!victim.isDeadOrDying()) {
            victim.setHealth(0.0F);
        }
        victim.die(source);

        // 死亡事件若被其他 mod 取消，强制走直接死亡
        if (!victim.isRemoved() && !victim.dead && victim.isDeadOrDying()) {
            directDie(victim, player, source);
        }
    }

    private static void zeroApollyonHealth(LivingEntity victim) {
        Class<?> apostleClass = null;
        try {
            apostleClass = Class.forName("com.Polarice3.Goety.common.entities.boss.Apostle");
        } catch (Throwable t) {
            LOGGER.info("[SoulSword] zeroApollyonHealth: Apostle class not found: " + t);
        }

        // 旧版 RevelationFix（≤4.2）：血量存于 apollyonHealth 同步字段（revelaionfix$setApollyonHealth）
        if (apostleClass != null && apostleClass.isInstance(victim)) {
            try {
                Method setter;
                try {
                    setter = apostleClass.getMethod("revelaionfix$setApollyonHealth", float.class);
                } catch (NoSuchMethodException e) {
                    setter = apostleClass.getDeclaredMethod("revelaionfix$setApollyonHealth", float.class);
                }
                setter.setAccessible(true);
                setter.invoke(victim, 0.0F);
            } catch (Throwable t) {
                LOGGER.info("[SoulSword] zeroApollyonHealth (apollyonHealth field) failed: " + t);
            }
            // 新版 RevelationFix 4.4：setter 运行时被 ASM 改名，血量实际在 MegaCapability，
            // 走未改名的 EventUtil.setData
            try {
                Class<?> eventUtilClass = Class.forName("com.mega.revelationfix.util.EventUtil");
                eventUtilClass.getMethod("setData", apostleClass, float.class).invoke(null, victim, 0.0F);
            } catch (Throwable t) {
                LOGGER.info("[SoulSword] zeroApollyonHealth (EventUtil capability) failed: " + t);
            }
        }

        // 纯 2.3.3fix（无 RevelationFix）：血量在普通 DATA_HEALTH_ID，直写 0 绕过 setHealth 限伤
        try {
            SoulSwordEventHandler.directSetHealth(victim, 0.0F);
        } catch (Throwable t) {
            LOGGER.info("[SoulSword] zeroApollyonHealth (direct health write) failed: " + t);
        }
    }

    private void directKillVariantZsiein(LivingEntity victim, Player player) {
        try {
            java.lang.reflect.Method setWantAway = victim.getClass().getMethod("setWantAway", boolean.class);
            setWantAway.setAccessible(true);
            setWantAway.invoke(victim, true);
        } catch (Exception e) {
            LOGGER.error("[SoulSword] setWantAway failed: " + e);
        }
        try {
            java.lang.reflect.Method setNormal = victim.getClass().getMethod("setExaltedNormal", float.class);
            setNormal.setAccessible(true);
            setNormal.invoke(victim, 1.0F);
        } catch (Exception e) {
            LOGGER.error("[SoulSword] setExaltedNormal failed: " + e);
        }
        try {
            java.lang.reflect.Method setAway = victim.getClass().getMethod("setExaltedAway", float.class);
            setAway.setAccessible(true);
            setAway.invoke(victim, 1.0F);
        } catch (Exception e) {
            LOGGER.error("[SoulSword] setExaltedAway failed: " + e);
        }

        // 掉落 Boss 战利品 + 经验 (AT 使 dropAllDeathLoot 公开)
        try {
            victim.dropAllDeathLoot(player.damageSources().playerAttack(player));
        } catch (Exception e) {
            LOGGER.error("[SoulSword] dropAllDeathLoot failed: " + e);
        }
        player.killedEntity((net.minecraft.server.level.ServerLevel) victim.level(), victim);
        // 不立即移除 —— 让 tickDeath 自然播放"放逐"序列 (死亡动画 + "你亲眼见证了放逐"播报 + 额外66666经验)
    }

    private LivingEntity resolveVictim(Entity entity) {
        if (entity instanceof LivingEntity le) {
            return le;
        }
        if (entity instanceof net.minecraftforge.entity.PartEntity<?> part
                && part.getParent() instanceof LivingEntity parent) {
            return parent;
        }
        return null;
    }

    private static void playAttack(Player player) {
        player.swing(InteractionHand.MAIN_HAND);
        player.level().playSound(null, player.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static boolean isInfiniteArmorUser(LivingEntity entity) {
        for (Map.Entry<EquipmentSlot, String> entry : INFINITY_ARMOR.entrySet()) {
            ItemStack slotStack = entity.getItemBySlot(entry.getKey());
            if (slotStack.isEmpty()) return false;
            if (!entry.getValue().equals(BuiltInRegistries.ITEM.getKey(slotStack.getItem()).toString())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        String text = Component.translatable(this.getDescriptionId(stack)).getString();
        return makeGradient(text);
    }

    private static Component makeGradient(String text) {
        long tick = System.currentTimeMillis() / 100;
        MutableComponent result = Component.literal("");
        int n = Math.max(text.length(), 1);
        for (int i = 0; i < text.length(); i++) {
            float t = ((tick + i * (20 / n)) % 20) / 20.0f;
            int color = lerp3Color(0x06FFFA, 0x0412FC, 0x7359EB, t);
            result.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
        }
        return result;
    }

    private static int lerp3Color(int c1, int c2, int c3, float t) {
        if (t < 1.0f / 3.0f) {
            return lerpColor(c1, c2, t * 3.0f);
        } else if (t < 2.0f / 3.0f) {
            return lerpColor(c2, c3, (t - 1.0f / 3.0f) * 3.0f);
        } else {
            return lerpColor(c3, c1, (t - 2.0f / 3.0f) * 3.0f);
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> modifiers = HashMultimap.create(super.getDefaultAttributeModifiers(slot));
        if (slot == EquipmentSlot.MAINHAND) {
            modifiers.put(ForgeMod.ENTITY_REACH.get(),
                    new AttributeModifier(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
                            "SoulSword reach bonus", 3.0, AttributeModifier.Operation.ADDITION));
            IronSpellsCompat.addIronAttributes(modifiers);
        }
        return modifiers;
    }
}