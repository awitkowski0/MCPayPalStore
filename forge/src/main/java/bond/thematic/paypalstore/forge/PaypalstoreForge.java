package bond.thematic.paypalstore.forge;

import bond.thematic.paypalstore.PayPalStore;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(PayPalStore.MOD_ID)
public final class PaypalstoreForge {
    public PaypalstoreForge() {
        // Submit our event bus to let Architectury API register our content on the
        // right time.
        EventBuses.registerModEventBus(PayPalStore.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        PayPalStore.init();
    }
}
