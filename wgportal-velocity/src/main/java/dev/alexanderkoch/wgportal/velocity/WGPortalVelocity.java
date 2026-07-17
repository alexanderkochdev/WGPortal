package dev.alexanderkoch.wgportal.velocity;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Velocity companion for WGPortal.
 * <p>
 * Receives teleport data from the source Bukkit server via plugin message channel
 * {@code wgportal:teleport}, stores it temporarily, and forwards it to the target
 * Bukkit server via {@code wgportal:apply} once the player has connected.
 */
@Plugin(
        id = "wgportal-velocity",
        name = "WGPortalVelocity",
        version = "1.1.0",
        description = "Velocity companion for WGPortal — forwards teleport data across servers.",
        authors = {"alexanderkochdev"}
)
public final class WGPortalVelocity {

    private static final MinecraftChannelIdentifier TELEPORT_CHANNEL =
            MinecraftChannelIdentifier.create("wgportal", "teleport");
    private static final MinecraftChannelIdentifier APPLY_CHANNEL =
            MinecraftChannelIdentifier.create("wgportal", "apply");

    private static final long TELEPORT_TTL_MS = 15_000L;

    private final ProxyServer server;
    private final Logger logger;
    private final Map<UUID, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

    @Inject
    public WGPortalVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(TELEPORT_CHANNEL, APPLY_CHANNEL);
        server.getEventManager().register(this, this);
        logger.info("WGPortalVelocity enabled — listening for teleport data on Velocity.");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(TELEPORT_CHANNEL)) {
            return;
        }

        if (!(event.getSource() instanceof Player player)) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        ByteArrayDataInput in = ByteStreams.newDataInput(event.getData());

        String uuidStr;
        try {
            uuidStr = in.readUTF();
        } catch (Exception e) {
            logger.warn("Malformed teleport data from {}", player.getUsername());
            return;
        }

        UUID uuid;
        try {
            uuid = UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID in teleport data from {}", player.getUsername());
            return;
        }

        String world = in.readUTF();
        String coords = in.readUTF();

        pendingTeleports.put(uuid, new PendingTeleport(world, coords, System.currentTimeMillis()));
        logger.info("Stored teleport for {} -> world={} coords={}", player.getUsername(), world, coords);
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (pending == null || pending.isExpired()) {
            return;
        }

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeUTF(pending.world());
                out.writeUTF(pending.coords());
            }

            event.getServer().sendPluginMessage(APPLY_CHANNEL, bytes.toByteArray());
            logger.info("Applied teleport for {} -> world={} coords={}",
                    player.getUsername(), pending.world(), pending.coords());
        } catch (IOException e) {
            logger.error("Failed to send apply data for {}", player.getUsername(), e);
        }
    }

    private record PendingTeleport(String world, String coords, long timestamp) {
        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > TELEPORT_TTL_MS;
        }
    }
}
