package io.github.notgamerthuz.thuzmenu;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/** Loads menus, opens and renders them, and handles clicks. */
public final class MenuManager implements Listener {

    public enum Mode {
        /** Opened by a command or item: forget the back-history. */
        FRESH,
        /** Opened from another menu: remember it so [return] works. */
        PUSH,
        /** Opened by [return]: keep the history as it is. */
        KEEP
    }

    private static final String[] DEFAULT_MENUS = {
            "main", "travel", "homes", "players", "utilities", "status", "stats", "info", "admin"
    };

    private final ThuzMenu plugin;
    private final Map<String, MenuDef> menus = new LinkedHashMap<>();
    private final Map<UUID, Deque<String>> history = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private ActionRunner actions;
    private BukkitTask liveTask;

    public MenuManager(ThuzMenu plugin) {
        this.plugin = plugin;
    }

    void setActionRunner(ActionRunner actions) {
        this.actions = actions;
    }

    // ---- loading ----

    public void loadAll() {
        File dir = new File(plugin.getDataFolder(), "menus");
        if (!dir.exists()) {
            dir.mkdirs();
            for (String name : DEFAULT_MENUS) {
                plugin.saveResource("menus/" + name + ".yml", false);
            }
        }

        Map<String, MenuDef> loaded = new LinkedHashMap<>();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files);
            for (File file : files) {
                String fileName = file.getName();
                String id = fileName.substring(0, fileName.length() - 4).toLowerCase(Locale.ROOT);
                try {
                    loaded.put(id, MenuDef.parse(id, YamlConfiguration.loadConfiguration(file)));
                } catch (Exception ex) {
                    plugin.getLogger().warning("Could not load menu " + fileName + ": " + ex.getMessage());
                }
            }
        }
        menus.clear();
        menus.putAll(loaded);
        plugin.getLogger().info("Loaded " + menus.size() + " menu(s).");
    }

    public int count() {
        return menus.size();
    }

    public Collection<String> ids() {
        return menus.keySet();
    }

    public MenuDef get(String id) {
        return menus.get(id.toLowerCase(Locale.ROOT));
    }

    public void startLiveTask() {
        if (liveTask != null) {
            liveTask.cancel();
        }
        long period = Math.max(5L, plugin.getConfig().getLong("update-interval-ticks", 20L));
        liveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                MenuHolder holder = currentHolder(p);
                if (holder != null && holder.def().live()) {
                    render(holder);
                }
            }
        }, period, period);
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (currentHolder(p) != null) {
                p.closeInventory();
            }
        }
        history.clear();
    }

    // ---- opening ----

    public MenuHolder currentHolder(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top.getHolder() instanceof MenuHolder holder ? holder : null;
    }

    public boolean open(Player player, String id, Mode mode) {
        MenuDef def = get(id);
        if (def == null) {
            plugin.send(player, "menu-not-found", Map.of("menu", id));
            return false;
        }
        if (!def.permission().isEmpty() && !player.hasPermission(def.permission())) {
            plugin.send(player, "no-permission");
            Text.playSound(player, plugin.getConfig().getString("sounds.deny", ""));
            return false;
        }

        Deque<String> trail = history.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        MenuHolder current = currentHolder(player);
        switch (mode) {
            case FRESH -> trail.clear();
            case PUSH -> {
                if (current != null) {
                    trail.push(current.def().id());
                }
            }
            case KEEP -> {
                // history stays as it is
            }
        }

        MenuHolder holder = new MenuHolder(def, player);
        Context ctx = new Context(player, Map.of());
        Inventory inventory = Bukkit.createInventory(holder, def.rows() * 9, ctx.text(def.title()));
        holder.setInventory(inventory);
        render(holder);
        player.openInventory(inventory);
        Text.playSound(player, plugin.getConfig().getString("sounds.open", ""));
        return true;
    }

    public void goBack(Player player) {
        Deque<String> trail = history.get(player.getUniqueId());
        if (trail == null || trail.isEmpty()) {
            player.closeInventory();
            return;
        }
        String id = trail.pop();
        if (!open(player, id, Mode.KEEP)) {
            player.closeInventory();
        }
    }

    public void changePage(Player player, String argument) {
        MenuHolder holder = currentHolder(player);
        if (holder == null) {
            return;
        }
        String arg = argument.trim().toLowerCase(Locale.ROOT);
        switch (arg) {
            case "next" -> holder.setPage(holder.page() + 1);
            case "prev", "previous" -> holder.setPage(holder.page() - 1);
            default -> {
                try {
                    holder.setPage(Integer.parseInt(arg) - 1);
                } catch (NumberFormatException ignored) {
                    return;
                }
            }
        }
        render(holder);
    }

    public void refresh(Player player) {
        MenuHolder holder = currentHolder(player);
        if (holder != null) {
            render(holder);
        }
    }

    // ---- rendering ----

    private static List<Integer> contentSlots(int rows) {
        List<Integer> slots = new ArrayList<>();
        for (int r = 1; r < rows - 1; r++) {
            for (int c = 1; c <= 7; c++) {
                slots.add(r * 9 + c);
            }
        }
        return slots;
    }

    private void render(MenuHolder holder) {
        Player player = holder.viewer();
        MenuDef def = holder.def();
        Inventory inventory = holder.getInventory();
        int rows = def.rows();

        inventory.clear();
        holder.clickables().clear();

        Map<String, String> pageVars = new HashMap<>();
        List<Player> listed = new ArrayList<>();
        List<Integer> content = List.of();
        int pages = 1;

        if (def.type() == MenuDef.Type.PLAYERS && def.playerButton() != null) {
            content = contentSlots(rows);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (def.excludeSelf() && p.equals(player)) {
                    continue;
                }
                if (!player.canSee(p)) {
                    continue; // hidden by a vanish plugin
                }
                listed.add(p);
            }
            listed.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
            if (!content.isEmpty()) {
                pages = Math.max(1, (int) Math.ceil(listed.size() / (double) content.size()));
            }
            holder.setPage(Math.max(0, Math.min(pages - 1, holder.page())));
        }
        pageVars.put("page", String.valueOf(holder.page() + 1));
        pages = Math.max(1, pages);
        pageVars.put("pages", String.valueOf(pages));

        Context base = new Context(player, pageVars);

        // 1) filler
        if (def.fillItem() != null && !def.fillMode().equals("none")) {
            ItemStack filler = def.fillItem().build(base, null);
            int size = rows * 9;
            for (int slot = 0; slot < size; slot++) {
                int row = slot / 9;
                int col = slot % 9;
                boolean edge = row == 0 || row == rows - 1 || col == 0 || col == 8;
                if (def.fillMode().equals("all") || edge) {
                    inventory.setItem(slot, filler.clone());
                }
            }
        }

        // 2) player list
        if (!content.isEmpty()) {
            int start = holder.page() * content.size();
            for (int i = 0; i < content.size() && start + i < listed.size(); i++) {
                Player target = listed.get(start + i);
                Map<String, String> extras = new HashMap<>(pageVars);
                extras.put("target", target.getName());
                extras.put("target_display", target.getName());
                extras.put("target_ping", String.valueOf(target.getPing()));
                extras.put("target_world", target.getWorld().getName());
                Context targetCtx = new Context(player, extras);
                int slot = content.get(i);
                inventory.setItem(slot, def.playerButton().item().build(targetCtx, target));
                holder.clickables().put(slot, new MenuHolder.Clickable(def.playerButton(), extras));
            }
        }

        // 3) buttons
        for (ButtonDef button : def.buttons()) {
            if (!button.viewPermission().isEmpty() && !player.hasPermission(button.viewPermission())) {
                continue;
            }
            ItemStack stack = button.item().build(base, null);
            for (int slot : button.slots()) {
                inventory.setItem(slot, stack.clone());
                holder.clickables().put(slot, new MenuHolder.Clickable(button, pageVars));
            }
        }
    }

    // ---- clicking ----

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(top)) {
            return;
        }
        MenuHolder.Clickable clickable = holder.clickables().get(event.getSlot());
        if (clickable == null) {
            return;
        }
        handleClick(player, holder, clickable, event.getClick());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // If another menu opens right away the history must survive.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (currentHolder(player) == null) {
                history.remove(player.getUniqueId());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        history.remove(event.getPlayer().getUniqueId());
        cooldowns.remove(event.getPlayer().getUniqueId());
    }

    private void handleClick(Player player, MenuHolder holder, MenuHolder.Clickable clickable, ClickType click) {
        ButtonDef button = clickable.button();
        List<String> list = button.forClick(click);
        if (list.isEmpty()) {
            return;
        }

        if (!button.permission().isEmpty() && !player.hasPermission(button.permission())) {
            plugin.send(player, "no-permission");
            Text.playSound(player, plugin.getConfig().getString("sounds.deny", ""));
            return;
        }

        if (button.cooldown() > 0 && !player.hasPermission("thuzmenu.bypass.cooldown")) {
            Map<String, Long> map = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            String key = holder.def().id() + ":" + button.id();
            long now = System.currentTimeMillis();
            Long until = map.get(key);
            if (until != null && until > now) {
                long seconds = (until - now + 999) / 1000;
                plugin.send(player, "cooldown", Map.of("seconds", String.valueOf(seconds)));
                Text.playSound(player, plugin.getConfig().getString("sounds.deny", ""));
                return;
            }
            map.put(key, now + button.cooldown() * 1000L);
        }

        Text.playSound(player, plugin.getConfig().getString("sounds.click", ""));

        boolean keepOpen = !button.closeOnClick() || hasTransition(list);
        Context ctx = new Context(player, clickable.extras());
        // Run next tick so closing/opening inventories is safe inside a click event.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!keepOpen) {
                player.closeInventory();
            }
            actions.run(player, list, ctx);
        });
    }

    private static boolean hasTransition(List<String> list) {
        for (String raw : list) {
            String s = raw.trim().toLowerCase(Locale.ROOT);
            if (s.startsWith("[menu]") || s.startsWith("[return]") || s.startsWith("[page]")
                    || s.startsWith("[refresh]")) {
                return true;
            }
        }
        return false;
    }
}
