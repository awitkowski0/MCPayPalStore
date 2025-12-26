package bond.thematic.paypalstore.integration;

import dev.architectury.platform.Platform;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.List;

public class KitsIntegration {
    public static boolean isLoaded() {
        return Platform.isModLoaded("kits");
    }

    public static List<ItemStack> getKitItems(String kitId) {
        if (!isLoaded()) {
            return Collections.emptyList();
        }

        try {
            // Placeholder since we don't have the API
            ItemStack placeholder = new ItemStack(Items.CHEST);
            placeholder.setHoverName(Component.literal("Kit: " + kitId));
            return List.of(placeholder);
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}
