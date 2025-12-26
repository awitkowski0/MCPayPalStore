package bond.thematic.paypalstore;

import bond.thematic.paypalstore.config.StoreConfig;
import bond.thematic.paypalstore.commands.StoreCommands;
import bond.thematic.paypalstore.server.WebhookServer;
import com.google.common.base.Suppliers;
import dev.architectury.registry.registries.RegistrarManager;
import dev.architectury.event.events.common.LifecycleEvent;

import java.util.function.Supplier;

public class PayPalStore {
  public static final String MOD_ID = "paypalstore";
  public static final Supplier<RegistrarManager> REGISTRIES = Suppliers.memoize(() -> RegistrarManager.get(MOD_ID));

  public static void init() {
    StoreConfig.load();
    StoreCommands.register();

    LifecycleEvent.SERVER_STARTED.register(server -> {
      WebhookServer.start(server);

      // Generate Documentation
      try {
        java.nio.file.Path docPath = dev.architectury.platform.Platform.getConfigFolder()
            .resolve("PAYPAL_STORE_README.md");
        if (!java.nio.file.Files.exists(docPath)) {
          java.nio.file.Files.writeString(docPath,
              """
                  # PayPal Store Mod

                  A comprehensive, server-side Minecraft mod for accepting PayPal payments via an in-game GUI. Supports Fabric and Forge (1.20.1).

                  ## Features
                  - **Secure Payments**: Direct integration with PayPal REST API.
                  - **In-Game GUI**: Browse items, preview kits, and purchase ranks without leaving the game.
                  - **Rich Customization**:
                      -   **Rich Text**: Descriptions support color codes (`&a`, `&l`) and multi-line formatting.
                      -   **Custom Icons**: Set any item as an icon, including `CustomModelData` for texture packs.
                      -   **Configurable Messages**: Translate and customize every chat message.
                  - **Robust Verification**: Uses polling (or webhooks) to verify payments even if the server is offline or behind a firewall.
                  - **Admin Tools**: Built-in commands to test rewards, reload config, and manage orders.
                  - **Developer API**: Hook into `StoreEvents` to trigger custom logic on payment completion.

                  ## Liability Disclaimer
                  **IMPORTANT**: By using this mod, you acknowledge that:
                  1. The mod author is NOT responsible for any financial transactions, chargebacks, disputes, or losses incurred by using this software.
                  2. You are solely responsible for compliance with PayPal's Acceptable Use Policy and local laws.
                  3. Use of this mod in a production environment is AT YOUR OWN RISK.

                  ## Configuration
                  The configuration file is located at `config/paypal_store.json`.

                  ### Example Config
                  ```json
                  {
                    "clientId": "YOUR_CLIENT_ID",
                    "clientSecret": "YOUR_CLIENT_SECRET",
                    "sandbox": true,
                    "debug": false,
                    "brandName": "My Server Store",
                    "landingPage": "BILLING",
                    "items": [
                      {
                        "id": "vip_rank",
                        "name": "&6VIP Rank",
                        "price": 10.00,
                        "currency": "USD",
                        "itemIcon": "minecraft:nether_star",
                        "description": [
                          "&7Unlock exclusive perks:",
                          "&e- Flight",
                          "&e- Special Kit"
                        ],
                        "previewItems": [
                          "minecraft:diamond_sword{Enchantments:[{id:sharpness,lvl:5}]} count:1",
                          "minecraft:golden_apple count:64"
                        ],
                        "commands": [
                          "lp user %player% parent add vip",
                          "give %player% diamond_sword 1"
                        ]
                      }
                    ]
                  }
                  ```

                  ### Item Configuration
                  -   **previewItems**: A list of items shown when right-clicking the shop item.
                      -   **Auto-Sync**: If you use the **Kits** mod (Fabric), leave this empty and set the `kit` field. The mod will automatically read the kit file!
                      -   **Manual**: If not using Kits mod, list items here manually.
                      -   Format: `itemId` or `itemId{nbt} count:Amount`
                      -   Example: `"minecraft:diamond_sword{Enchantments:[{id:sharpness,lvl:5}]} count:1"`
                  -   **commands**: List of commands to run upon purchase.
                      -   Use `%player%` as a placeholder for the username.
                      -   Example: `"lp user %player% parent add vip"` or `"kit give %player% starter"`

                  ## Commands
                  ### Player
                  - `/shop`: Opens the main shop GUI.

                  ### Admin (OP)
                  - `/shop reload`: Reloads the configuration file from disk.
                  - `/shop test <itemId>`: Simulates a purchase for the sender (triggers rewards without payment).
                  - `/shop give <player> <itemId>`: Forces a purchase logic for a specific player.
                  - `/paypal complete-test <orderId>`: Manually completes a pending order (useful if PayPal callback fails).

                  ## Developer API
                  You can listen to store events using the `StoreEvents` class.

                  ```java
                  import bond.thematic.paypalstore.api.StoreEvents;

                  StoreEvents.PAYMENT_COMPLETED.register((player, orderId) -> {
                      if (player != null) {
                          player.sendSystemMessage(Component.literal("Thanks for your support!"));
                      }
                  });
                  ```

                  ## Setup
                  1.  **PayPal Developer Account**:
                      -   Go to the [PayPal Developer Dashboard](https://developer.paypal.com/) and log in (or create an account).
                      -   Navigate to **Apps & Credentials**.
                      -   Toggle to **Live** (for real payments) or **Sandbox** (for testing).

                  2.  **Create App**:
                      -   Click **Create App**.
                      -   Enter an App Name (e.g., "Minecraft Store").
                      -   Click **Create App**.

                  3.  **Get Credentials**:
                      -   Copy the **Client ID**.
                      -   Click **Show** under "Secret" and copy the **Secret**.

                  4.  **Configure Mod**:
                      -   Open `config/paypal_store.json`.
                      -   Paste the `clientId` and `clientSecret`.
                      -   Set `sandbox` to `false` if using Live credentials.
                      -   Restart the server or run `/shop reload`.
                  """);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
    LifecycleEvent.SERVER_STOPPING.register(server -> WebhookServer.stop());
  }
}
