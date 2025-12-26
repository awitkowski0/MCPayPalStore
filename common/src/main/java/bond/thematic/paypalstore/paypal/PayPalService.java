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

    public static CompletableFuture<OrderResponse> createOrder(
            bond.thematic.paypalstore.config.StoreConfig.StoreItem item, String customId) {
        return getAccessToken().thenCompose(token -> {
            JsonObject orderRequest = new JsonObject();
            orderRequest.addProperty("intent", "CAPTURE");

            JsonObject purchaseUnit = new JsonObject();
            JsonObject amountJson = new JsonObject();
            amountJson.addProperty("currency_code", item.currency);
            amountJson.addProperty("value", String.format("%.2f", item.price));
            purchaseUnit.add("amount", amountJson);
            purchaseUnit.addProperty("custom_id", customId);

            // Description (Strip colors)
            String desc = "";
            if (item.description != null && !item.description.isEmpty()) {
                desc = String.join(" ", item.description).replaceAll("&[0-9a-fk-or]", "")
                        .replaceAll("§[0-9a-fk-or]", "");
                if (desc.length() > 127)
                    desc = desc.substring(0, 124) + "...";
                purchaseUnit.addProperty("description", desc);
            }

            // Add Item Details for Checkout UI
            com.google.gson.JsonArray itemsArray = new com.google.gson.JsonArray();
            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("name", item.name.replaceAll("&[0-9a-fk-or]", "").replaceAll("§[0-9a-fk-or]", ""));
            if (!desc.isEmpty()) {
                itemObj.addProperty("description", desc);
            }
            itemObj.addProperty("quantity", "1");

            JsonObject unitAmount = new JsonObject();
            unitAmount.addProperty("currency_code", item.currency);
            unitAmount.addProperty("value", String.format("%.2f", item.price));
            itemObj.add("unit_amount", unitAmount);

            itemsArray.add(itemObj);
            purchaseUnit.add("items", itemsArray);

            // Add breakdown to match total
            JsonObject breakdown = new JsonObject();
            breakdown.add("item_total", unitAmount);
            amountJson.add("breakdown", breakdown);

            // Soft Descriptor
            if (StoreConfig.get().softDescriptor != null && !StoreConfig.get().softDescriptor.isEmpty()) {
                purchaseUnit.addProperty("soft_descriptor", StoreConfig.get().softDescriptor);
            }

            com.google.gson.JsonArray purchaseUnits = new com.google.gson.JsonArray();
            purchaseUnits.add(purchaseUnit);
            orderRequest.add("purchase_units", purchaseUnits);

            JsonObject applicationContext = new JsonObject();
            // Global Configs
            applicationContext.addProperty("brand_name", StoreConfig.get().brandName);
            applicationContext.addProperty("landing_page", StoreConfig.get().landingPage);
            applicationContext.addProperty("shipping_preference", "NO_SHIPPING"); // Forced

            applicationContext.addProperty("user_action", "PAY_NOW");
            applicationContext.addProperty("return_url", "https://example.com/return"); // Placeholder
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
