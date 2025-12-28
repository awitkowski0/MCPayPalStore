package bond.thematic.paypalstore.integration;

import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Method;

public class PermissionIntegration {
    private static boolean checkedFabricPermissions = false;
    private static Method fabricCheckMethod = null;

    /**
     * Checks if a player has a specific permission node.
     * Supports Fabric Permissions API via reflection.
     * Fallback: OPs (level 4) always have permission.
     *
     * @param player The player to check
     * @param node   The permission node (e.g. "paypalstore.vip")
     * @return true if player has permission, false otherwise.
     */
    public static boolean hasPermission(ServerPlayer player, String node) {
        // No permission required
        if (node == null || node.isEmpty())
            return true;

        // OPs always have access
        if (player.hasPermissions(4))
            return true;

        // Try Fabric Permissions API (which LuckPerms supports)
        try {
            if (!checkedFabricPermissions) {
                try {
                    // public static boolean check(Entity entity, String permission)
                    Class<?> clazz = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
                    fabricCheckMethod = clazz.getMethod("check", net.minecraft.world.entity.Entity.class, String.class);
                } catch (Exception ignored) {
                    // Class not found
                }
                checkedFabricPermissions = true;
            }

            if (fabricCheckMethod != null) {
                return (boolean) fabricCheckMethod.invoke(null, player, node);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
