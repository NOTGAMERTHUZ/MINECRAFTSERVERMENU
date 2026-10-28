package io.github.notgamerthuz.thuzmenu;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Built-in [spawn] and [back] actions with warm-up, cooldown, cancel-on-move
 * and a safety check for the death location.
 */
public final class TeleportService implements Listener {

    private record DeathPoint(Location location, long time) {
    }

    private final ThuzMenu plugin;
    private final Map<UUID, DeathPoint> deaths = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, BukkitTask> pending = new HashMap<>();

    public TeleportService(ThuzMenu plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        deaths.put(p.getUniqueId(), new DeathPoint(p.getLocation().clone(), System.currentTimeMillis()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPending(event.getPlayer().getUniqueId());
    }

    public void spawn(Player player) {
        if (!player.hasPermission("thuzmenu.spawn")) {
            plugin.send(player, "no-permission");
            return;
        }
        teleport(player, resolveSpawn(), "teleport-spawn");
    }

    public void back(Player player) {
        if (!player.hasPermission("thuzmenu.back")) {
            plugin.send(player, "no-permission");
            return;
        }
        DeathPoint point = deaths.get(player.getUniqueId());
        long expireMillis = plugin.getConfig().getLong("teleport.back-expire-minutes", 30L) * 60_000L;
        if (point == null || point.location().getWorld() == null
                || (expireMillis > 0 && System.currentTimeMillis() - point.time() > expireMillis)) {
            plugin.send(player, "no-death-point");
            return;
        }
        teleport(player, safe(point.location().clone()), "teleport-back");
    }

    // ---- teleporting ----

    private void teleport(Player player, Location destination, String doneKey) {
        UUID id = player.getUniqueId();
        if (pending.containsKey(id)) {
            plugin.send(player, "teleport-pending");
            return;
        }

        long now = System.currentTimeMillis();
        Long until = cooldownUntil.get(id);
        if (until != null && until > now && !player.hasPermission("thuzmenu.bypass.cooldown")) {
            plugin.send(player, "teleport-cooldown", Map.of("seconds", String.valueOf((until - now + 999) / 1000)));
            return;
        }

        int warmup = player.hasPermission("thuzmenu.bypass.warmup")
                ? 0 : plugin.getConfig().getInt("teleport.warmup-seconds", 3);
        if (warmup <= 0) {
            go(player, destination, doneKey);
            return;
        }

        boolean cancelOnMove = plugin.getConfig().getBoolean("teleport.cancel-on-move", true);
        Location origin = player.getLocation().clone();
        int[] ticksLeft = {warmup * 20};

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || player.isDead()) {
                cancelPending(id);
                return;
            }
            if (cancelOnMove && moved(origin, player.getLocation())) {
                cancelPending(id);
                plugin.send(player, "teleport-cancelled");
                return;
            }
            if (ticksLeft[0] > 0 && ticksLeft[0] % 20 == 0) {
                player.sendActionBar(plugin.raw("teleport-warmup", Map.of("seconds", String.valueOf(ticksLeft[0] / 20))));
            }
            ticksLeft[0] -= 5;
            if (ticksLeft[0] <= 0) {
                cancelPending(id);
                go(player, destination, doneKey);
            }
        }, 0L, 5L);
        pending.put(id, task);
    }

    private void go(Player player, Location destination, String doneKey) {
        player.teleportAsync(destination).thenAccept(success -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!success) {
                return;
            }
            int cooldown = plugin.getConfig().getInt("teleport.cooldown-seconds", 10);
            if (cooldown > 0) {
                cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + cooldown * 1000L);
            }
            plugin.send(player, doneKey);
            Text.playSound(player, plugin.getConfig().getString("sounds.teleport", ""));
        }));
    }

    private void cancelPending(UUID id) {
        BukkitTask task = pending.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    private static boolean moved(Location a, Location b) {
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return true;
        }
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz > 0.09;
    }

    // ---- locations ----

    private Location resolveSpawn() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("teleport.spawn");
        if (section != null) {
            String worldName = section.getString("world", "");
            if (!worldName.isEmpty()) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    return new Location(world, section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                            (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
                }
                plugin.getLogger().warning("teleport.spawn.world '" + worldName + "' not found, using the main world spawn.");
            }
        }
        return Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5, 0, 0.5);
    }

    /** Looks up to 8 blocks upward for a spot with room to stand and no lava or fire below. */
    private Location safe(Location loc) {
        World world = loc.getWorld();
        if (world == null || !plugin.getConfig().getBoolean("teleport.safe-back", true)) {
            return loc;
        }
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 2;
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        int startY = Math.max(minY, Math.min(maxY, loc.getBlockY()));

        for (int dy = 0; dy <= 8; dy++) {
            int y = startY + dy;
            if (y > maxY) {
                break;
            }
            Block feet = world.getBlockAt(x, y, z);
            Block head = feet.getRelative(0, 1, 0);
            Block below = feet.getRelative(0, -1, 0);
            if (feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid() && !hazard(below)) {
                Location result = loc.clone();
                result.setX(x + 0.5);
                result.setY(y);
                result.setZ(z + 0.5);
                return result;
            }
        }
        return loc;
    }

    private static boolean hazard(Block block) {
        Material type = block.getType();
        return type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE
                || type == Material.MAGMA_BLOCK || type == Material.CACTUS;
    }
}
