package io.github.notgamerthuz.thuzmenu;

import java.lang.reflect.Method;
import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Text parsing (MiniMessage + legacy &-codes), sounds and optional PlaceholderAPI. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static Method papiMethod;

    private Text() {
    }

    static void initPlaceholderApi() {
        papiMethod = null;
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            Class<?> clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            papiMethod = clazz.getMethod("setPlaceholders", OfflinePlayer.class, String.class);
        } catch (ReflectiveOperationException ex) {
            papiMethod = null;
        }
    }

    static String papi(Player player, String text) {
        if (papiMethod == null || player == null || text.indexOf('%') < 0) {
            return text;
        }
        try {
            Object result = papiMethod.invoke(null, player, text);
            return result == null ? text : result.toString();
        } catch (ReflectiveOperationException ex) {
            return text;
        }
    }

    /** Parses MiniMessage; legacy &-codes (&a, &l, &#rrggbb) are converted first. */
    public static Component parse(String input) {
        if (input == null) {
            return Component.empty();
        }
        return MM.deserialize(legacyToMini(input));
    }

    /** Like parse, but italics are off unless the text asks for them (for item names and lore). */
    public static Component item(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Plays a sound given as "sound.key [volume] [pitch]". Empty spec does nothing. */
    public static void playSound(Player player, String spec) {
        if (player == null || spec == null || spec.isBlank()) {
            return;
        }
        String[] parts = spec.trim().split("\\s+");
        float volume = 1.0f;
        float pitch = 1.0f;
        try {
            if (parts.length > 1) {
                volume = Float.parseFloat(parts[1]);
            }
            if (parts.length > 2) {
                pitch = Float.parseFloat(parts[2]);
            }
        } catch (NumberFormatException ignored) {
            // keep defaults
        }
        player.playSound(player.getLocation(), parts[0], volume, pitch);
    }

    static String legacyToMini(String s) {
        if (s.indexOf('&') < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                if (next == '#' && i + 8 <= s.length() && isHex(s, i + 2, i + 8)) {
                    out.append("<#").append(s, i + 2, i + 8).append('>');
                    i += 7;
                    continue;
                }
                String tag = legacyTag(next);
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isHex(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String legacyTag(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0' -> "<black>";
            case '1' -> "<dark_blue>";
            case '2' -> "<dark_green>";
            case '3' -> "<dark_aqua>";
            case '4' -> "<dark_red>";
            case '5' -> "<dark_purple>";
            case '6' -> "<gold>";
            case '7' -> "<gray>";
            case '8' -> "<dark_gray>";
            case '9' -> "<blue>";
            case 'a' -> "<green>";
            case 'b' -> "<aqua>";
            case 'c' -> "<red>";
            case 'd' -> "<light_purple>";
            case 'e' -> "<yellow>";
            case 'f' -> "<white>";
            case 'l' -> "<bold>";
            case 'o' -> "<italic>";
            case 'n' -> "<underlined>";
            case 'm' -> "<strikethrough>";
            case 'k' -> "<obfuscated>";
            case 'r' -> "<reset>";
            default -> null;
        };
    }

    /** Formats seconds as "2d 3h", "4h 12m" or "3m 20s". */
    public static String duration(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        long days = s / 86400;
        long hours = (s % 86400) / 3600;
        long minutes = (s % 3600) / 60;
        long seconds = s % 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m " + seconds + "s";
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
