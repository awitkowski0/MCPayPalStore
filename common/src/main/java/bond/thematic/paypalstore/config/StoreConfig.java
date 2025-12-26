package bond.thematic.paypalstore.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StoreConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Platform.getConfigFolder().resolve("paypal_store.json");
    private static ConfigData instance;

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
                instance = GSON.fromJson(reader, ConfigData.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (instance == null) {
            instance = new ConfigData();
        }

        // Save to ensure any new fields are written to disk
        save();
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static ConfigData get() {
        return instance;
    }

    public static class ConfigData {
        public String clientId = "YOUR_CLIENT_ID";
        public String clientSecret = "YOUR_CLIENT_SECRET";
        public boolean sandbox = true;
        public boolean debug = false;
        public int webhookPort = 8080;
        public boolean useWebhooks = false;
        public List<StoreItem> items = new ArrayList<>();

        public ConfigData() {
            // Default item for example
            items.add(new StoreItem("vip_rank", "VIP Rank", 10.00, "USD", List.of("lp user %player% parent add vip"),
                    "vip_kit_id", "https://www.paypal.com/ncp/payment/QH8LMTX7SXKNJ"));
        }
    }

    public static class StoreItem {
        public String id;
        public String name;
        public double price;
        public String currency;
        public List<String> commands;
        public String kit;
        public String paymentUrl;

        public StoreItem(String id, String name, double price, String currency, List<String> commands, String kit,
                String paymentUrl) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.currency = currency;
            this.commands = commands;
            this.kit = kit;
            this.paymentUrl = paymentUrl;
        }
    }
}
