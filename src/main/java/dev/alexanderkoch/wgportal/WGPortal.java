package dev.alexanderkoch.wgportal;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WGPortal — lightweight WorldGuard portal plugin for Paper 1.21.
 * <p>
 * Defines custom WorldGuard flags ({@code portal-target}, {@code portal-world},
 * {@code portal-coords}, {@code portal-enabled}) and handles player teleportation
 * when they walk into a portal region.
 * <p>
 * Depending on which flags are set, a player is either sent to a BungeeCord server
 * ({@code portal-target}), teleported to specific coordinates ({@code portal-coords}),
 * or moved to a world's spawn ({@code portal-world}). No database, no config hassle.
 */
public final class WGPortal extends JavaPlugin implements Listener, PluginMessageListener {

    private static final String BUNGEE_CHANNEL = "BungeeCord";
    private static final String TELEPORT_CHANNEL = "wgportal:teleport";
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();

    // ---- Custom WorldGuard Flags ----
    public static StringFlag PORTAL_TARGET;
    public static StringFlag PORTAL_WORLD;
    public static StringFlag PORTAL_COORDS;
    public static StateFlag PORTAL_ENABLED;

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private int cooldownSeconds;
    private String cooldownMessage;

    // ---- Lifecycle ----

    @Override
    public void onLoad() {
        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

        PORTAL_TARGET  = new StringFlag("portal-target");
        PORTAL_WORLD   = new StringFlag("portal-world");
        PORTAL_COORDS  = new StringFlag("portal-coords");
        PORTAL_ENABLED = new StateFlag("portal-enabled", true);

        Flag<?>[] flags = {PORTAL_TARGET, PORTAL_WORLD, PORTAL_COORDS, PORTAL_ENABLED};
        for (Flag<?> flag : flags) {
            try {
                registry.register(flag);
            } catch (FlagConflictException e) {
                getLogger().warning("Flag " + flag.getName() + " already registered.");
            }
        }

        getLogger().info("WGPortal flags registered.");
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        cooldownSeconds = getConfig().getInt("cooldown-seconds", 5);
        cooldownMessage = ChatColor.translateAlternateColorCodes('&',
                getConfig().getString("cooldown-message",
                        "&fYou are on cooldown for &b%d seconds&f."));

        // Register channels
        getServer().getMessenger().registerOutgoingPluginChannel(this, BUNGEE_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, TELEPORT_CHANNEL, this);

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("WGPortal v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, BUNGEE_CHANNEL);
        getServer().getMessenger().unregisterIncomingPluginChannel(this, TELEPORT_CHANNEL);
        pendingTeleports.clear();
        cooldowns.clear();
        getLogger().info("WGPortal disabled.");
    }

    // ---- Events ----

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        // Only process on block change
        if (to == null || event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }

        // Cooldown check
        long now = System.currentTimeMillis();
        long cooldownMs = cooldownSeconds * 1000L;
        Long last = cooldowns.get(player.getUniqueId());

        if (last != null && (now - last) < cooldownMs) {
            long remaining = ((cooldownMs - (now - last)) / 1000) + 1;
            player.sendMessage(String.format(cooldownMessage, remaining));
            return;
        }

        // Check WorldGuard regions at player's location
        RegionManager regions = WorldGuard.getInstance().getPlatform()
                .getRegionContainer().get(BukkitAdapter.adapt(to.getWorld()));

        if (regions == null) return;

        for (var region : regions.getApplicableRegions(BukkitAdapter.asBlockVector(to))) {
            if (region.getFlag(PORTAL_ENABLED) != StateFlag.State.ALLOW) continue;

            String target = region.getFlag(PORTAL_TARGET);
            String world  = region.getFlag(PORTAL_WORLD);
            String coords = region.getFlag(PORTAL_COORDS);

            // Skip if no action flag is set
            if (isEmpty(target) && isEmpty(world) && isEmpty(coords)) continue;

            // Apply cooldown and cancel movement
            cooldowns.put(player.getUniqueId(), now);
            event.setCancelled(true);

            // 1) BungeeCord server teleport via portal-target
            if (!isEmpty(target)) {
                getLogger().info("Teleporting " + player.getName() + " → " + target);

                // If portal-world / portal-coords are also set, forward teleport
                // data to the target server so it can position the player on join
                if (!isEmpty(world) || !isEmpty(coords)) {
                    sendPendingTeleport(player, target, world, coords);
                }

                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF(target);
                player.sendPluginMessage(this, BUNGEE_CHANNEL, out.toByteArray());
                return;
            }

            // 2) Coordinate teleport via portal-coords (optionally in portal-world)
            if (!isEmpty(coords)) {
                Location loc = parseCoords(coords, world, player);
                if (loc != null) {
                    getLogger().info("Teleporting " + player.getName() + " → " + formatLocation(loc));
                    player.teleport(loc);
                } else {
                    player.sendMessage(ChatColor.RED + "Invalid portal-coords format. "
                            + "Expected: x,y,z or x,y,z,yaw,pitch");
                }
                return;
            }

            // 3) World spawn teleport via portal-world
            if (!isEmpty(world)) {
                World targetWorld = getServer().getWorld(world);
                if (targetWorld != null) {
                    getLogger().info("Teleporting " + player.getName() + " → world " + world);
                    player.teleport(targetWorld.getSpawnLocation());
                } else {
                    player.sendMessage(ChatColor.RED + "World '" + world + "' not found.");
                }
                return;
            }
        }
    }

