package bond.thematic.paypalstore.ui;

import bond.thematic.paypalstore.config.StoreConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class PreviewMenu extends ChestMenu {
    /** Slots 0-44 show contents; the bottom row is reserved for navigation and the buy button. */
    private static final int CONTENT_SLOTS = 45;
    private static final int SLOTS_PER_ROW = 9;

    private static final int SLOT_PREV = 45;
    private static final int SLOT_NEXT = 46;
    private static final int SLOT_PAGE = 48;
    private static final int SLOT_EXPIRY = 49;
    private static final int SLOT_BUY = 50;
    private static final int SLOT_BACK = 53;

    private final StoreConfig.StoreItem item;
    private final ServerPlayer player;
    private final int page;

    public PreviewMenu(int syncId, Inventory playerInventory, Container container, ServerPlayer player,
            StoreConfig.StoreItem item, int page) {
        super(MenuType.GENERIC_9x6, syncId, playerInventory, container, 6);
        this.player = player;
        this.item = item;
        this.page = page;
    }

    public static void open(ServerPlayer player, StoreConfig.StoreItem item) {
        open(player, item, 0);
    }

    /**
     * Renders one page of an item's contents. Previously everything was written into slots 0-44 and
     * whatever did not fit was dropped silently, so a rank granting a dozen inherited kits appeared
     * to contain only the first few - the buyer had no way to tell the list had been cut short.
     */
    public static void open(ServerPlayer player, StoreConfig.StoreItem item, int page) {
        java.util.List<ItemStack> content = buildContent(item);
        int totalPages = Math.max(1, (content.size() + CONTENT_SLOTS - 1) / CONTENT_SLOTS);
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        SimpleContainer inventory = new SimpleContainer(54);
        int start = currentPage * CONTENT_SLOTS;
        for (int i = 0; i < CONTENT_SLOTS && start + i < content.size(); i++) {
            inventory.setItem(i, content.get(start + i));
        }

        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV, navButton("\u00a7ePrevious Page", "\u00a77Page " + currentPage + " of " + totalPages));
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, navButton("\u00a7aNext Page", "\u00a77Page " + (currentPage + 2) + " of " + totalPages));
        }
        if (totalPages > 1) {
            ItemStack indicator = new ItemStack(Items.BOOK);
            indicator.setHoverName(Component.literal("Page " + (currentPage + 1) + " of " + totalPages)
                    .withStyle(ChatFormatting.WHITE));
            inventory.setItem(SLOT_PAGE, indicator);
        }

        if (item.expiry != null && !item.expiry.isEmpty()) {
            ItemStack info = new ItemStack(Items.CLOCK);
            info.setHoverName(Component.literal("Expiry: " + item.expiry).withStyle(ChatFormatting.GOLD));
            inventory.setItem(SLOT_EXPIRY, info);
        }

        ItemStack buy = new ItemStack(Items.GREEN_CONCRETE);
        String buyStr;
        if (item.isSubscription) {
            buyStr = "Subscribe for " + String.format("%.2f", item.price) + " " + item.currency + " / " + item.interval;
        } else {
            buyStr = StoreConfig.get().messages.buyButton
                    .replace("%price%", String.format("%.2f", item.price))
                    .replace("%currency%", item.currency)
                    .replace("&", "\u00a7");
        }
        if (buyStr != null && !buyStr.isEmpty()) {
            buy.setHoverName(Component.literal(buyStr).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
        } else {
            buy.setHoverName(Component.empty());
        }
        inventory.setItem(SLOT_BUY, buy);

        ItemStack back = new ItemStack(Items.ARROW);
        String backStr = StoreConfig.get().messages.backToShopButton.replace("&", "\u00a7");
        if (!backStr.isEmpty()) {
            back.setHoverName(Component.literal(backStr).withStyle(ChatFormatting.RED));
        } else {
            back.setHoverName(Component.empty());
        }
        inventory.setItem(SLOT_BACK, back);

        String title = "Preview: " + ChatFormatting.stripFormatting(item.name.replace("&", "\u00a7"));
        if (totalPages > 1) {
            title += " (" + (currentPage + 1) + "/" + totalPages + ")";
        }
        final int openPage = currentPage;
        final String finalTitle = title;
        player.openMenu(new SimpleMenuProvider((syncId, playerInventory, playerEntity) -> {
            return new PreviewMenu(syncId, playerInventory, inventory, player, item, openPage);
        }, Component.literal(finalTitle)));
    }

    /**
     * The full contents as a flat slot list, unbounded - paging decides what actually fits.
     * Each kit starts on a fresh row so its header sits directly above its own items; because a page
     * is exactly five rows, that alignment survives page boundaries.
     */
    private static java.util.List<ItemStack> buildContent(StoreConfig.StoreItem item) {
        java.util.List<ItemStack> content = new java.util.ArrayList<>();

        if (!item.previewItems.isEmpty()) {
            for (String itemStr : item.previewItems) {
                content.add(parseStack(itemStr));
            }
            return content;
        }

        java.util.List<String> kitsToUse = new java.util.ArrayList<>();
        if (item.kits != null) {
            kitsToUse.addAll(item.kits);
        }
        if (kitsToUse.isEmpty() && item.kit != null && !item.kit.isEmpty()) {
            kitsToUse.add(item.kit);
        }
        if (kitsToUse.isEmpty()) {
            return content;
        }

        java.util.List<bond.thematic.paypalstore.integration.KitsIntegration.KitDetails> allDetails =
                bond.thematic.paypalstore.integration.KitsIntegration.getAllKitDetails(kitsToUse);

        for (bond.thematic.paypalstore.integration.KitsIntegration.KitDetails details : allDetails) {
            while (content.size() % SLOTS_PER_ROW != 0) {
                content.add(filler());
            }

            ItemStack header = new ItemStack(Items.PAPER);
            header.setHoverName(Component.literal("Kit: " + details.id)
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
            lore.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(
                    Component.literal(details.cooldown > 0
                            ? "Cooldown: " + formatTime(details.cooldown / 1000)
                            : "No Cooldown").withStyle(ChatFormatting.GRAY))));
            lore.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(
                    Component.literal(details.items.size() + " item(s)").withStyle(ChatFormatting.DARK_GRAY))));
            header.getOrCreateTagElement("display").put("Lore", lore);
            content.add(header);

            content.addAll(details.items);
        }
        return content;
    }

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
        pane.setHoverName(Component.empty());
        return pane;
    }

    private static ItemStack navButton(String name, String subtitle) {
        ItemStack stack = new ItemStack(Items.ARROW);
        stack.setHoverName(Component.literal(name));
        net.minecraft.nbt.ListTag lore = new net.minecraft.nbt.ListTag();
        lore.add(net.minecraft.nbt.StringTag.valueOf(Component.Serializer.toJson(Component.literal(subtitle))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
        return stack;
    }

    private static ItemStack parseStack(String itemStr) {
        try {
            // Format: id{nbt} count:Amount
            // Basic parsing
            String id = itemStr.split("\\{")[0].split(" ")[0];
            int count = 1;
            String nbt = null;

            if (itemStr.contains("count:")) {
                String[] parts = itemStr.split("count:");
                if (parts.length > 1) {
                    try {
                        count = Integer.parseInt(parts[1].trim().split(" ")[0]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            if (itemStr.contains("{") && itemStr.lastIndexOf("}") > itemStr.indexOf("{")) {
                nbt = itemStr.substring(itemStr.indexOf("{"), itemStr.lastIndexOf("}") + 1);
            }

            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
            ItemStack stack = new ItemStack(item, count);

            if (nbt != null) {
                CompoundTag tag = TagParser.parseTag(nbt);
                stack.setTag(tag);
            }
            return stack;
        } catch (Exception e) {
            e.printStackTrace();
            ItemStack error = new ItemStack(Items.BARRIER);
            error.setHoverName(Component.literal("Error parsing: " + itemStr).withStyle(ChatFormatting.RED));
            return error;
        }
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // Cancel all clicks in top inventory
        if (slotId >= 0 && slotId < this.getContainer().getContainerSize()) {
            // Guard on the slot actually holding a button: when there is no previous/next page the
            // slot is empty, and clicking bare glass should do nothing rather than re-render.
            if (slotId == SLOT_PREV && this.slots.get(slotId).hasItem()) {
                open(this.player, this.item, this.page - 1);
                return;
            }
            if (slotId == SLOT_NEXT && this.slots.get(slotId).hasItem()) {
                open(this.player, this.item, this.page + 1);
                return;
            }
            if (slotId == SLOT_BUY) { // Buy Button
                if (this.player != null && this.item != null) {
                    this.player.closeContainer();

                    if (item.paymentUrl != null && !item.paymentUrl.isEmpty()) {
                        // Use static NCP link
                        String finalUrl = item.paymentUrl;
                        if (!finalUrl.contains("?")) {
                            finalUrl += "?custom=" + this.player.getGameProfile().getName();
                        } else {
                            finalUrl += "&custom=" + this.player.getGameProfile().getName();
                        }

                        net.minecraft.network.chat.ClickEvent.Action action = net.minecraft.network.chat.ClickEvent.Action.OPEN_URL;

                        Component link = Component.literal(" [CLICK TO PAY] ")
                                .setStyle(net.minecraft.network.chat.Style.EMPTY
                                        .withColor(ChatFormatting.GREEN)
                                        .withBold(true)
                                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                                action, finalUrl)));

                        this.player.sendSystemMessage(Component.literal("Opening payment link for " + item.name + "...")
                                .withStyle(ChatFormatting.YELLOW).append(link));
                        this.player.sendSystemMessage(Component
                                .literal(
                                        "NOTE: verification for this item is manual or requires server-side IPN setup.")
                                .withStyle(ChatFormatting.RED));
                        return;
                    }

                    if (item.isSubscription) {
                        bond.thematic.paypalstore.SubscriptionManager.initiateSubscription(this.player, item);
                    } else {
                        bond.thematic.paypalstore.OrderManager.createOrder(this.player, this.item, () -> {
                            // Execute commands
                            for (String cmd : item.commands) {
                                this.player.getServer().getCommands().performPrefixedCommand(
                                        this.player.getServer().createCommandSourceStack(),
                                        cmd.replace("%player%", this.player.getGameProfile().getName()));
                            }
                        });
                    }
                }
            }
            if (slotId == SLOT_BACK) { // Back button
                if (player instanceof ServerPlayer serverPlayer) {
                    ShopGui.open(serverPlayer);
                }
            }
            return; // Cancel
        }

        // Prevent shift click into
        if (slotId >= 0) {
            if (clickType == ClickType.QUICK_MOVE || clickType == ClickType.SWAP) {
                return; // Cancel
            }
        }

        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    private static String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            long h = hours % 24;
            return days + "d " + (h > 0 ? h + "h" : "");
        }
        if (hours > 0) {
            long m = minutes % 60;
            return hours + "h " + (m > 0 ? m + "m" : "");
        }
        long s = seconds % 60;
        return minutes + "m " + (s > 0 ? s + "s" : "");
    }
}
