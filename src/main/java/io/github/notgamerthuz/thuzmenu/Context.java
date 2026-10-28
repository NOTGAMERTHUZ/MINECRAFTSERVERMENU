package io.github.notgamerthuz.thuzmenu;

import java.lang.management.ManagementFactory;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Placeholder context for one player. Built-in placeholders are filled in once,
 * extra values (like {target}) can be added on top.
 */
public final class Context {

    private final Player player;
    private final Map<String, String> vars = new HashMap<>();

    public Context(Player player, Map<String, String> extra) {
        this.player = player;
        fillBuiltins();
        if (extra != null) {
            vars.putAll(extra);
        }
    }

    public Player player() {
        return player;
    }

    private void fillBuiltins() {
        ThuzMenu plugin = JavaPlugin.getPlugin(ThuzMenu.class);

        vars.put("player", player.getName());
        vars.put("displayname", PlainTextComponentSerializer.plainText().serialize(player.displayName()));
        vars.put("server", plugin.getConfig().getString("server-name", "SMP"));

        Location loc = player.getLocation();
        vars.put("world", loc.getWorld() == null ? "?" : loc.getWorld().getName());
        vars.put("x", String.valueOf(loc.getBlockX()));
        vars.put("y", String.valueOf(loc.getBlockY()));
        vars.put("z", String.valueOf(loc.getBlockZ()));

        vars.put("ping", String.valueOf(player.getPing()));
        vars.put("online", String.valueOf(Bukkit.getOnlinePlayers().size()));
        vars.put("max", String.valueOf(Bukkit.getMaxPlayers()));

        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        vars.put("tps", one(tps));
        vars.put("tps_color", tps >= 18.0 ? "<green>" : tps >= 15.0 ? "<yellow>" : "<red>");
        vars.put("mspt", one(Bukkit.getAverageTickTime()));

        Runtime rt = Runtime.getRuntime();
        long max = rt.maxMemory() / (1024 * 1024);
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        vars.put("ram_used", String.valueOf(used));
        vars.put("ram_max", String.valueOf(max));
        vars.put("ram_percent", String.valueOf(max <= 0 ? 0 : used * 100 / max));
        vars.put("uptime", Text.duration(ManagementFactory.getRuntimeMXBean().getUptime() / 1000));

        vars.put("playtime", Text.duration(stat("PLAY_ONE_MINUTE", "PLAY_TIME") / 20L));
        vars.put("deaths", String.valueOf(stat("DEATHS")));
        vars.put("kills", String.valueOf(stat("PLAYER_KILLS")));
        vars.put("mob_kills", String.valueOf(stat("MOB_KILLS")));

        vars.put("health", String.format(Locale.US, "%.0f", player.getHealth()));
        vars.put("food", String.valueOf(player.getFoodLevel()));
        vars.put("level", String.valueOf(player.getLevel()));
        String mode = player.getGameMode().name().toLowerCase(Locale.ROOT);
        vars.put("gamemode", Character.toUpperCase(mode.charAt(0)) + mode.substring(1));

        vars.put("time", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        vars.put("date", LocalDate.now().toString());
    }

    private int stat(String... names) {
        for (String name : names) {
            try {
                return player.getStatistic(Statistic.valueOf(name));
            } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
                // try the next name
            }
        }
        return 0;
    }

    private static String one(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    /** Replaces {placeholders} (and %papi% placeholders when PlaceholderAPI is installed). */
    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String out = text;
        if (out.indexOf('{') >= 0) {
            for (Map.Entry<String, String> e : vars.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return Text.papi(player, out);
    }

    /** Parsed text for titles and chat. */
    public Component text(String input) {
        return Text.parse(apply(input));
    }

    /** Parsed text for item names and lore (no default italics). */
    public Component item(String input) {
        return Text.item(apply(input));
    }
}