    // ---- Cross-server pending teleport (via BungeeCord Forward) ----

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(TELEPORT_CHANNEL)) return;

        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        UUID playerId = UUID.fromString(in.readUTF());
        String worldName = in.readUTF();
        String coordsValue = in.readUTF();

        pendingTeleports.put(playerId, new PendingTeleport(
                isEmpty(worldName) ? null : worldName,
                isEmpty(coordsValue) ? null : coordsValue
        ));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PendingTeleport pending = pendingTeleports.remove(player.getUniqueId());
        if (pending == null) return;
        applyPendingTeleport(player, pending);
    }

    private void applyPendingTeleport(Player player, PendingTeleport pending) {
        if (!isEmpty(pending.coords)) {
            Location loc = parseCoords(pending.coords, pending.world, player);
            if (loc != null) {
                player.teleport(loc);
                getLogger().info("Applied pending teleport for " + player.getName()
                        + " → " + formatLocation(loc));
            } else {
                player.sendMessage(ChatColor.RED + "Invalid portal-coords format. "
                        + "Expected: x,y,z or x,y,z,yaw,pitch");
            }
        } else if (!isEmpty(pending.world)) {
            World targetWorld = getServer().getWorld(pending.world);
            if (targetWorld != null) {
                player.teleport(targetWorld.getSpawnLocation());
                getLogger().info("Applied pending teleport for " + player.getName()
                        + " → world " + pending.world);
            } else {
                player.sendMessage(ChatColor.RED + "World '" + pending.world + "' not found.");
            }
        }
    }

    /**
     * Sends a Forward PluginMessage to the target server with the player's
     * pending teleport data (world + coords). The target server's WGPortal
     * instance will catch this and apply it on player join.
     */
    private void sendPendingTeleport(Player player, String target, String world, String coords) {
        try {
            ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
            DataOutputStream payloadOut = new DataOutputStream(payloadBytes);
            payloadOut.writeUTF(player.getUniqueId().toString());
            payloadOut.writeUTF(world != null ? world : "");
            payloadOut.writeUTF(coords != null ? coords : "");
            payloadOut.close();

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Forward");
            out.writeUTF(target);
            out.writeUTF(TELEPORT_CHANNEL);
            byte[] payload = payloadBytes.toByteArray();
            out.writeShort(payload.length);
            out.write(payload);

            player.sendPluginMessage(this, BUNGEE_CHANNEL, out.toByteArray());
        } catch (IOException e) {
            getLogger().warning("Failed to forward pending teleport data: " + e.getMessage());
        }
    }

    // ---- Helpers ----

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static Location parseCoords(String coords, String worldName, Player player) {
        String[] parts = coords.split(",");
        if (parts.length < 3) return null;

        try {
            double x = Double.parseDouble(parts[0].trim());
            double y = Double.parseDouble(parts[1].trim());
            double z = Double.parseDouble(parts[2].trim());

            World world;
            if (!isEmpty(worldName)) {
                world = player.getServer().getWorld(worldName);
                if (world == null) return null;
            } else {
                world = player.getWorld();
            }

            float yaw = player.getLocation().getYaw();
            float pitch = player.getLocation().getPitch();
            if (parts.length >= 5) {
                yaw = Float.parseFloat(parts[3].trim());
                pitch = Float.parseFloat(parts[4].trim());
            }

            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String formatLocation(Location loc) {
        return String.format("(%.1f, %.1f, %.1f) in %s",
                loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
    }

    // ---- Pending teleport record ----

    private record PendingTeleport(String world, String coords) {}
}
