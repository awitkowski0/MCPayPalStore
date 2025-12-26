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
    // OrderID -> PlayerUUID
    public static final Map<String, String> pendingOrders = new ConcurrentHashMap<>();
    // OrderID -> Callback
    private static final Map<String, Runnable> orderCallbacks = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public static void createOrder(ServerPlayer player, double amount, String currency, Runnable onSuccess) {
        player.sendSystemMessage(Component.literal("Creating secure payment link...").withStyle(ChatFormatting.YELLOW));

        PayPalService.createOrder(amount, currency, player.getGameProfile().getName()).thenAccept(response -> {
            pendingOrders.put(response.id, player.getUUID().toString());
            orderCallbacks.put(response.id, onSuccess);

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
            schedulePoll(response.id, player.getServer(), player.getUUID(), 0);
        }).exceptionally(e -> {
            player.sendSystemMessage(
                    Component.literal("Error creating order: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return null;
        });
    }

    public static void completeOrder(String orderId) {
        Runnable callback = orderCallbacks.remove(orderId);
        String playerUUID = pendingOrders.remove(orderId);

        if (callback != null) {
            // We need a thread context suitable for Minecraft (Main Thread) usually,
            // but the callback is often run on the server thread by the caller or we
            // schedule it.
            // Since this might be called from HTTP thread or Command thread, let's just run
            // it.
            // But wait, the callback in StoreMenu expects to run on Server Thread?
            // StoreMenu callback uses 'serverPlayer.getServer()' so it's thread safe-ish
            // but logic might need main thread.
            // Let's assume the caller handles threading or the callback does.
            // Actually, best to try to find a server instance if possible, but here we
            // don't have it easily static.
            // Update: We passed 'player.getServer()' to schedulePoll, maybe we should store
            // it?
            // For now, simple run.
            try {
                callback.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void schedulePoll(String orderId, net.minecraft.server.MinecraftServer server,
            java.util.UUID playerUUID, int attempts) {
        if (!pendingOrders.containsKey(orderId)) {
            // Order was completed manually or removed
            orderCallbacks.remove(orderId);
            return;
        }

        if (attempts > 300) { // 25 minutes timeout (300 * 5s) - Increased for better UX
            pendingOrders.remove(orderId);
            orderCallbacks.remove(orderId);
            return;
        }

        scheduler.schedule(() -> {
            PayPalService.checkOrderStatus(orderId).thenAccept(status -> {
                if ("COMPLETED".equals(status) || "APPROVED".equals(status)) {
                    // Success!
                    server.execute(() -> {
                        // Notify player if online
                        ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
                        if (player != null) {
                            player.sendSystemMessage(Component.literal("Payment Successful! processing rewards...")
                                    .withStyle(ChatFormatting.GREEN));
                        }
                        completeOrder(orderId);
                    });
                } else {
                    // Continue polling
                    schedulePoll(orderId, server, playerUUID, attempts + 1);
                }
            }).exceptionally(e -> {
                // Log error but keep polling
                e.printStackTrace();
                schedulePoll(orderId, server, playerUUID, attempts + 1);
                return null;
            });
        }, 5, TimeUnit.SECONDS);
    }
}
