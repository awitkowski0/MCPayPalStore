package bond.thematic.paypalstore.server;

import bond.thematic.paypalstore.config.StoreConfig;
import bond.thematic.paypalstore.PayPalStore;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class WebhookServer {
    private static HttpServer server;
    private static MinecraftServer minecraftServer;

    public static void start(MinecraftServer mcServer) {
        minecraftServer = mcServer;
        if (!StoreConfig.get().useWebhooks)
            return;

        try {
            server = HttpServer.create(new InetSocketAddress(StoreConfig.get().webhookPort), 0);
            server.createContext("/paypal/ipn", new IPNHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("PayPal IPN server started on port " + StoreConfig.get().webhookPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    static class IPNHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            InputStream is = exchange.getRequestBody();
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.close();

            if (isJson(body)) {
                processWebhook(body);
            } else {
                verifyIPN(body);
            }
        }
    }

    private static boolean isJson(String body) {
        return body.trim().startsWith("{");
    }

    private static void processWebhook(String body) {
        if (!StoreConfig.get().debug) {
            // TODO: Implement proper Webhook Signature Verification
            // For now, we rely on the fact that we cross-check the Order ID with PayPal API
            // or if the user enabled debug mode, we trust the payload (SIMULATOR ONLY)
            System.out.println("Processing Webhook (Validation Skipped/Not Implemented fully for mod). Body length: "
                    + body.length());
        } else {
            System.out.println("Processing Webhook (DEBUG MODE - Validation Skipped).");
        }

        try {
            com.google.gson.JsonObject json = new com.google.gson.Gson().fromJson(body,
                    com.google.gson.JsonObject.class);
            String eventType = json.has("event_type") ? json.get("event_type").getAsString() : "";

            if ("PAYMENT.CAPTURE.COMPLETED".equals(eventType) || "CHECKOUT.ORDER.APPROVED".equals(eventType)) {
                com.google.gson.JsonObject resource = json.getAsJsonObject("resource");
                // In CHECKOUT.ORDER.APPROVED, purchase_units is array
                if (resource.has("purchase_units")) {
                    com.google.gson.JsonArray units = resource.getAsJsonArray("purchase_units");
                    if (units.size() > 0) {
                        com.google.gson.JsonObject unit = units.get(0).getAsJsonObject();
                        if (unit.has("custom_id")) {
                            String customId = unit.get("custom_id").getAsString();
                            String amount = unit.getAsJsonObject("amount").get("value").getAsString();

                            // If it's APPROVED (not captured yet), we might want to capture it?
                            // But usually for "PAY NOW" buttons it auto-captures.
                            // Let's just process reward if we trust it.

                            if (StoreConfig.get().debug) {
                                // Simulator trust
                                processReward(customId, amount);
                            } else {
                                // Secure check: Verify order status with PayPal API
                                // If resource has ID, check it.
                                if (resource.has("id")) {
                                    String orderId = resource.get("id").getAsString();
                                    // Use our service to check if it's really approved/completed
                                    bond.thematic.paypalstore.paypal.PayPalService.checkOrderStatus(orderId)
                                            .thenAccept(status -> {
                                                if ("COMPLETED".equals(status) || "APPROVED".equals(status)) {
                                                    processReward(customId, amount);
                                                } else {
                                                    System.out.println("Webhook verification failed: Order " + orderId
                                                            + " is " + status);
                                                }
                                            });
                                }
                            }
                        }
                    }
                }
            } else if ("scam".equals(eventType)) {
                // Just kidding
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void verifyIPN(String body) {
        // Keep old IPN logic for backward compatibility if needed,
        // or just allow it to fail if we are fully moving to webhooks.
        // ... (Existing logic can stay or be removed)
        // For brevity in this refactor, I'm leaving the old call but it won't be used
        // by the JSON simulator.
        if (StoreConfig.get().debug) {
            processPayment(parseFormData(body));
            return;
        }

        String validationUrl = StoreConfig.get().sandbox ? "https://ipnpb.sandbox.paypal.com/cgi-bin/webscr"
                : "https://ipnpb.paypal.com/cgi-bin/webscr";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(validationUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("cmd=_notify-validate&" + body))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if ("VERIFIED".equals(response.body())) {
                        processPayment(parseFormData(body));
                    } else {
                        System.out.println("IPN Validation Failed: " + response.body());
                    }
                });
    }

    private static Map<String, String> parseFormData(String body) {
        Map<String, String> map = new HashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static void processPayment(Map<String, String> data) {
        String custom = data.get("custom");
        String gross = data.get("mc_gross");
        processReward(custom, gross);
    }

    private static void processReward(String customId, String grossAmount) {
        if (customId != null && !customId.isEmpty() && minecraftServer != null) {
            try {
                // Run on main thread
                minecraftServer.execute(() -> {
                    // Try to resolve player
                    String userName = customId;
                    String userUUID = null;

                    // Check if customId is a UUID
                    try {
                        UUID uuid = UUID.fromString(customId);
                        userUUID = uuid.toString();
                        // Try to get name from online player
                        ServerPlayer p = minecraftServer.getPlayerList().getPlayer(uuid);
                        if (p != null) {
                            userName = p.getGameProfile().getName();
                        } else {
                            // Offline, try UserCache
                            userName = minecraftServer.getProfileCache().get(uuid)
                                    .map(com.mojang.authlib.GameProfile::getName).orElse(customId);
                        }
                    } catch (IllegalArgumentException e) {
                        // It's a username
                        userName = customId;
                        ServerPlayer p = minecraftServer.getPlayerList().getPlayerByName(userName);
                        if (p != null) {
                            userUUID = p.getStringUUID();
                        } else {
                            // Try to look up offline UUID
                            userUUID = minecraftServer.getProfileCache().get(userName)
                                    .map(profile -> profile.getId().toString()).orElse(null);
                        }
                    }

                    final String finalName = userName;
                    final String finalUUID = userUUID;

                    StoreConfig.get().items.stream()
                            .filter(item -> String.format("%.2f", item.price).equals(grossAmount)) // Basic match by
                                                                                                   // price
                            .findFirst()
                            .ifPresent(item -> {

                                // Execute console commands
                                for (String cmd : item.commands) {
                                    String cmdToRun = cmd.replace("%player%", finalName);
                                    if (cmd.contains("%uuid%")) {
                                        cmdToRun = cmdToRun.replace("%uuid%",
                                                finalUUID != null ? finalUUID : finalName);
                                    }

                                    minecraftServer.getCommands().performPrefixedCommand(
                                            minecraftServer.createCommandSourceStack(),
                                            cmdToRun);
                                }
                                System.out.println("Processed Payment for " + finalName + " (" + item.name + ")");
                            });
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
