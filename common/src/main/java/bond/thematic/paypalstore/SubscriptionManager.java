package bond.thematic.paypalstore;

import bond.thematic.paypalstore.config.StoreConfig;
import bond.thematic.paypalstore.paypal.PayPalService;
import bond.thematic.paypalstore.storage.SubscriptionStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

public class SubscriptionManager {

    // SubscriptionID -> PlayerUUID
    private static final java.util.Map<String, String> pendingSubscriptions = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors
            .newSingleThreadScheduledExecutor();

    public static void initiateSubscription(ServerPlayer player, StoreConfig.StoreItem item) {
        player.sendSystemMessage(
                Component.literal("Processing subscription details...").withStyle(ChatFormatting.GRAY));

        ensureProduct(item)
                .thenCompose(productId -> ensurePlan(item, productId))
                .thenCompose(planId -> PayPalService.createSubscription(item, planId, player.getStringUUID()))
                .thenAccept(response -> {
                    String url = response.approveLink;
                    if (url == null) {
                        player.sendSystemMessage(Component.literal("Error: No approval link returned from PayPal.")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }
                    Component link = Component.literal(" [CLICK TO SUBSCRIBE] ")
                            .setStyle(Style.EMPTY
                                    .withColor(ChatFormatting.GOLD)
                                    .withBold(true)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url)));

                    player.sendSystemMessage(Component.literal("Subscription created! Click to approve:")
                            .withStyle(ChatFormatting.GREEN).append(link));
                    player.sendSystemMessage(
                            Component.literal("After approving, you may need to wait a moment for activation.")
                                    .withStyle(ChatFormatting.GRAY));

                    // Start Polling
                    pendingSubscriptions.put(response.id, player.getStringUUID());
                    schedulePoll(response.id, player.getServer(), player.getUUID(), 0);

                }).exceptionally(e -> {
                    player.sendSystemMessage(Component.literal("Error creating subscription: " + e.getMessage())
                            .withStyle(ChatFormatting.RED));
                    e.printStackTrace();
                    return null;
                });
    }

    private static void schedulePoll(String subscriptionId, MinecraftServer server,
            java.util.UUID playerUUID, int attempts) {
        if (!pendingSubscriptions.containsKey(subscriptionId)) {
            return;
        }

        if (attempts > 300) { // 25 minutes timeout
            pendingSubscriptions.remove(subscriptionId);
            return;
        }

        scheduler.schedule(() -> {
            PayPalService.getSubscriptionDetails(subscriptionId).thenAccept(details -> {
                if ("ACTIVE".equalsIgnoreCase(details.status)) {
                    server.execute(() -> {
                        String customId = pendingSubscriptions.remove(subscriptionId);
                        if (customId != null) {
                            onSubscriptionActivated(server, subscriptionId, customId, details.planId);

                            // Notify player
                            ServerPlayer player = server.getPlayerList().getPlayer(playerUUID);
                            if (player != null) {
                                player.sendSystemMessage(
                                        Component.literal("Subscription Activated!").withStyle(ChatFormatting.GREEN));
                            }
                        }
                    });
                } else {
                    // Continue polling
                    schedulePoll(subscriptionId, server, playerUUID, attempts + 1);
                }
            }).exceptionally(e -> {
                // Keep polling on error
                schedulePoll(subscriptionId, server, playerUUID, attempts + 1);
                return null;
            });
        }, 5, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static CompletableFuture<String> ensureProduct(StoreConfig.StoreItem item) {
        if (item.cachedProductId != null && !item.cachedProductId.isEmpty()) {
            return CompletableFuture.completedFuture(item.cachedProductId);
        }
        return PayPalService.createProduct(item).thenApply(id -> {
            item.cachedProductId = id;
            StoreConfig.save(); // Cache it
            return id;
        });
    }

    private static CompletableFuture<String> ensurePlan(StoreConfig.StoreItem item, String productId) {
        if (item.cachedPlanId != null && !item.cachedPlanId.isEmpty()) {
            return CompletableFuture.completedFuture(item.cachedPlanId);
        }
        return PayPalService.createPlan(item, productId).thenApply(id -> {
            item.cachedPlanId = id;
            StoreConfig.save(); // Cache it
            return id;
        });
    }

    public static void onSubscriptionActivated(MinecraftServer server, String subscriptionId, String customId,
            String planId) {
        // Find item by planId
        StoreConfig.StoreItem item = StoreConfig.get().items.stream()
                .filter(i -> planId.equals(i.cachedPlanId))
                .findFirst()
                .orElse(null);

        if (item == null) {
            System.err.println(
                    "Activated subscription " + subscriptionId + " but could not find local item for plan " + planId);
            return;
        }

        try {
            java.util.UUID uuid = java.util.UUID.fromString(customId);

            // Try to resolve username
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            String name = (player != null) ? player.getGameProfile().getName() : uuid.toString();
            if (player == null) {
                // Try cache if offline
                name = server.getProfileCache().get(uuid).map(p -> p.getName()).orElse(uuid.toString());
            }

            SubscriptionStorage.addSubscription(uuid, name, item, subscriptionId);
            System.out.println("Activating subscription for " + uuid + " (" + name + ") item " + item.id);

            // Execute commands (will queue if offline)
            for (String cmd : item.commands) {
                SubscriptionStorage.processCommand(server, uuid, cmd);
            }

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid UUID in customId for subscription: " + customId);
        }
    }
}
