package io.github.notgamerthuz.thuzmenu;

import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** The optional menu item in the hotbar (right-click or Shift+F opens the menu). */
public final class HotbarItem implements Listener {

    private final ThuzMenu plugin;
    private final MenuManager menus;
    private final NamespacedKey key;

    public HotbarItem(ThuzMenu plugin, MenuManager menus) {
        this.plugin = plugin;
        this.menus = menus;
        this.key = new NamespacedKey(plugin, "menu_item");
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private boolean enabled() {
        return cfg().getBoolean("menu-item.enabled", true);
    }

    public ItemStack create(Player player) {
        ConfigurationSection section = cfg().getConfigurationSection("menu-item");
        if (section == null) {
            section = new MemoryConfiguration();
        }
        ItemStack item = ItemSpec.parse(section, "NETHER_STAR").build(new Context(player, Map.of()), null);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean is(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Gives the item if the player does not have it yet. */
    public void give(Player player) {
        if (!enabled() || !player.hasPermission("thuzmenu.use")) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        for (ItemStack stack : inventory.getContents()) {
            if (is(stack)) {
                return;
            }
        }
        ItemStack item = create(player);
        int slot = cfg().getInt("menu-item.slot", 8);
        ItemStack existing = slot >= 0 && slot < 9 ? inventory.getItem(slot) : null;
        if (slot >= 0 && slot < 9 && (existing == null || existing.getType().isAir())) {
            inventory.setItem(slot, item);
        } else {
            inventory.addItem(item); // if the inventory is full the player simply uses /menu
        }
    }

    private boolean protect() {
        return cfg().getBoolean("menu-item.prevent-move", true);
    }

    // ---- giving ----

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (enabled() && cfg().getBoolean("menu-item.give-on-join", true)) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> give(player), 10L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (enabled() && cfg().getBoolean("menu-item.give-on-respawn", true)) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> give(player), 2L);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::is);
    }

    // ---- using ----

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled() || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!cfg().getBoolean("menu-item.open-on-right-click", true) || !is(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("thuzmenu.use")) {
            plugin.send(player, "no-permission");
            return;
        }
        menus.open(player, cfg().getString("menu-item.menu", "main"), MenuManager.Mode.FRESH);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (enabled() && player.isSneaking() && cfg().getBoolean("menu-item.open-with-sneak-swap", true)
                && player.hasPermission("thuzmenu.use")) {
            event.setCancelled(true);
            menus.open(player, cfg().getString("menu-item.menu", "main"), MenuManager.Mode.FRESH);
            return;
        }
        if (protect() && (is(event.getMainHandItem()) || is(event.getOffHandItem()))) {
            event.setCancelled(true);
        }
    }

    // ---- protecting ----

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (protect() && is(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!protect() || event.getView().getTopInventory().getType() == InventoryType.CRAFTING) {
            return; // moving things around your own inventory is fine
        }
        ItemStack hotbar = null;
        if (event.getClick() == ClickType.NUMBER_KEY) {
            hotbar = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
        } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
            hotbar = event.getWhoClicked().getInventory().getItemInOffHand();
        }
        if (is(event.getCurrentItem()) || is(event.getCursor()) || is(hotbar)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (protect() && event.getView().getTopInventory().getType() != InventoryType.CRAFTING
                && is(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }
}
