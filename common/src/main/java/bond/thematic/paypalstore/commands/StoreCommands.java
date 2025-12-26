package bond.thematic.paypalstore.commands;

import bond.thematic.paypalstore.ui.ShopGui;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StoreCommands {
        public static void register() {
                dev.architectury.event.events.common.CommandRegistrationEvent.EVENT
                                .register((dispatcher, registry, selection) -> {
                                        dispatcher.register(Commands.literal("shop")
                                                        .executes(context -> {
                                                                ShopGui.open((ServerPlayer) context.getSource()
                                                                                .getPlayerOrException());
                                                                return 1;
                                                        })
                                                        .then(Commands.literal("reload")
                                                                        .requires(source -> source.hasPermission(4))
                                                                        .executes(context -> {
                                                                                bond.thematic.paypalstore.config.StoreConfig
                                                                                                .load();
                                                                                context.getSource().sendSuccess(
                                                                                                () -> Component.literal(
                                                                                                                "Store config reloaded!")
                                                                                                                .withStyle(net.minecraft.ChatFormatting.GREEN),
                                                                                                true);
                                                                                return 1;
                                                                        }))
                                                        .then(Commands.literal("test")
                                                                        .requires(source -> source.hasPermission(4))
                                                                        .then(Commands.argument("itemId",
                                                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                                                        .string())
                                                                                        .executes(context -> {
                                                                                                String itemId = com.mojang.brigadier.arguments.StringArgumentType
                                                                                                                .getString(context,
                                                                                                                                "itemId");
                                                                                                ServerPlayer player = (ServerPlayer) context
                                                                                                                .getSource()
                                                                                                                .getPlayerOrException();

                                                                                                bond.thematic.paypalstore.config.StoreConfig.StoreItem item = bond.thematic.paypalstore.config.StoreConfig
                                                                                                                .get().items
                                                                                                                .stream()
                                                                                                                .filter(i -> i.id
                                                                                                                                .equals(itemId))
                                                                                                                .findFirst()
                                                                                                                .orElse(null);

                                                                                                if (item == null) {
                                                                                                        context.getSource()
                                                                                                                        .sendFailure(Component
                                                                                                                                        .literal("Item not found: "
                                                                                                                                                        + itemId));
                                                                                                        return 0;
                                                                                                }

                                                                                                // Execute commands
                                                                                                for (String cmd : item.commands) {
                                                                                                        player.getServer()
                                                                                                                        .getCommands()
                                                                                                                        .performPrefixedCommand(
                                                                                                                                        player.getServer()
                                                                                                                                                        .createCommandSourceStack(),
                                                                                                                                        cmd.replace("%player%",
                                                                                                                                                        player.getGameProfile()
                                                                                                                                                                        .getName()));
                                                                                                }
                                                                                                context.getSource()
                                                                                                                .sendSuccess(() -> Component
                                                                                                                                .literal("Executed purchase logic for "
                                                                                                                                                + itemId),
                                                                                                                                true);
                                                                                                return 1;
                                                                                        })))
                                                        .then(Commands.literal("give")
                                                                        .requires(source -> source.hasPermission(4))
                                                                        .then(Commands.argument("player",
                                                                                        net.minecraft.commands.arguments.EntityArgument
                                                                                                        .player())
                                                                                        .then(Commands.argument(
                                                                                                        "itemId",
                                                                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                                                                        .string())
                                                                                                        .executes(context -> {
                                                                                                                ServerPlayer target = net.minecraft.commands.arguments.EntityArgument
                                                                                                                                .getPlayer(context,
                                                                                                                                                "player");
                                                                                                                String itemId = com.mojang.brigadier.arguments.StringArgumentType
                                                                                                                                .getString(context,
                                                                                                                                                "itemId");

                                                                                                                bond.thematic.paypalstore.config.StoreConfig.StoreItem item = bond.thematic.paypalstore.config.StoreConfig
                                                                                                                                .get().items
                                                                                                                                .stream()
                                                                                                                                .filter(i -> i.id
                                                                                                                                                .equals(itemId))
                                                                                                                                .findFirst()
                                                                                                                                .orElse(null);

                                                                                                                if (item == null) {
                                                                                                                        context.getSource()
                                                                                                                                        .sendFailure(Component
                                                                                                                                                        .literal("Item not found: "
                                                                                                                                                                        + itemId));
                                                                                                                        return 0;
                                                                                                                }

                                                                                                                // Execute
                                                                                                                // commands
                                                                                                                for (String cmd : item.commands) {
                                                                                                                        target.getServer()
                                                                                                                                        .getCommands()
                                                                                                                                        .performPrefixedCommand(
                                                                                                                                                        target.getServer()
                                                                                                                                                                        .createCommandSourceStack(),
                                                                                                                                                        cmd.replace("%player%",
                                                                                                                                                                        target.getGameProfile()
                                                                                                                                                                                        .getName()));
                                                                                                                }
                                                                                                                context.getSource()
                                                                                                                                .sendSuccess(() -> Component
                                                                                                                                                .literal("Gave " + itemId
                                                                                                                                                                + " to "
                                                                                                                                                                + target.getName()
                                                                                                                                                                                .getString()),
                                                                                                                                                true);
                                                                                                                return 1;
                                                                                                        })))));

                                        dispatcher.register(Commands.literal("donate")
                                                        .executes(context -> {
                                                                context.getSource().sendSuccess(() -> Component
                                                                                .literal("Usage: /donate <amount>"),
                                                                                false);
                                                                return 1;
                                                        }));
                                        dispatcher.register(Commands.literal("paypal")
                                                        .requires(source -> source.hasPermission(4))
                                                        .then(Commands.literal("complete-test")
                                                                        .then(Commands
                                                                                        .argument("orderId",
                                                                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                                                                        .string())
                                                                                        .executes(context -> {
                                                                                                String orderId = com.mojang.brigadier.arguments.StringArgumentType
                                                                                                                .getString(context,
                                                                                                                                "orderId");
                                                                                                bond.thematic.paypalstore.OrderManager
                                                                                                                .completeOrder(orderId);
                                                                                                context.getSource()
                                                                                                                .sendSuccess(
                                                                                                                                () -> Component
                                                                                                                                                .literal("Forced completion of order: "
                                                                                                                                                                + orderId)
                                                                                                                                                .withStyle(net.minecraft.ChatFormatting.GREEN),
                                                                                                                                true);
                                                                                                return 1;
                                                                                        }))));
                                });
        }
}
