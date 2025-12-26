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
        // Global Customization
        public String brandName = "Thematic Store";
        public String softDescriptor = "PAYPAL *THEMATIC";
        public String landingPage = "BILLING"; // BILLING or LOGIN

        public List<StoreItem> items = new ArrayList<>();
        public Messages messages = new Messages();

        public ConfigData() {
            // Default item for example
            List<String> desc = new ArrayList<>();
            desc.add("&7Unlock the &6VIP &7rank.");
            desc.add("&7Includes:");
            desc.add("&e- &fFlight");
            desc.add("&e- &fKit");

            // Default icon: Nether Star
            items.add(new StoreItem("vip_rank", "VIP Rank", 10.00, "USD", List.of("lp user %player% parent add vip"),
                    "vip_kit_id", "", desc, null, new ArrayList<>(), "", "minecraft:nether_star", 0));
        }
    }

    public static class Messages {
        public String orderCreated = "&bOrder #%id% created! &a[CLICK TO PAY]";
        public String pollingWait = "&7Waiting for payment... (Checks every 5s)";
        public String paymentSuccess = "&aPayment Successful! Processing rewards...";
        public String paymentFailed = "&cError processing payment: %error%";
    }

    public static class StoreItem {
        public String id;
        public String name;
        public double price;
        public String currency;
        public List<String> commands;
        public String kit;
        public String paymentUrl;
        // New Fields
        public List<String> description = new ArrayList<>();
        public String expiry; // e.g. "30 Days"
        public List<String> previewItems = new ArrayList<>(); // e.g. "minecraft:diamond"
        public String requiredPermission;
        public String itemIcon; // e.g. "minecraft:emerald"
        public int customModelData;

        public StoreItem(String id, String name, double price, String currency, List<String> commands, String kit,
                String paymentUrl, List<String> description, String expiry, List<String> previewItems,
                String requiredPermission, String itemIcon, int customModelData) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.currency = currency;
            this.commands = commands;
            this.kit = kit;
            this.paymentUrl = paymentUrl;
            this.description = description != null ? description : new ArrayList<>();
            this.expiry = expiry;
            this.previewItems = previewItems != null ? previewItems : new ArrayList<>();
            this.requiredPermission = requiredPermission;
            this.itemIcon = itemIcon;
            this.customModelData = customModelData;
        }
    }
}
