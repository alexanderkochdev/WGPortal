package dev.alexanderkoch.wgportal.bungee;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.event.ServerConnectedEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BungeeCord companion plugin for WGPortal.
 * <p>
 * Receives pending teleport data from the source server (via {@code wgportal:teleport})
 * and, after the player connects to the target server ({@code ServerConnectedEvent}),
 * forwards the target world/coordinates to the Bukkit WGPortal instance
 * (via {@code wgportal:apply}) so it can position the player exactly.
 */
public final class WGPortalBungee extends Plugin implements Listener {

    private static final String TELEPORT_CHANNEL = "wgportal:teleport";
    private static final String APPLY_CHANNEL = "wgportal:apply";

    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        getProxy().registerChannel(TELEPORT_CHANNEL);
        getProxy().registerChannel(APPLY_CHANNEL);
        getProxy().getPluginManager().registerListener(this, this);
        getLogger().info("WGPortalBungee enabled — listening for teleport data.");
    }

    @Override
    public void onDisable() {
        pendingTeleports.clear();
        getProxy().unregisterChannel(TELEPORT_CHANNEL);
        getProxy().unregisterChannel(APPLY_CHANNEL);
        super.onDisable();
    }

    /**
     * Receives pending teleport data from the Bukkit WGPortal source server
     * on the {@code wgportal:teleport} channel. Stores it for later forwarding
     * when the player switches servers.
     */
    @EventHandler
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getTag().equals(TELEPORT_CHANNEL)) return;
        if (!(event.getReceiver() instanceof ProxiedPlayer player)) return;

        event.setCancelled(true);

        try {
            ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());
            UUID playerId = UUID.fromString(in.readUTF());
            String worldName = in.readUTF();
            String coordsValue = in.readUTF();

            if (!pendingTeleports.containsKey(playerId)) {
                pendingTeleports.put(playerId, new PendingTeleport(
                        isEmpty(worldName) ? null : worldName,
                        isEmpty(coordsValue) ? null : coordsValue,
                        System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            getLogger().warning("Invalid teleport data: " + e.getMessage());
        }
    }

    /**
     * Fires after the player has connected to the target server.
     * If a pending teleport exists, forwards the world/coords to the
     * Bukkit WGPortal on that server so it can position the player.
     */
    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        ProxiedPlayer player = event.getPlayer();
        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (pending == null || pending.isExpired()) return;

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(pending.world != null ? pending.world : "");
            out.writeUTF(pending.coords != null ? pending.coords : "");
            out.close();

            // Send via the player's connection to the server they just joined.
            // The Bukkit WGPortal will receive this as wgportal:apply and
            // teleport them immediately (player is already online).
            player.sendData(APPLY_CHANNEL, bytes.toByteArray());

            getLogger().info("Forwarded teleport data for "
                    + player.getName() + " → " + event.getServer().getInfo().getName());
        } catch (IOException e) {
            getLogger().warning("Failed to send apply data: " + e.getMessage());
        }
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private record PendingTeleport(String world, String coords, long createdAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > 15_000;
        }
    }
}
