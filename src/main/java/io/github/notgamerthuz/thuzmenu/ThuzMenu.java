package io.github.notgamerthuz.thuzmenu;

import java.util.Map;
import java.util.Objects;

import net.kyori.adventure.text.Component;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ThuzMenu - a fully configurable SMP menu.
 * Forged by GAMERTHUZ - https://github.com/NOTGAMERTHUZ
 */
public final class ThuzMenu extends JavaPlugin {

    private MenuManager menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Text.initPlaceholderApi();

        TeleportService teleports = new TeleportService(this);
        menus = new MenuManager(this);
        menus.setActionRunner(new ActionRunner(this, menus, teleports));
        HotbarItem hotbar = new HotbarItem(this, menus);

        menus.loadAll();
        menus.startLiveTask();

        getServer().getPluginManager().registerEvents(menus, this);
        getServer().getPluginManager().registerEvents(teleports, this);
        getServer().getPluginManager().registerEvents(hotbar, this);

        MenuCommand command = new MenuCommand(this, menus, hotbar);
        for (String name : new String[] {"menu", "thuzmenu"}) {
            PluginCommand pluginCommand = Objects.requireNonNull(getCommand(name), name + " missing from plugin.yml");
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getLogger().info("ThuzMenu v" + getPluginMeta().getVersion() + " enabled - forged by GAMERTHUZ"
                + " (https://github.com/NOTGAMERTHUZ)");
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.closeAll();
        }
    }

    /** Reloads config.yml and every menu file. */
    public void reload() {
        reloadConfig();
        Text.initPlaceholderApi();
        menus.closeAll();
        menus.loadAll();
        menus.startLiveTask();
    }

    // ---- messages (config.yml -> messages:) ----

    private String template(String key, Map<String, String> vars) {
        String raw = getConfig().getString("messages." + key);
        if (raw == null) {
            raw = "<red>Missing message: " + key;
        }
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            raw = raw.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return raw;
    }

    /** Message without prefix (for action bars). */
    public Component raw(String key, Map<String, String> vars) {
        return Text.parse(template(key, vars));
    }

    public void send(CommandSender to, String key) {
        send(to, key, Map.of());
    }

    public void send(CommandSender to, String key, Map<String, String> vars) {
        to.sendMessage(Text.parse(getConfig().getString("prefix", "") + template(key, vars)));
    }
}
