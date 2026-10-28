package io.github.notgamerthuz.thuzmenu;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

/**
 * Runs the action lists from the menu files.
 * Format: "[tag] argument". Without a tag the line is run as a player command.
 */
public final class ActionRunner {

    private final ThuzMenu plugin;
    private final MenuManager menus;
    private final TeleportService teleports;
    private final Set<String> warned = new HashSet<>();

    public ActionRunner(ThuzMenu plugin, MenuManager menus, TeleportService teleports) {
        this.plugin = plugin;
        this.menus = menus;
        this.teleports = teleports;
    }

    public void run(Player player, List<String> actions, Context ctx) {
        runFrom(player, actions, 0, ctx);
    }

    private void runFrom(Player player, List<String> actions, int start, Context ctx) {
        for (int i = start; i < actions.size(); i++) {
            if (!player.isOnline()) {
                return;
            }
            String raw = actions.get(i).trim();
            if (raw.isEmpty()) {
                continue;
            }

            String tag = "player";
            String argument = raw;
            if (raw.startsWith("[")) {
                int end = raw.indexOf(']');
                if (end > 0) {
                    tag = raw.substring(1, end).trim().toLowerCase(Locale.ROOT);
                    argument = raw.substring(end + 1).trim();
                }
            }

            if (tag.equals("delay")) {
                long ticks = 20L;
                try {
                    ticks = Math.max(1L, Long.parseLong(argument));
                } catch (NumberFormatException ignored) {
                    // keep one second
                }
                int next = i + 1;
                Bukkit.getScheduler().runTaskLater(plugin, () -> runFrom(player, actions, next, ctx), ticks);
                return;
            }

            execute(player, tag, ctx.apply(argument));
        }
    }

    private void execute(Player player, String tag, String text) {
        switch (tag) {
            case "player" -> player.performCommand(stripSlash(text));
            case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripSlash(text));
            case "message" -> player.sendMessage(Text.parse(text));
            case "broadcast" -> Bukkit.broadcast(Text.parse(text));
            case "actionbar" -> player.sendActionBar(Text.parse(text));
            case "title" -> {
                String[] parts = text.split(";", 2);
                player.showTitle(Title.title(Text.parse(parts[0]), Text.parse(parts.length > 1 ? parts[1] : "")));
            }
            case "sound" -> Text.playSound(player, text);
            case "close" -> player.closeInventory();
            case "menu" -> menus.open(player, text, MenuManager.Mode.PUSH);
            case "return" -> menus.goBack(player);
            case "page" -> menus.changePage(player, text);
            case "refresh" -> menus.refresh(player);
            case "url" -> sendLink(player, text);
            case "spawn" -> teleports.spawn(player);
            case "back" -> teleports.back(player);
            case "workbench" -> player.openWorkbench(null, true);
            case "enderchest" -> player.openInventory(player.getEnderChest());
            case "trash" -> {
                Component title = Text.parse(plugin.getConfig().getString("messages.trash-title", "<red>Trash"));
                Inventory bin = Bukkit.createInventory(null, 36, title);
                player.openInventory(bin);
            }
            default -> {
                if (warned.add(tag)) {
                    plugin.getLogger().warning("Unknown action tag [" + tag + "] in a menu file.");
                }
            }
        }
    }

    private void sendLink(Player player, String url) {
        String link = url.trim();
        if (!link.startsWith("https://") && !link.startsWith("http://")) {
            plugin.getLogger().warning("[url] action needs a link starting with http:// or https:// (got: " + link + ")");
            return;
        }
        String template = plugin.getConfig().getString("messages.open-link", "<aqua><underlined>Click here to open the link");
        Component message = Text.parse(plugin.getConfig().getString("prefix", "") + template)
                .clickEvent(ClickEvent.openUrl(link))
                .hoverEvent(HoverEvent.showText(Component.text(link)));
        player.sendMessage(message);
    }

    private static String stripSlash(String command) {
        String c = command.trim();
        return c.startsWith("/") ? c.substring(1) : c;
    }
}
