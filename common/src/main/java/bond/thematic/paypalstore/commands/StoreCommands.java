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
                                ShopGui.open((ServerPlayer) context.getSource().getPlayerOrException());
                                return 1;
                            }));

                    dispatcher.register(Commands.literal("donate")
                            .executes(context -> {
                                context.getSource().sendSuccess(() -> Component.literal("Usage: /donate <amount>"),
                                        false);
                                return 1;
                            }));
                    dispatcher.register(Commands.literal("paypal")
                            .requires(source -> source.hasPermission(4))
                            .then(Commands.literal("complete-test")
                                    .then(Commands
                                            .argument("orderId",
                                                    com.mojang.brigadier.arguments.StringArgumentType.string())
                                            .executes(context -> {
                                                String orderId = com.mojang.brigadier.arguments.StringArgumentType
                                                        .getString(context, "orderId");
                                                bond.thematic.paypalstore.OrderManager.completeOrder(orderId);
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
