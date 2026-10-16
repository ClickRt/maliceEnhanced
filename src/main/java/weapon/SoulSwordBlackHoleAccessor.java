package weapon;

import net.minecraft.world.phys.Vec3;

public interface SoulSwordBlackHoleAccessor {
    int soulSword$getColor();

    void soulSword$setColor(int color);

    float soulSword$getDamage();

    /** 锚定黑洞：每 tick 强制回到锚点（未定义凋灵亡语黑洞专用，防止上飘） */
    void witherTitle$setAnchor(Vec3 pos);
}
