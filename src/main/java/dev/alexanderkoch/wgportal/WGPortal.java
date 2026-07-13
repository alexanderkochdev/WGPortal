package dev.alexanderkoch.wgportal;

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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WGPortal — lightweight WorldGuard portal plugin for Paper 1.21.
 * <p>
 * Defines custom WorldGuard flags ({@code portal-target}, {@code portal-world},
 * {@code portal-coords}, {@code portal-enabled}) and teleports players to
 * a BungeeCord server when they walk into a portal region.
 * <p>
 * Create portal regions manually with WorldGuard and set the {@code portal-target}
 * flag to the target server name. No database, no config hassle.
 */
public final class WGPortal extends JavaPlugin implements Listener {

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

        // Register BungeeCord channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("WGPortal v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
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
            if (target == null || target.isEmpty()) continue;

            // Teleport player
            cooldowns.put(player.getUniqueId(), now);
            getLogger().info("Teleporting " + player.getName() + " → " + target);

            com.google.common.io.ByteArrayDataOutput out =
                    com.google.common.io.ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(target);
            player.sendPluginMessage(this, "BungeeCord", out.toByteArray());
            event.setCancelled(true);
            return;
        }
    }
}
