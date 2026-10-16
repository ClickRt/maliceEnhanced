package weapon.net;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import weapon.SoulSwordEventHandler;

import java.util.function.Supplier;

public class S2CSoulSwordTotemPack {
    private final ItemStack stack;
    private final int entityId;
    /** 复活后 30 秒无敌帧窗口剩余 tick，客户端用于控制灵魂粒子特效 */
    private final int soulHurtDefendDuration;

    public S2CSoulSwordTotemPack(FriendlyByteBuf buf) {
        this.stack = buf.readItem();
        this.entityId = buf.readInt();
        this.soulHurtDefendDuration = buf.readInt();
    }

    public S2CSoulSwordTotemPack(ItemStack stack, int entityId, int soulHurtDefendDuration) {
        this.stack = stack;
        this.entityId = entityId;
        this.soulHurtDefendDuration = soulHurtDefendDuration;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeItem(this.stack);
        buf.writeInt(this.entityId);
        buf.writeInt(this.soulHurtDefendDuration);
    }

    public void run(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                Minecraft instance = Minecraft.getInstance();
                ClientLevel world = instance.level;
                if (world != null) {
                    Entity entity = world.getEntity(this.entityId);
                    if (entity != null) {
                        // 客户端记录 soulHurtDefend 剩余时长，用于每 tick 粒子特效
                        SoulSwordEventHandler.setClientSoulHurtDefend(entity.getUUID(), soulHurtDefendDuration);

                        instance.particleEngine.createTrackingEmitter(entity, ParticleTypes.TOTEM_OF_UNDYING, 30);
                        world.playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                                SoundEvents.TOTEM_USE, entity.getSoundSource(), 1.0F, 1.0F, false);
                        instance.gameRenderer.displayItemActivation(this.stack);
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}