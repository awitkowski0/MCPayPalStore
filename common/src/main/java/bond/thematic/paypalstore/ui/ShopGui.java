package bond.thematic.paypalstore.ui;

import bond.thematic.paypalstore.config.StoreConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

public class ShopGui {
    public static void open(ServerPlayer player) {
        SimpleContainer inventory = new SimpleContainer(27); // 3 rows

        int slot = 0;
        for (StoreConfig.StoreItem item : StoreConfig.get().items) {
            if (slot >= 27)
                break;

            net.minecraft.world.item.Item mcItem = BuiltInRegistries.ITEM.get(new ResourceLocation(
                    item.itemIcon != null && !item.itemIcon.isEmpty() ? item.itemIcon : "minecraft:emerald"));
            ItemStack stack = new ItemStack(mcItem);

            if (item.customModelData > 0) {
                stack.getOrCreateTag().putInt("CustomModelData", item.customModelData);
            }

            stack.setHoverName(Component.literal(item.name.replace("&", "§")));

            // Lore
            java.util.List<Component> lore = new java.util.ArrayList<>();
            // Description
            for (String line : item.description) {
                lore.add(Component.literal(line.replace("&", "§")));
            }
            lore.add(Component.empty());

            // Price
            String priceStr = StoreConfig.get().messages.priceFormat
                    .replace("%price%", String.format("%.2f", item.price))
                    .replace("%currency%", item.currency)
                    .replace("&", "§");
            lore.add(Component.literal(priceStr).withStyle(net.minecraft.ChatFormatting.GOLD));

            // Requirement
            if (item.requiredPermission != null && !item.requiredPermission.isEmpty()) {
                lore.add(Component.literal("Requires: " + item.requiredPermission)
                        .withStyle(net.minecraft.ChatFormatting.RED));
            }
            // Expiry
            if (item.expiry != null && !item.expiry.isEmpty()) {
                lore.add(Component.literal("Expires: " + item.expiry).withStyle(net.minecraft.ChatFormatting.GRAY));
            }

            // Right-click hint
            if (!item.previewItems.isEmpty() || (!item.kits.isEmpty() || (item.kit != null && !item.kit.isEmpty()))) {
                String previewMsg = StoreConfig.get().messages.clickToPreview.replace("&", "§");
                if (!previewMsg.isEmpty()) {
                    lore.add(Component.empty());
                    lore.add(Component.literal(previewMsg)
                            .withStyle(net.minecraft.ChatFormatting.YELLOW));
                }
            }

            // Click hint
            String buyMsg = StoreConfig.get().messages.clickToBuy.replace("&", "§");
            if (!buyMsg.isEmpty()) {
                lore.add(Component.empty());
                lore.add(Component.literal(buyMsg)
                        .withStyle(net.minecraft.ChatFormatting.GREEN));
            }

            net.minecraft.nbt.ListTag loreTag = new net.minecraft.nbt.ListTag();
            for (Component c : lore) {
                loreTag.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(c)));
            }
            stack.getOrCreateTagElement("display").put("Lore", loreTag);

            inventory.setItem(slot, stack);
            slot++;
        }

        player.openMenu(new SimpleMenuProvider((syncId, playerInventory, playerEntity) -> {
            return StoreMenu.create(syncId, playerInventory, inventory);
        }, Component.literal("PayPal Store")));
    }
}
