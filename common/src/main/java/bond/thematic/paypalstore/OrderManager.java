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

    public static void createOrder(ServerPlayer player, bond.thematic.paypalstore.config.StoreConfig.StoreItem item,
            Runnable onSuccess) {
        player.sendSystemMessage(Component.literal("Creating secure payment link...").withStyle(ChatFormatting.YELLOW));

        PayPalService.createOrder(item, player.getGameProfile().getName()).thenAccept(response -> {
            pendingOrders.put(response.id, player.getUUID().toString());
            orderCallbacks.put(response.id, onSuccess);

            bond.thematic.paypalstore.api.StoreEvents.ORDER_CREATED.invoker().onCreate(player, response.id, item.price,
                    item.currency);

            String orderMsg = bond.thematic.paypalstore.config.StoreConfig.get().messages.orderCreated
                    .replace("%id%", response.id)
                    .replace("&", "§");

            net.minecraft.network.chat.MutableComponent messageComp = Component.literal("");

            // Robust parsing: find [CLICK TO PAY]
            String clickText = "[CLICK TO PAY]";
            int clickIndex = orderMsg.indexOf(clickText);

            if (clickIndex != -1) {
                if (response.approveLink != null) {
                    // Pre-part
                    if (clickIndex > 0) {
                        messageComp.append(Component.literal(orderMsg.substring(0, clickIndex)));
                    }

                    // Clickable part
                    ClickEvent.Action action = ClickEvent.Action.OPEN_URL;
                    Component link = Component.literal(clickText)
                            .setStyle(Style.EMPTY
                                    .withColor(ChatFormatting.GREEN)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent(action, response.approveLink))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Click to pay on PayPal"))));
                    messageComp.append(link);

                    // Post-part
                    if (clickIndex + clickText.length() < orderMsg.length()) {
                        messageComp.append(Component.literal(orderMsg.substring(clickIndex + clickText.length())));
                    }
                } else {
                    // LINK IS NULL - Notify op/console and user
                    Component errorComp = Component.literal(orderMsg)
                            .append(Component.literal(" [ERROR: No Link]").withStyle(ChatFormatting.RED));
                    messageComp.append(errorComp);
                }
            } else {
                // Fallback: Just send the message, and append a link if we have one
                messageComp.append(Component.literal(orderMsg));

                if (response.approveLink != null) {
                    messageComp.append(Component.literal(" ")
                            .append(Component.literal("[OPEN]")
                                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)
                                            .withBold(true)
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
                                                    response.approveLink)))));
                }
            }

            player.sendSystemMessage(messageComp);
            player.sendSystemMessage(
                    Component.literal(
                            bond.thematic.paypalstore.config.StoreConfig.get().messages.pollingWait.replace("&", "§")));

            // Start polling for this specific order
            schedulePoll(response.id, player.getServer(), player.getUUID(), 0);
        }).exceptionally(e -> {
            String errorMsg = bond.thematic.paypalstore.config.StoreConfig.get().messages.paymentFailed
                    .replace("%error%", e.getMessage())
                    .replace("&", "§");
            player.sendSystemMessage(Component.literal(errorMsg));
            return null;
        });
    }

    public static void completeOrder(String orderId) {
        Runnable callback = orderCallbacks.remove(orderId);
        pendingOrders.remove(orderId);

        if (callback != null) {
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
                            String successMsg = bond.thematic.paypalstore.config.StoreConfig
                                    .get().messages.paymentSuccess.replace("&", "§");
                            player.sendSystemMessage(Component.literal(successMsg));
                            bond.thematic.paypalstore.api.StoreEvents.PAYMENT_COMPLETED.invoker().onComplete(player,
                                    orderId);
                        } else {
                            // Player offline, still fire event with null player?
                            // API says nullable player.
                            bond.thematic.paypalstore.api.StoreEvents.PAYMENT_COMPLETED.invoker().onComplete(null,
                                    orderId);
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
