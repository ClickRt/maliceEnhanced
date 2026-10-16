package wither_title.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import weapon.WeaponSounds;
import wither_title.WitherTitleHelper;

@Mod.EventBusSubscriber(modid = "malice_enhanced", bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class UndefinedWitherMusic {
    private static final double RANGE = 128.0D;
    private static SoundInstance playing;

    private UndefinedWitherMusic() {
    }

    public static boolean isPlaying() {
        return playing != null;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            stop(minecraft);
            return;
        }
        if (nearUndefinedWither(minecraft)) {
            if (playing == null || !minecraft.getSoundManager().isActive(playing)) {
                minecraft.getMusicManager().stopPlaying();
                playing = new SimpleSoundInstance(
                        WeaponSounds.UNDEFINED_WITHER_BOSS.get().getLocation(),
                        SoundSource.RECORDS,
                        1.0F,
                        1.0F,
                        SoundInstance.createUnseededRandom(),
                        true,
                        0,
                        SoundInstance.Attenuation.NONE,
                        0.0D,
                        0.0D,
                        0.0D,
                        true);
                minecraft.getSoundManager().play(playing);
            }
        } else {
            stop(minecraft);
        }
    }

    private static boolean nearUndefinedWither(Minecraft minecraft) {
        AABB box = minecraft.player.getBoundingBox().inflate(RANGE);
        for (WitherBoss wither : minecraft.level.getEntitiesOfClass(WitherBoss.class, box,
                WitherTitleHelper::isUndefinedWither)) {
            if (wither.isAlive() && !wither.isRemoved()) return true;
        }
        return false;
    }

    private static void stop(Minecraft minecraft) {
        if (playing == null) return;
        minecraft.getSoundManager().stop(playing);
        playing = null;
    }
}
