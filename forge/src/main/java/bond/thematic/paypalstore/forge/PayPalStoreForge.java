package bond.thematic.paypalstore.forge;

import bond.thematic.paypalstore.PayPalStore;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(PayPalStore.MOD_ID)
public final class PayPalStoreForge {
    public PayPalStoreForge() {
        EventBuses.registerModEventBus(PayPalStore.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        PayPalStore.init();
    }
}
