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
    public PreviewMenu(int syncId, Inventory playerInventory, Container container, ServerPlayer player) {
        super(MenuType.GENERIC_9x6, syncId, playerInventory, container, 6);
    }

    public static void open(ServerPlayer player, StoreConfig.StoreItem item) {
        SimpleContainer inventory = new SimpleContainer(54);

        if (!item.previewItems.isEmpty()) {
            int slot = 0;
            for (String itemStr : item.previewItems) {
                if (slot >= 45)
                    break;
                inventory.setItem(slot, parseStack(itemStr));
                slot++;
            }
        } else if (item.kit != null && !item.kit.isEmpty()) {
            java.util.List<ItemStack> kitItems = bond.thematic.paypalstore.integration.KitsIntegration
                    .getKitItems(item.kit);
            int slot = 0;
            for (ItemStack stack : kitItems) {
                if (slot >= 45)
                    break;
                inventory.setItem(slot, stack);
                slot++;
            }
        }

        // Info Info
        if (item.expiry != null && !item.expiry.isEmpty()) {
            ItemStack info = new ItemStack(Items.CLOCK);
            info.setHoverName(Component.literal("Expiry: " + item.expiry).withStyle(ChatFormatting.GOLD));
            inventory.setItem(49, info);
        }

        // Back Button
        ItemStack back = new ItemStack(Items.ARROW);
        back.setHoverName(Component.literal("Back to Shop").withStyle(ChatFormatting.RED));
        inventory.setItem(53, back);

        player.openMenu(new SimpleMenuProvider((syncId, playerInventory, playerEntity) -> {
            return new PreviewMenu(syncId, playerInventory, inventory, player);
        }, Component.literal("Preview: " + item.name)));
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
            if (slotId == 53) { // Back button
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
}
