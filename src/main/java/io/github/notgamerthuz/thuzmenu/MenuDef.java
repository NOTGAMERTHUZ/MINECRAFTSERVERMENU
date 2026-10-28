package io.github.notgamerthuz.thuzmenu;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.configuration.ConfigurationSection;

/** A menu as written in a file inside plugins/ThuzMenu/menus/. */
public record MenuDef(
        String id,
        String title,
        int rows,
        Type type,
        boolean live,
        String permission,
        String fillMode,
        ItemSpec fillItem,
        List<ButtonDef> buttons,
        ButtonDef playerButton,
        boolean excludeSelf) {

    public enum Type { NORMAL, PLAYERS }

    public static MenuDef parse(String id, ConfigurationSection cfg) {
        int rows = Math.max(1, Math.min(6, cfg.getInt("rows", 3)));
        int size = rows * 9;

        Type type = Type.NORMAL;
        try {
            type = Type.valueOf(cfg.getString("type", "NORMAL").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // unknown type -> NORMAL
        }

        String fillMode = "none";
        ItemSpec fillItem = null;
        ConfigurationSection fill = cfg.getConfigurationSection("fill");
        if (fill != null) {
            fillMode = fill.getString("mode", "border").toLowerCase(Locale.ROOT);
            ItemSpec spec = ItemSpec.parse(fill, "GRAY_STAINED_GLASS_PANE");
            fillItem = new ItemSpec(spec.material(), " ", List.of(), 1, false, "");
        }

        List<ButtonDef> buttons = new ArrayList<>();
        ConfigurationSection buttonSection = cfg.getConfigurationSection("buttons");
        if (buttonSection != null) {
            for (String key : buttonSection.getKeys(false)) {
                ConfigurationSection b = buttonSection.getConfigurationSection(key);
                if (b != null) {
                    buttons.add(ButtonDef.parse(key, b, size, "STONE"));
                }
            }
        }

        ButtonDef playerButton = null;
        ConfigurationSection pb = cfg.getConfigurationSection("player-button");
        if (pb != null) {
            playerButton = ButtonDef.parse("player", pb, size, "PLAYER_HEAD");
        }

        return new MenuDef(
                id,
                cfg.getString("title", "<white>" + id),
                rows,
                type,
                cfg.getBoolean("live", false),
                cfg.getString("permission", ""),
                fillMode,
                fillItem,
                List.copyOf(buttons),
                playerButton,
                cfg.getBoolean("exclude-self", true));
    }
}
