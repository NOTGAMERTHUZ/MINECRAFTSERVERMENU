package io.github.notgamerthuz.thuzmenu;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.inventory.ClickType;

/** One button of a menu, as written in a menu file. */
public record ButtonDef(
        String id,
        List<Integer> slots,
        ItemSpec item,
        List<String> actions,
        List<String> leftActions,
        List<String> rightActions,
        List<String> shiftActions,
        String permission,
        String viewPermission,
        int cooldown,
        boolean closeOnClick) {

    public static ButtonDef parse(String id, ConfigurationSection s, int size, String defaultMaterial) {
        Object rawSlots = s.contains("slots") ? s.get("slots") : s.get("slot");
        return new ButtonDef(
                id,
                parseSlots(rawSlots, size),
                ItemSpec.parse(s, defaultMaterial),
                Cfg.list(s, "actions"),
                Cfg.list(s, "left-actions"),
                Cfg.list(s, "right-actions"),
                Cfg.list(s, "shift-actions"),
                s.getString("permission", ""),
                s.getString("view-permission", ""),
                Math.max(0, s.getInt("cooldown", 0)),
                s.getBoolean("close-on-click", true));
    }

    /** The actions for this click; falls back to the general "actions" list. */
    public List<String> forClick(ClickType click) {
        List<String> specific = switch (click) {
            case SHIFT_LEFT -> !shiftActions.isEmpty() ? shiftActions : leftActions;
            case SHIFT_RIGHT -> !shiftActions.isEmpty() ? shiftActions : rightActions;
            case LEFT -> leftActions;
            case RIGHT -> rightActions;
            default -> List.of();
        };
        return specific.isEmpty() ? actions : specific;
    }

    /** Accepts 11, "0-8", "1,3,5" or a YAML list of those. */
    public static List<Integer> parseSlots(Object raw, int size) {
        List<Integer> out = new ArrayList<>();
        collect(raw, size, out);
        return out;
    }

    private static void collect(Object raw, int size, List<Integer> out) {
        if (raw == null) {
            return;
        }
        if (raw instanceof Number number) {
            add(number.intValue(), size, out);
        } else if (raw instanceof List<?> list) {
            for (Object o : list) {
                collect(o, size, out);
            }
        } else {
            for (String part : raw.toString().split(",")) {
                String p = part.trim();
                if (p.isEmpty()) {
                    continue;
                }
                try {
                    int dash = p.indexOf('-', 1);
                    if (dash > 0) {
                        int a = Integer.parseInt(p.substring(0, dash).trim());
                        int b = Integer.parseInt(p.substring(dash + 1).trim());
                        for (int i = Math.min(a, b); i <= Math.max(a, b); i++) {
                            add(i, size, out);
                        }
                    } else {
                        add(Integer.parseInt(p), size, out);
                    }
                } catch (NumberFormatException ignored) {
                    // skip invalid slot text
                }
            }
        }
    }

    private static void add(int slot, int size, List<Integer> out) {
        if (slot >= 0 && slot < size) {
            out.add(slot);
        }
    }
}
