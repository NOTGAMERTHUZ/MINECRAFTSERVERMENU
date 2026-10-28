package io.github.notgamerthuz.thuzmenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** Handles /menu (for everyone) and /thuzmenu (admin tools). */
public final class MenuCommand implements CommandExecutor, TabCompleter {

    private final ThuzMenu plugin;
    private final MenuManager menus;
    private final HotbarItem hotbar;

    public MenuCommand(ThuzMenu plugin, MenuManager menus, HotbarItem hotbar) {
        this.plugin = plugin;
        this.menus = menus;
        this.hotbar = hotbar;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("menu")) {
            openMenu(sender, args);
        } else {
            admin(sender, args);
        }
        return true;
    }

    private void openMenu(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "player-only");
            return;
        }
        if (!player.hasPermission("thuzmenu.use")) {
            plugin.send(player, "no-permission");
            return;
        }
        String id = args.length > 0 ? args[0] : plugin.getConfig().getString("default-menu", "main");
        menus.open(player, id, MenuManager.Mode.FRESH);
    }

    private void admin(CommandSender sender, String[] args) {
        String sub = args.length == 0 ? "about" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("about")) {
            about(sender);
            return;
        }
        if (!sender.hasPermission("thuzmenu.admin")) {
            plugin.send(sender, "no-permission");
            return;
        }
        switch (sub) {
            case "reload" -> {
                plugin.reload();
                plugin.send(sender, "reloaded", Map.of("count", String.valueOf(menus.count())));
            }
            case "list" -> {
                plugin.send(sender, "menu-list", Map.of("menus", String.join(", ", menus.ids())));
            }
            case "open" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.parse("<gray>Usage: <white>/thuzmenu open <menu> [player]"));
                    return;
                }
                Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                        : sender instanceof Player p ? p : null;
                if (target == null) {
                    plugin.send(sender, "player-not-found");
                    return;
                }
                menus.open(target, args[1], MenuManager.Mode.FRESH);
            }
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(Text.parse("<gray>Usage: <white>/thuzmenu give <player>"));
                    return;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    plugin.send(sender, "player-not-found");
                    return;
                }
                hotbar.give(target);
                plugin.send(sender, "item-given", Map.of("player", target.getName()));
            }
            default -> sender.sendMessage(Text.parse("<gray>Usage: <white>/thuzmenu <about|reload|list|open|give>"));
        }
    }

    private void about(CommandSender sender) {
        sender.sendMessage(Text.parse(""));
        sender.sendMessage(Text.parse("  <gradient:#00e5ff:#7c4dff><bold>ThuzMenu</bold></gradient> <dark_gray>v"
                + plugin.getPluginMeta().getVersion()));
        sender.sendMessage(Text.parse("  <gray>Forged by <gradient:#00e5ff:#7c4dff><bold>GAMERTHUZ</bold></gradient>"));
        sender.sendMessage(Text.parse("  <click:open_url:'https://github.com/NOTGAMERTHUZ'><aqua>"
                + "https://github.com/NOTGAMERTHUZ</aqua></click>"));
        sender.sendMessage(Text.parse(""));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean isMenu = command.getName().equalsIgnoreCase("menu");

        if (isMenu) {
            if (args.length == 1) {
                for (String id : menus.ids()) {
                    MenuDef def = menus.get(id);
                    if (def != null && (def.permission().isEmpty() || sender.hasPermission(def.permission()))) {
                        out.add(id);
                    }
                }
            }
        } else if (sender.hasPermission("thuzmenu.admin")) {
            if (args.length == 1) {
                out.addAll(List.of("about", "reload", "list", "open", "give"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("open")) {
                out.addAll(menus.ids());
            } else if ((args.length == 3 && args[0].equalsIgnoreCase("open"))
                    || (args.length == 2 && args[0].equalsIgnoreCase("give"))) {
                Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
            }
        } else if (args.length == 1) {
            out.add("about");
        }

        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
