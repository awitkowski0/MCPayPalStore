package bond.thematic.paypalstore.storage;

import bond.thematic.paypalstore.config.StoreConfig;
import bond.thematic.paypalstore.paypal.PayPalService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SubscriptionStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_PATH = Platform.getConfigFolder().resolve("subscription_data.json");
    // Map<PlayerUUID, List<Subscription>>
    private static Map<UUID, List<PlayerSubscription>> subscriptions = new ConcurrentHashMap<>();

    // Map<PlayerUUID, List<String>> pendingCommands
    private static Map<UUID, List<String>> pendingCommands = new ConcurrentHashMap<>();

    public static void load() {
        if (Files.exists(DATA_PATH)) {
            try (FileReader reader = new FileReader(DATA_PATH.toFile())) {
                StorageData data = GSON.fromJson(reader, StorageData.class);
                if (data != null) {
                    if (data.subscriptions != null)
                        subscriptions = new ConcurrentHashMap<>(data.subscriptions);
                    if (data.pendingCommands != null)
                        pendingCommands = new ConcurrentHashMap<>(data.pendingCommands);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(DATA_PATH.toFile())) {
            StorageData data = new StorageData();
            data.subscriptions = subscriptions;
            data.pendingCommands = pendingCommands;
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void addSubscription(UUID playerUUID, String username, StoreConfig.StoreItem item,
            String paypalSubscriptionId) {
        long now = System.currentTimeMillis();
        long expiry = calculateExpiry(now, item.interval, item.intervalCount);

        PlayerSubscription sub = new PlayerSubscription();
        sub.itemId = item.id;
        sub.startTime = now;
        sub.expiryTime = expiry;
        sub.paypalSubscriptionId = paypalSubscriptionId;
        sub.active = true;
        sub.username = username;

        subscriptions.computeIfAbsent(playerUUID, k -> new ArrayList<>()).add(sub);
        save();
    }

    private static long calculateExpiry(long start, String interval, int count) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(start);

        switch (interval.toUpperCase()) {
            case "DAY":
                cal.add(Calendar.DAY_OF_YEAR, count);
                break;
            case "WEEK":
                cal.add(Calendar.WEEK_OF_YEAR, count);
                break;
            case "MONTH":
                cal.add(Calendar.MONTH, count);
                break;
            case "YEAR":
                cal.add(Calendar.YEAR, count);
                break;
            default:
                cal.add(Calendar.MONTH, count); // Default
                break;
        }
        return cal.getTimeInMillis();
    }

    public static void processCommand(MinecraftServer server, UUID uuid, String command) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            // Online - execute immediately
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    command.replace("%player%", player.getGameProfile().getName()));
        } else {
            // Offline - queue it
            System.out.println("Player " + uuid + " is offline. Queuing command: " + command);
            pendingCommands.computeIfAbsent(uuid, k -> new ArrayList<>()).add(command);
            save(); // Save immediately so we don't lose it
        }
    }

    public static void executePendingCommands(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (pendingCommands.containsKey(uuid)) {
            List<String> commands = pendingCommands.remove(uuid);
            if (commands != null && !commands.isEmpty()) {
                System.out.println(
                        "Propagating " + commands.size() + " pending commands for " + player.getName().getString());
                for (String cmd : commands) {
                    player.getServer().getCommands().performPrefixedCommand(
                            player.getServer().createCommandSourceStack(),
                            cmd.replace("%player%", player.getGameProfile().getName()));
                }
                save();
            }
        }
    }

    public static void checkExpiry(MinecraftServer server) {
        long now = System.currentTimeMillis();
        boolean changed = false;

        for (Map.Entry<UUID, List<PlayerSubscription>> entry : subscriptions.entrySet()) {
            UUID uuid = entry.getKey();

            for (PlayerSubscription sub : entry.getValue()) {
                if (sub.active && now > sub.expiryTime) {
                    // Expired!
                    StoreConfig.StoreItem item = StoreConfig.get().items.stream()
                            .filter(i -> i.id.equals(sub.itemId)).findFirst().orElse(null);

                    if (item != null && !item.expiryCommands.isEmpty()) {
                        System.out.println("Subscription expired for " + uuid + " item " + sub.itemId);
                        for (String cmd : item.expiryCommands) {
                            processCommand(server, uuid, cmd);
                        }
                    }
                    sub.active = false;
                    changed = true;
                }
            }
        }

        if (changed) {
            save();
        }
    }

    // Call this when a recurring payment is received via Webhook (Deprecated but
    // kept for safety/logic reuse)
    // Actually we shouldn't use this if we removed Webhooks, but let's leave it as
    // is or update it?
    // The prompt asked to remove Webhooks, and we did. This method is likely unused
    // now.
    // But checkRenewals uses logic similar.

    public static void checkRenewals(MinecraftServer server) {
        long now = System.currentTimeMillis();

        for (Map.Entry<UUID, List<PlayerSubscription>> entry : subscriptions.entrySet()) {
            UUID uuid = entry.getKey();
            for (PlayerSubscription sub : entry.getValue()) {
                if (sub.active && sub.paypalSubscriptionId != null) {
                    PayPalService.getSubscriptionDetails(sub.paypalSubscriptionId).thenAccept(details -> {
                        server.execute(() -> {
                            // Sync Status
                            if ("CANCELLED".equalsIgnoreCase(details.status)
                                    || "SUSPENDED".equalsIgnoreCase(details.status)
                                    || "EXPIRED".equalsIgnoreCase(details.status)) {
                                if (sub.active) {
                                    System.out.println("Subscription " + sub.paypalSubscriptionId + " is now "
                                            + details.status + ". Marking inactive.");
                                    sub.active = false;
                                    save();
                                }
                                return;
                            }

                            // Check Renewal
                            if (details.lastPaymentTime != null
                                    && !details.lastPaymentTime.equals(sub.lastPaymentTime)) {
                                System.out.println("Detected new payment for subscription " + sub.paypalSubscriptionId);
                                sub.lastPaymentTime = details.lastPaymentTime;

                                // Grant Rewards
                                StoreConfig.StoreItem item = StoreConfig.get().items.stream()
                                        .filter(i -> i.id.equals(sub.itemId)).findFirst().orElse(null);

                                if (item != null) {
                                    if (!sub.active)
                                        sub.active = true;
                                    long base = Math.max(System.currentTimeMillis(), sub.expiryTime);
                                    sub.expiryTime = calculateExpiry(base, item.interval, item.intervalCount);

                                    for (String cmd : item.commands) {
                                        processCommand(server, uuid, cmd);
                                    }
                                }
                                save();
                            }
                        });
                    }).exceptionally(e -> {
                        System.err.println(
                                "Failed to poll subscription " + sub.paypalSubscriptionId + ": " + e.getMessage());
                        return null;
                    });
                }
            }
        }
    }

    private static class StorageData {
        public Map<UUID, List<PlayerSubscription>> subscriptions = new HashMap<>();
        public Map<UUID, List<String>> pendingCommands = new HashMap<>();
    }

    public static class PlayerSubscription {
        public String itemId;
        public long startTime;
        public long expiryTime;
        public String paypalSubscriptionId;
        public boolean active;
        public String lastPaymentTime;
        public String username;
    }
}
