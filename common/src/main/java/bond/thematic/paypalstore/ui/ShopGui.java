package bond.thematic.paypalstore.ui;

import bond.thematic.paypalstore.config.StoreConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ShopGui {
    /** Slots 0-17 hold listings; the bottom row is navigation. */
    private static final int ITEMS_PER_PAGE = 18;

    private static final int SLOT_PREV = 18;
    private static final int SLOT_PAGE = 22;
    private static final int SLOT_NEXT = 24;
    private static final int SLOT_ADMIN_SHOP = 26;

    /** Marks the nav buttons; the target page travels alongside in {@code nav_page}. */
    public static final String NAV_PAGE = "page";
    public static final String NAV_ADMIN_SHOP = "admin_shop";

    public static void open(ServerPlayer player) {
        open(player, 0);
    }

    /**
     * Renders one page of the store. The listing used to be capped at the 26 slots of a single
     * screen and anything past that was dropped without a word, so the store could silently stop
     * showing items as it grew.
     */
    public static void open(ServerPlayer player, int page) {
        List<StoreConfig.StoreItem> visible = new ArrayList<>();
        for (StoreConfig.StoreItem item : StoreConfig.get().items) {
            if (bond.thematic.paypalstore.integration.PermissionIntegration.hasPermission(player,
                    item.requiredPermission)) {
                visible.add(item);
            }
        }

        int totalPages = Math.max(1, (visible.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        SimpleContainer inventory = new SimpleContainer(27);

        int start = currentPage * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && start + i < visible.size(); i++) {
            inventory.setItem(i, buildListing(visible.get(start + i)));
        }

        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV, navButton("§ePrevious Page", "§7Page " + currentPage + " of " + totalPages,
                    currentPage - 1));
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, navButton("§aNext Page", "§7Page " + (currentPage + 2) + " of " + totalPages,
                    currentPage + 1));
        }
        if (totalPages > 1) {
            ItemStack indicator = new ItemStack(net.minecraft.world.item.Items.BOOK);
            indicator.setHoverName(Component.literal("§fPage " + (currentPage + 1) + " of " + totalPages));
            setLore(indicator, List.of(Component.literal("§7" + visible.size() + " item(s) available")));
            inventory.setItem(SLOT_PAGE, indicator);
        }

        ItemStack adminShopStack = new ItemStack(net.minecraft.world.item.Items.CHEST);
        adminShopStack.setHoverName(Component.literal("§bAdmin Shop"));
        setLore(adminShopStack, List.of(
                Component.literal("§7Looking for the server's admin shop?"),
                Component.literal("§eClick to open /shop")));
        adminShopStack.getOrCreateTag().putString("nav_action", NAV_ADMIN_SHOP);
        inventory.setItem(SLOT_ADMIN_SHOP, adminShopStack);

        String title = "PayPal Store";
        if (totalPages > 1) {
            title += " (" + (currentPage + 1) + "/" + totalPages + ")";
        }
        final String finalTitle = title;
        player.openMenu(new SimpleMenuProvider((syncId, playerInventory, playerEntity) -> {
            return StoreMenu.create(syncId, playerInventory, inventory);
        }, Component.literal(finalTitle)));
    }

    private static ItemStack buildListing(StoreConfig.StoreItem item) {
        net.minecraft.world.item.Item mcItem = BuiltInRegistries.ITEM.get(new ResourceLocation(
                item.itemIcon != null && !item.itemIcon.isEmpty() ? item.itemIcon : "minecraft:emerald"));
        ItemStack stack = new ItemStack(mcItem);

        if (item.customModelData > 0) {
            stack.getOrCreateTag().putInt("CustomModelData", item.customModelData);
        }
        stack.getOrCreateTag().putString("store_item_id", item.id);
        stack.setHoverName(Component.literal(item.name.replace("&", "§")));

        List<Component> lore = new ArrayList<>();
        for (String line : item.description) {
            lore.add(Component.literal(line.replace("&", "§")));
        }
        lore.add(Component.empty());

        String priceStr = StoreConfig.get().messages.priceFormat
                .replace("%price%", String.format("%.2f", item.price))
                .replace("%currency%", item.currency)
                .replace("&", "§");
        lore.add(Component.literal(priceStr).withStyle(net.minecraft.ChatFormatting.GOLD));

        if (item.requiredPermission != null && !item.requiredPermission.isEmpty()) {
            lore.add(Component.literal("Requires: " + item.requiredPermission)
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
        if (item.expiry != null && !item.expiry.isEmpty()) {
            lore.add(Component.literal("Expires: " + item.expiry).withStyle(net.minecraft.ChatFormatting.GRAY));
        }

        if (!item.previewItems.isEmpty() || (!item.kits.isEmpty() || (item.kit != null && !item.kit.isEmpty()))) {
            String previewMsg = StoreConfig.get().messages.clickToPreview.replace("&", "§");
            if (!previewMsg.isEmpty()) {
                lore.add(Component.empty());
                lore.add(Component.literal(previewMsg).withStyle(net.minecraft.ChatFormatting.YELLOW));
            }
        }

        String buyMsg = StoreConfig.get().messages.clickToBuy.replace("&", "§");
        if (!buyMsg.isEmpty()) {
            lore.add(Component.empty());
            lore.add(Component.literal(buyMsg).withStyle(net.minecraft.ChatFormatting.GREEN));
        }

        setLore(stack, lore);
        return stack;
    }

    private static ItemStack navButton(String name, String subtitle, int targetPage) {
        ItemStack stack = new ItemStack(net.minecraft.world.item.Items.ARROW);
        stack.setHoverName(Component.literal(name));
        setLore(stack, List.of(Component.literal(subtitle)));
        stack.getOrCreateTag().putString("nav_action", NAV_PAGE);
        stack.getOrCreateTag().putInt("nav_page", targetPage);
        return stack;
    }

    private static void setLore(ItemStack stack, List<Component> lore) {
        net.minecraft.nbt.ListTag loreTag = new net.minecraft.nbt.ListTag();
        for (Component c : lore) {
            loreTag.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(c)));
        }
        stack.getOrCreateTagElement("display").put("Lore", loreTag);
    }
}
