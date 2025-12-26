package bond.thematic.paypalstore;

import bond.thematic.paypalstore.paypal.PayPalService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OrderManager {
    private static final Map<String, String> pendingOrders = new ConcurrentHashMap<>(); // OrderID -> PlayerUUID
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void createOrder(ServerPlayer player, double amount, String currency, Runnable onSuccess) {
        player.sendSystemMessage(Component.literal("Creating secure payment link...").withStyle(ChatFormatting.YELLOW));

        PayPalService.createOrder(amount, currency, player.getGameProfile().getName()).thenAccept(response -> {
            pendingOrders.put(response.id, player.getUUID().toString());

            Component link = Component.literal(" [CLICK TO PAY] ")
                    .setStyle(Style.EMPTY
                            .withColor(ChatFormatting.GREEN)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, response.approveLink)));

            player.sendSystemMessage(Component.literal("Order #").append(response.id).append(" created!")
                    .withStyle(ChatFormatting.AQUA).append(link));
            player.sendSystemMessage(
                    Component.literal("Waiting for payment... (Checks every 5s)").withStyle(ChatFormatting.GRAY));

            // Start polling for this specific order
            schedulePoll(response.id, player, onSuccess, 0);
        }).exceptionally(e -> {
            player.sendSystemMessage(
                    Component.literal("Error creating order: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return null;
        });
    }

    private static void schedulePoll(String orderId, ServerPlayer player, Runnable onSuccess, int attempts) {
        if (attempts > 60) { // 5 minutes timeout
            pendingOrders.remove(orderId);
            player.sendSystemMessage(
                    Component.literal("Order " + orderId + " timed out.").withStyle(ChatFormatting.RED));
            return;
        }

        scheduler.schedule(() -> {
            PayPalService.checkOrderStatus(orderId).thenAccept(status -> {
                if ("COMPLETED".equals(status) || "APPROVED".equals(status)) {
                    pendingOrders.remove(orderId);
                    player.getServer().execute(() -> {
                        player.sendSystemMessage(Component.literal("Payment Successful! processing rewards...")
                                .withStyle(ChatFormatting.GREEN));
                        onSuccess.run();
                    });
                } else {
                    // Continue polling
                    schedulePoll(orderId, player, onSuccess, attempts + 1);
                }
            }).exceptionally(e -> {
                // Log error but maybe keep polling?
                return null;
            });
        }, 5, TimeUnit.SECONDS);
    }
}
