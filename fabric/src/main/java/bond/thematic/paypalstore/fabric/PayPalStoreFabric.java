package bond.thematic.paypalstore.fabric;

import bond.thematic.paypalstore.PayPalStore;
import net.fabricmc.api.ModInitializer;

public final class PayPalStoreFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        PayPalStore.init();
    }
}
