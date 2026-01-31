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
            String desc = item.name.replaceAll("&[0-9a-fk-or]", "").replaceAll("§[0-9a-fk-or]", "");
            if (item.description != null && !item.description.isEmpty()) {
                String details = String.join(" ", item.description).replaceAll("&[0-9a-fk-or]", "")
                        .replaceAll("§[0-9a-fk-or]", "");
                desc += " - " + details;
            }
            if (desc.length() > 127) {
                desc = desc.substring(0, 124) + "...";
            }
            purchaseUnit.addProperty("description", desc);

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

            JsonObject paymentSource = new JsonObject();
            JsonObject paypal = new JsonObject();
            JsonObject experienceContext = new JsonObject();

            experienceContext.addProperty("brand_name", StoreConfig.get().brandName);
            experienceContext.addProperty("shipping_preference", "NO_SHIPPING");
            experienceContext.addProperty("user_action", "PAY_NOW");

            // Try to force immediate payment to potentially suppress Pay Later
            experienceContext.addProperty("payment_method_preference", "IMMEDIATE_PAYMENT_REQUIRED");

            // Set landing page
            experienceContext.addProperty("landing_page", StoreConfig.get().landingPage);

            // Valid URLs are required
            experienceContext.addProperty("return_url", "https://www.paypal.com");
            experienceContext.addProperty("cancel_url", "https://www.paypal.com");

            paypal.add("experience_context", experienceContext);
            paymentSource.add("paypal", paypal);
            orderRequest.add("payment_source", paymentSource);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/v2/checkout/orders"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("PayPal-Request-Id", java.util.UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(orderRequest)))
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        System.out.println("DEBUG: PayPal Create Order Response: " + body);
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        String id = json.get("id").getAsString();
                        String approveLink = null;
                        if (json.has("links")) {
                            for (com.google.gson.JsonElement link : json.getAsJsonArray("links")) {
                                String rel = link.getAsJsonObject().get("rel").getAsString();
                                if (rel.equals("approve") || rel.equals("payer-action")) {
                                    approveLink = link.getAsJsonObject().get("href").getAsString();
                                    break;
                                }
                            }
                        }
                        return new OrderResponse(id, approveLink);
                    });
        });
    }

    // ============================================
    // SUBSCRIPTION SUPPORT (Billing Plans API)
    // ============================================

    public static CompletableFuture<String> createProduct(StoreConfig.StoreItem item) {
        return getAccessToken().thenCompose(token -> {
            JsonObject productRequest = new JsonObject();
            productRequest.addProperty("name", item.name.replaceAll("&[0-9a-fk-or]", ""));
            productRequest.addProperty("description", "Subscription for " + item.name);
            productRequest.addProperty("type", "DIGITAL");
            productRequest.addProperty("category", "SOFTWARE");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/v1/catalogs/products"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("PayPal-Request-Id", java.util.UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(productRequest)))
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        if (json.has("id")) {
                            return json.get("id").getAsString();
                        }
                        throw new RuntimeException("Failed to create product: " + body);
                    });
        });
    }

    public static CompletableFuture<String> createPlan(StoreConfig.StoreItem item, String productId) {
        return getAccessToken().thenCompose(token -> {
            JsonObject planRequest = new JsonObject();
            planRequest.addProperty("product_id", productId);
            planRequest.addProperty("name", item.name.replaceAll("&[0-9a-fk-or]", "") + " Plan");
            planRequest.addProperty("description", "Recurring subscription for " + item.name);
            planRequest.addProperty("status", "ACTIVE");

            JsonObject billingCycle = new JsonObject();
            JsonObject frequency = new JsonObject();
            frequency.addProperty("interval_unit", item.interval); // DAY, WEEK, MONTH, YEAR
            frequency.addProperty("interval_count", item.intervalCount); // e.g. 1
            billingCycle.add("frequency", frequency);

            JsonObject tenureType = new JsonObject();
            tenureType.addProperty("tenure_type", "REGULAR"); // Standard recurring
            billingCycle.addProperty("tenure_type", "REGULAR");
            billingCycle.addProperty("sequence", 1);
            billingCycle.addProperty("total_cycles", 0); // 0 = Infinite

            JsonObject pricingScheme = new JsonObject();
            JsonObject fixedPrice = new JsonObject();
            fixedPrice.addProperty("value", String.format("%.2f", item.price));
            fixedPrice.addProperty("currency_code", item.currency);
            pricingScheme.add("fixed_price", fixedPrice);
            billingCycle.add("pricing_scheme", pricingScheme);

            com.google.gson.JsonArray cycles = new com.google.gson.JsonArray();
            cycles.add(billingCycle);
            planRequest.add("billing_cycles", cycles);

            JsonObject paymentPreferences = new JsonObject();
            paymentPreferences.addProperty("auto_bill_outstanding", true);
            paymentPreferences.addProperty("setup_fee_failure_action", "CANCEL");
            paymentPreferences.addProperty("payment_failure_threshold", 3);
            planRequest.add("payment_preferences", paymentPreferences);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/v1/billing/plans"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("PayPal-Request-Id", java.util.UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(planRequest)))
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        if (json.has("id")) {
                            return json.get("id").getAsString();
                        }
                        throw new RuntimeException("Failed to create plan: " + body);
                    });
        });
    }

    public static CompletableFuture<OrderResponse> createSubscription(StoreConfig.StoreItem item, String planId,
            String customId) {
        return getAccessToken().thenCompose(token -> {
            JsonObject subRequest = new JsonObject();
            subRequest.addProperty("plan_id", planId);
            subRequest.addProperty("custom_id", customId);

            JsonObject applicationContext = new JsonObject();
            applicationContext.addProperty("brand_name", StoreConfig.get().brandName);
            applicationContext.addProperty("shipping_preference", "NO_SHIPPING");
            applicationContext.addProperty("user_action", "SUBSCRIBE_NOW");
            applicationContext.addProperty("return_url", "https://example.com/return"); // Generic
            applicationContext.addProperty("cancel_url", "https://example.com/cancel");
            subRequest.add("application_context", applicationContext);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/v1/billing/subscriptions"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("PayPal-Request-Id", java.util.UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(subRequest)))
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        String id = json.get("id").getAsString();
                        String approveLink = null;
                        if (json.has("links")) {
                            for (com.google.gson.JsonElement link : json.getAsJsonArray("links")) {
                                String rel = link.getAsJsonObject().get("rel").getAsString();
                                if (rel.equals("approve")) {
                                    approveLink = link.getAsJsonObject().get("href").getAsString();
                                    break;
                                }
                            }
                        }
                        return new OrderResponse(id, approveLink);
                    });
        });
    }

    public static CompletableFuture<String> captureOrder(String orderId) {
        return getAccessToken().thenCompose(token -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/v2/checkout/orders/" + orderId + "/capture"))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("PayPal-Request-Id", java.util.UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        return json.get("status").getAsString();
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

    public static CompletableFuture<SubscriptionDetails> getSubscriptionDetails(String subscriptionId) {
        return getAccessToken().thenCompose(token -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/v1/billing/subscriptions/" + subscriptionId))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(body -> {
                        JsonObject json = gson.fromJson(body, JsonObject.class);
                        String status = json.get("status").getAsString();
                        String lastPaymentTime = null;

                        if (json.has("billing_info")) {
                            JsonObject billingInfo = json.getAsJsonObject("billing_info");
                            if (billingInfo.has("last_payment")) {
                                JsonObject lastPayment = billingInfo.getAsJsonObject("last_payment");
                                if (lastPayment.has("time")) {
                                    lastPaymentTime = lastPayment.get("time").getAsString();
                                }
                            }
                        }
                        String planId = null;
                        if (json.has("plan_id")) {
                            planId = json.get("plan_id").getAsString();
                        }
                        return new SubscriptionDetails(status, lastPaymentTime, planId);

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

    public static class SubscriptionDetails {
        public String status; // ACTIVE, CANCELLED, SUSPENDED, EXPIRED
        public String lastPaymentTime; // ISO 8601 string
        public String planId;

        public SubscriptionDetails(String status, String lastPaymentTime, String planId) {
            this.status = status;
            this.lastPaymentTime = lastPaymentTime;
            this.planId = planId;
        }
    }
}
