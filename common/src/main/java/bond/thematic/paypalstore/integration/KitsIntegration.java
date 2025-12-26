package bond.thematic.paypalstore.integration;

import dev.architectury.platform.Platform;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class KitsIntegration {
    public static boolean isLoaded() {
        return Platform.isModLoaded("kits");
    }

    public static List<ItemStack> getKitItems(String kitId) {
        // We do NOT check isLoaded() because we are reading config files directly.
        // This allows the preview to work even if the mod isn't loaded (as long as the
        // configs exist).

        try {
            java.nio.file.Path kitPath = dev.architectury.platform.Platform.getConfigFolder()
                    .resolve("kits/" + kitId + ".nbt");

            if (java.nio.file.Files.exists(kitPath)) {
                net.minecraft.nbt.CompoundTag tag;
                try {
                    tag = net.minecraft.nbt.NbtIo.readCompressed(kitPath.toFile());
                } catch (java.util.zip.ZipException | java.io.UTFDataFormatException e) {
                    // Fallback to uncompressed read
                    tag = net.minecraft.nbt.NbtIo.read(kitPath.toFile());
                }

                // The Kits mod saves items in an "inventory" list tag
                if (tag.contains("inventory")) {
                    net.minecraft.nbt.ListTag inventoryTag = tag.getList("inventory", 10); // 10 = Compound
                    List<ItemStack> items = new java.util.ArrayList<>();

                    for (int i = 0; i < inventoryTag.size(); i++) {
                        net.minecraft.nbt.CompoundTag itemTag = inventoryTag.getCompound(i);
                        items.add(ItemStack.of(itemTag));
                    }
                    return items;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }
}
