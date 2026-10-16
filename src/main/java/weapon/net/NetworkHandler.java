package weapon.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class NetworkHandler {
    private static final String VERSION = "1.0";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath("malice_enhanced", "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);
    private static int id = 0;

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        CHANNEL.registerMessage(id++, S2CSoulSwordTotemPack.class,
                S2CSoulSwordTotemPack::write, S2CSoulSwordTotemPack::new,
                S2CSoulSwordTotemPack::run, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
