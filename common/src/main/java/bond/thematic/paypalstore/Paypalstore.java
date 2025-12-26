package bond.thematic.paypalstore;

import bond.thematic.paypalstore.config.StoreConfig;
import bond.thematic.paypalstore.commands.StoreCommands;
import bond.thematic.paypalstore.server.WebhookServer;
import com.google.common.base.Suppliers;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.event.events.common.LifecycleEvent;

import java.util.function.Supplier;

public class PayPalStore {
    public static final String MOD_ID = "paypalstore";
    public static final Supplier<RegistrarManager> REGISTRIES = Suppliers.memoize(() -> RegistrarManager.get(MOD_ID));

    public static void init() {
        StoreConfig.load();
        StoreCommands.register();

        LifecycleEvent.SERVER_STARTED.register(WebhookServer::start);
        LifecycleEvent.SERVER_STOPPING.register(server -> WebhookServer.stop());
    }
}
