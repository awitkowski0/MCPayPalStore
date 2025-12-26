package bond.thematic.paypalstore.api;

import dev.architectury.event.Event;
import dev.architectury.event.EventFactory;
import net.minecraft.server.level.ServerPlayer;

public class StoreEvents {
    // Fired when an order is created (link generated)
    // Callback: (player, orderId, amount, currency) -> void
    public static final Event<OrderCreated> ORDER_CREATED = EventFactory.createLoop();

    // Fired when a payment is completed/verified
    // Callback: (player (nullable), orderId) -> void
    public static final Event<PaymentCompleted> PAYMENT_COMPLETED = EventFactory.createLoop();

    public interface OrderCreated {
        void onCreate(ServerPlayer player, String orderId, double amount, String currency);
    }

    public interface PaymentCompleted {
        void onComplete(ServerPlayer player, String orderId);
    }
}
