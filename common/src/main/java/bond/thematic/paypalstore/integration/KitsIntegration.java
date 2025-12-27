package bond.thematic.paypalstore.integration;

import dev.architectury.platform.Platform;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class KitsIntegration {
    public static class KitDetails {
        public String id;
        public long cooldown; // In milliseconds or ticks? Usually mod dependent. assuming seconds or ticks.
        // The Kits mod typically uses seconds for cooldown in config/commands.
        public List<ItemStack> items;

        public KitDetails(String id, long cooldown, List<ItemStack> items) {
            this.id = id;
            this.cooldown = cooldown;
            this.items = items;
        }
    }

    public static boolean isLoaded() {
        return Platform.isModLoaded("kits");
    }

    public static KitDetails getKitDetails(String kitId) {
        try {
            java.nio.file.Path kitPath = dev.architectury.platform.Platform.getConfigFolder()
                    .resolve("kits/" + kitId + ".nbt");

            if (java.nio.file.Files.exists(kitPath)) {
                net.minecraft.nbt.CompoundTag tag;
                try {
                    tag = net.minecraft.nbt.NbtIo.readCompressed(kitPath.toFile());
                } catch (java.util.zip.ZipException | java.io.UTFDataFormatException e) {
                    tag = net.minecraft.nbt.NbtIo.read(kitPath.toFile());
                }

                long cooldown = 0;
                if (tag.contains("cooldown")) {
                    cooldown = tag.getLong("cooldown");
                }

                if (tag.contains("inventory")) {
                    net.minecraft.nbt.ListTag inventoryTag = tag.getList("inventory", 10);
                    List<ItemStack> items = new java.util.ArrayList<>();

                    for (int i = 0; i < inventoryTag.size(); i++) {
                        net.minecraft.nbt.CompoundTag itemTag = inventoryTag.getCompound(i);
                        items.add(ItemStack.of(itemTag));
                    }
                    return new KitDetails(kitId, cooldown, items);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new KitDetails(kitId, 0, Collections.emptyList());
    }

    public static List<ItemStack> getKitItems(String kitId) {
        return getKitDetails(kitId).items;
    }

    public static List<KitDetails> getAllKitDetails(List<String> kitIds) {
        if (kitIds == null || kitIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<KitDetails> details = new java.util.ArrayList<>();
        for (String kitId : kitIds) {
            details.add(getKitDetails(kitId));
        }
        return details;
    }

    public static List<ItemStack> getAllKitItems(List<String> kitIds) {
        if (kitIds == null || kitIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> allItems = new java.util.ArrayList<>();
        for (String kitId : kitIds) {
            allItems.addAll(getKitItems(kitId));
        }
        return allItems;
    }
}
