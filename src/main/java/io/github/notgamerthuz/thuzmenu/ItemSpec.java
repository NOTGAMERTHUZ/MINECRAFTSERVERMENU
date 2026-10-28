package io.github.notgamerthuz.thuzmenu;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/** The look of an item, as written in a menu file. */
public record ItemSpec(String material, String name, List<String> lore, int amount, boolean glow, String head) {

    public static ItemSpec parse(ConfigurationSection s, String defaultMaterial) {
        return new ItemSpec(
                s.getString("material", defaultMaterial),
                s.getString("name", ""),
                Cfg.list(s, "lore"),
                Math.max(1, s.getInt("amount", 1)),
                s.getBoolean("glow", false),
                s.getString("head", ""));
    }

    /** Builds the item. headOwner (optional) is used for player heads in player lists. */
    public ItemStack build(Context ctx, Player headOwner) {
        Material mat = Material.matchMaterial(material);
        if (mat == null || mat.isAir() || !mat.isItem()) {
            mat = Material.BARRIER; // unknown material in the config
        }
        ItemStack stack = new ItemStack(mat, Math.min(amount, 64));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        if (!name.isEmpty()) {
            meta.displayName(ctx.item(name));
        }
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(ctx.item(line));
            }
            meta.lore(lines);
        }
        meta.addItemFlags(ItemFlag.values());
        if (glow) {
            meta.setEnchantmentGlintOverride(true);
        }

        if (meta instanceof SkullMeta skull) {
            OfflinePlayer owner = headOwner;
            if (owner == null && !head.isEmpty()) {
                String wanted = ctx.apply(head);
                owner = Bukkit.getPlayerExact(wanted);
                if (owner == null) {
                    owner = Bukkit.getOfflinePlayerIfCached(wanted); // never blocks on a web lookup
                }
            }
            if (owner != null) {
                skull.setOwningPlayer(owner);
            }
        }

        stack.setItemMeta(meta);
        return stack;
    }
}
