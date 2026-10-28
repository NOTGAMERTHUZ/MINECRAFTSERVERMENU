package io.github.notgamerthuz.thuzmenu;

import java.util.List;

import org.bukkit.configuration.ConfigurationSection;

/** Small YAML helpers. */
final class Cfg {

    private Cfg() {
    }

    /** Reads a list of strings; a single string value is treated as a one-item list. */
    static List<String> list(ConfigurationSection section, String key) {
        if (section == null) {
            return List.of();
        }
        if (section.isList(key)) {
            return section.getStringList(key);
        }
        if (section.isString(key)) {
            return List.of(section.getString(key, ""));
        }
        return List.of();
    }
}
