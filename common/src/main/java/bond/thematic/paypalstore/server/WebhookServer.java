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

            // Send 200 OK immediately to prevent PayPal retries if our processing takes
            // time
            exchange.sendResponseHeaders(200, 0);
            OutputStream os = exchange.getResponseBody();
            os.close();

            // Verify with PayPal
            verifyIPN(body);
        }
    }

    private static void verifyIPN(String body) {
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
        String paymentStatus = data.get("payment_status");
        if (!"Completed".equalsIgnoreCase(paymentStatus))
            return;

        String custom = data.get("custom"); // Should contain UUID
        String txnId = data.get("txn_id");
        String gross = data.get("mc_gross");

        // Simple logic: If we have a UUID, find the player and match item by price
        // (imperfect but works for simple shops)
        // Better would be to use 'item_number' or 'item_name' if NCP passes it
        // correctly.

        if (custom != null && !custom.isEmpty() && minecraftServer != null) {
            try {
                UUID uuid = UUID.fromString(custom);

                // Run on main thread
                minecraftServer.execute(() -> {
                    StoreConfig.get().items.stream()
                            .filter(item -> String.format("%.2f", item.price).equals(gross)) // Basic match by price
                            .findFirst()
                            .ifPresent(item -> {
                                ServerPlayer player = minecraftServer.getPlayerList().getPlayer(uuid);
                                String userName = player != null ? player.getGameProfile().getName() : "OfflinePlayer";

                                // Execute console commands
                                for (String cmd : item.commands) {
                                    // For offline support we might need a different approach (e.g. LuckPerms
                                    // supports UUIDs)
                                    // Assuming LP for now: "lp user <uuid> ..."
                                    String cmdToRun = cmd.replace("%player%", userName);
                                    if (cmd.contains("lp user")) {
                                        // Heuristic to support UUID in LP commands if user configured it that way,
                                        // or we just rely on name if they are online.
                                        // Ideally, commands should support UUID: "lp user %uuid% ..."
                                        cmdToRun = cmdToRun.replace("%uuid%", uuid.toString());
                                    }

                                    minecraftServer.getCommands().performPrefixedCommand(
                                            minecraftServer.createCommandSourceStack(),
                                            cmdToRun);
                                }
                                System.out.println("Processed IPN for " + uuid + " (" + item.name + ")");
                            });
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
