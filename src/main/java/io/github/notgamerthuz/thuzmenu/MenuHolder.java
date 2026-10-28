package io.github.notgamerthuz.thuzmenu;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks an inventory as a ThuzMenu menu and remembers what each slot does. */
public final class MenuHolder implements InventoryHolder {

    /** What happens when a slot is clicked. */
    public record Clickable(ButtonDef button, Map<String, String> extras) {
    }

    private final MenuDef def;
    private final Player viewer;
    private final Map<Integer, Clickable> clickables = new HashMap<>();
    private Inventory inventory;
    private int page;

    public MenuHolder(MenuDef def, Player viewer) {
        this.def = def;
        this.viewer = viewer;
    }

    public MenuDef def() {
        return def;
    }

    public Player viewer() {
        return viewer;
    }

    public Map<Integer, Clickable> clickables() {
        return clickables;
    }

    public int page() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
