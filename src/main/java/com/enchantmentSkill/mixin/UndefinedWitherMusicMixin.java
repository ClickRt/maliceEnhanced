package com.enchantmentSkill.mixin;

import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wither_title.client.UndefinedWitherMusic;

@Mixin(MusicManager.class)
public abstract class UndefinedWitherMusicMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void malice$holdBossMusic(CallbackInfo ci) {
        if (UndefinedWitherMusic.isPlaying()) {
            ci.cancel();
        }
    }
}
