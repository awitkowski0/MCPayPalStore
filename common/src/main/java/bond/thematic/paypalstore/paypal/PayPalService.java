package bond.thematic.paypalstore.paypal;

import bond.thematic.paypalstore.config.StoreConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

public class PayPalService {
    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();
    private static String accessToken = null;
    private static long tokenExpiry = 0;

    private static String getBaseUrl() {
        return StoreConfig.get().sandbox ? "https://api-m.sandbox.paypal.com" : "https://api-m.paypal.com";
    }

    private static synchronized CompletableFuture<String> getAccessToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpiry) {
            return CompletableFuture.completedFuture(accessToken);
        }

        String auth = StoreConfig.get().clientId + ":" + StoreConfig.get().clientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/v1/oauth2/token"))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(body -> {
                    JsonObject json = gson.fromJson(body, JsonObject.class);
                    if (json.has("access_token")) {
                        accessToken = json.get("access_token").getAsString();
                        tokenExpiry = System.currentTimeMillis() + (json.get("expires_in").getAsLong() * 1000) - 10000;
                        return accessToken;
                    } else {
                        throw new RuntimeException("Failed to get access token: " + body);
                    }
                });
    }

    public static CompletableFuture<OrderResponse> createOrder(double amount, String currency) {
        return getAccessToken().thenCompose(token -> {
            JsonObject orderRequest = new JsonObject();
            orderRequest.addProperty("intent", "CAPTURE");

            JsonObject purchaseUnit = new JsonObject();
            JsonObject amountJson = new JsonObject();
            amountJson.addProperty("currency_code", currency);
            amountJson.addProperty("value", String.format("%.2f", amount));
            purchaseUnit.add("amount", amountJson);

            com.google.gson.JsonArray purchaseUnits = new com.google.gson.JsonArray();
            purchaseUnits.add(purchaseUnit);
            orderRequest.add("purchase_units", purchaseUnits);

            // Allow the user to pay without logging in (guest checkout) if possible, though
            // 'landing_page' is in application_context
            JsonObject applicationContext = new JsonObject();
            applicationContext.addProperty("landing_page", "BILLING"); // Prefer non-login checkout
            applicationContext.addProperty("user_action", "PAY_NOW");
            applicationContext.addProperty("return_url", "https://example.com/return"); // Placeholder, we poll
            applicationContext.addProperty("cancel_url", "https://example.com/cancel");
            orderRequest.add("application_context", applicationContext);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/v2/checkout/orders"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(orderRequest)))
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        String id = json.get("id").getAsString();
                        String approveLink = null;
                        for (com.google.gson.JsonElement link : json.getAsJsonArray("links")) {
                            if (link.getAsJsonObject().get("rel").getAsString().equals("approve")) {
                                approveLink = link.getAsJsonObject().get("href").getAsString();
                                break;
                            }
                        }
                        return new OrderResponse(id, approveLink);
                    });
        });
    }

    public static CompletableFuture<String> checkOrderStatus(String orderId) {
        return getAccessToken().thenCompose(token -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/v2/checkout/orders/" + orderId))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        return json.get("status").getAsString();
                    });
        });
    }

    public static class OrderResponse {
        public String id;
        public String approveLink;

        public OrderResponse(String id, String approveLink) {
            this.id = id;
            this.approveLink = approveLink;
        }
    }
}
