package weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class SoulBowItem extends BowItem {
    public static final double BASE_DAMAGE = 50.0D; // 基础伤害50点
    public static final float FULL_DRAW_VELOCITY = 6.0F;  //基础动量=6
    public static final String KILLS_TAG = "SoulBowKills";
    public static final int KILLS_PER_DAMAGE = 100;
    /** 三连发的偏航角偏移：中间 0°，两侧各偏 2°（上一发为左、下一发为右） */
    private static final float[] TRIPLE_SHOT_YAW_OFFSETS = {-2.0F, 0.0F, 2.0F};

    public static int getKills(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasTag()) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(KILLS_TAG);
    }

    public static void addKill(ItemStack stack, Entity owner) {
        if (stack.isEmpty()) {
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(KILLS_TAG, tag.getInt(KILLS_TAG) + 1);
        if (owner instanceof ServerPlayer player) {
            player.containerMenu.broadcastChanges();
        }
    }

    public static double getShotBaseDamage(ItemStack stack) {
        return BASE_DAMAGE + getKills(stack) / KILLS_PER_DAMAGE;
    }

    public SoulBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        stack.getOrCreateTag().putBoolean("Unbreakable", true);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide) {
            stack.getOrCreateTag().putBoolean("Unbreakable", true);
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
    public AbstractArrow customArrow(AbstractArrow arrow) {
        return SoulBowArrow.wrap(arrow);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        InteractionResultHolder<ItemStack> ret = net.minecraftforge.event.ForgeEventFactory.onArrowNock(
                stack, level, player, hand, true);
        if (ret != null) {
            return ret;
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 优先取药水箭 / 光灵箭；没有则视为无弹药，射出临时普通箭 */
    private static ItemStack findSpecialArrow(Player player) {
        ItemStack offhand = player.getOffhandItem();
        if (isSpecialArrow(offhand)) {
            return offhand;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isSpecialArrow(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isSpecialArrow(ItemStack stack) {
        return stack.is(Items.SPECTRAL_ARROW) || stack.is(Items.TIPPED_ARROW);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player)) {
            return;
        }

        ItemStack projectile = findSpecialArrow(player);
        boolean conjured = projectile.isEmpty();

        int charge = this.getUseDuration(stack) - timeLeft;
        charge = net.minecraftforge.event.ForgeEventFactory.onArrowLoose(
                stack, level, player, charge, true);
        if (charge < 0) {
            return;
        }

        if (conjured) {
            projectile = new ItemStack(Items.ARROW);
        }

        float power = getPowerForTime(charge);
        if ((double) power < 0.1D) {
            return;
        }

        if (!level.isClientSide) {
            ArrowItem arrowItem = projectile.getItem() instanceof ArrowItem item ? item : (ArrowItem) Items.ARROW;
            int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
            int punch = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack);
            boolean flaming = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack) > 0;

            List<LivingEntity> homingTargets = SoulBowHoming.pickAlongLook(
                    level, player, player.getEyePosition(), player.getLookAngle(),
                    SoulBowHoming.AIM_RANGE, SoulBowHoming.AIM_CONE_COS, TRIPLE_SHOT_YAW_OFFSETS.length);
            int[] slotRank = new int[TRIPLE_SHOT_YAW_OFFSETS.length];
            Arrays.fill(slotRank, -1);
            int[] centerFirst = {1, 0, 2};
            for (int r = 0; r < centerFirst.length; r++) {
                slotRank[centerFirst[r]] = r;
            }

            for (int i = 0; i < TRIPLE_SHOT_YAW_OFFSETS.length; i++) {
                float yawOffset = TRIPLE_SHOT_YAW_OFFSETS[i];
                AbstractArrow arrow = customArrow(arrowItem.createArrow(level, projectile, player));
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOffset, 0.0F,
                        power * FULL_DRAW_VELOCITY, 1.0F);
                if (power == 1.0F) {
                    arrow.setCritArrow(true);
                }

                if (arrow instanceof SoulBowArrow soulArrow) {
                    soulArrow.setSourceBow(stack);
                    if (conjured) {
                        soulArrow.markConjured();
                    }
                }
                arrow.setBaseDamage(getShotBaseDamage(stack));
                if (powerLevel > 0) {
                    arrow.setBaseDamage(arrow.getBaseDamage() + (double) powerLevel * 0.5D + 0.5D);
                }

                if (punch > 0) {
                    arrow.setKnockback(punch);
                }

                if (flaming) {
                    arrow.setSecondsOnFire(100);
                }

                if (conjured) {
                    arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                } else if (player.getAbilities().instabuild) {
                    arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                }

                int rank = slotRank[i];
                if (arrow instanceof SoulBowArrow soulArrow && rank >= 0 && rank < homingTargets.size()) {
                    soulArrow.setHomingTarget(homingTargets.get(rank));
                }

                level.addFreshEntity(arrow);
            }
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        if (!conjured && !player.getAbilities().instabuild) {
            projectile.shrink(1);
            if (projectile.isEmpty()) {
                player.getInventory().removeItem(projectile);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public Component getName(ItemStack stack) {
        String text = Component.translatable(this.getDescriptionId(stack)).getString();
        return makeGradient(text);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.malice_enhanced.soul_bow.kills", getKills(stack))
                .withStyle(ChatFormatting.DARK_GRAY));
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
}
