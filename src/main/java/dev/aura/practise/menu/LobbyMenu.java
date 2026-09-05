package dev.aura.practise.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.aura.practise.PractiseAuraPlugin;
import dev.aura.practise.mode.ModeHandler;
import dev.aura.practise.mode.ModeRegistry;
import dev.aura.practise.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** 等待区快捷物品 + 模式选择菜单（箱子 GUI，自动列出所有注册模式） */
public class LobbyMenu {

    /** 标记菜单物品用途的 PDC 键 */
    private final NamespacedKey tagKey;
    private final PractiseAuraPlugin plugin;
    private final Map<UUID, ModeHandler> lastGame = new HashMap<>();

    public LobbyMenu(PractiseAuraPlugin plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, "menuitem");
    }

    /** 记住玩家上次玩的模式（用于快速加入/约战默认模式），退出时由监听器清除 */
    public void rememberLastGame(UUID id, ModeHandler mode) {
        if (mode != null) lastGame.put(id, mode);
    }

    public ModeHandler lastGameOf(UUID id) {
        return lastGame.get(id);
    }

    public void forgetLastGame(UUID id) {
        lastGame.remove(id);
    }

    // ------------------------------------------------------------------
    // 物品
    // ------------------------------------------------------------------

    /** 等待区：游戏菜单（slot 0）+ 快速加入上次（slot 1，玩过才有） */
    public void giveLobbyItems(Player p) {
        p.getInventory().clear();
        p.getInventory().setHelmet(null);
        p.getInventory().setChestplate(null);
        p.getInventory().setLeggings(null);
        p.getInventory().setBoots(null);
        p.getInventory().setItem(0, tagged(
                plugin.settings().lobbyItem(),
                "menu",
                Msg.legacy(Msg.text("menu.item-title")),
                Msg.legacy(Msg.text("menu.item-lore-1"))));
        ModeHandler last = lastGame.get(p.getUniqueId());
        if (last != null) {
            p.getInventory().setItem(1, tagged(
                    plugin.settings().rejoinItem(),
                    "rejoin",
                    Msg.legacy(Msg.text("rejoin.title", "mode", last.display())),
                    Msg.legacy(Msg.text("rejoin.lore"))));
        }
    }

    /** 排队中：第 9 格红色染料 = 退出排队 */
    public void giveQueueItem(Player p) {
        p.getInventory().setItem(8, tagged(
                Material.RED_DYE,
                "leave",
                Msg.legacy(Msg.text("queue.dye-title")),
                Msg.legacy(Msg.text("queue.dye-lore"))));
    }

    /** 返回物品携带的用途标记（menu/rejoin/leave），非菜单物品返回 null */
    public String tagOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(tagKey, PersistentDataType.STRING);
    }

    private ItemStack tagged(Material material, String tag, Component name, Component... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        meta.lore(java.util.Arrays.asList(lore));
        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, tag);
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------------
    // 模式选择菜单（自动列出 ModeRegistry 里注册的所有模式）
    // ------------------------------------------------------------------

    public static class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    public void open(Player p) {
        List<ModeHandler> modes = new ArrayList<>(ModeRegistry.all());
        Inventory menu = Bukkit.createInventory(new Holder(), 9,
                Msg.legacy(Msg.text("menu.title")));
        int slot = 0;
        for (ModeHandler mode : modes) {
            if (slot >= 9) break; // 一页最多 9 个，多了以后再做翻页
            menu.setItem(slot++, modeItem(mode));
        }
        p.openInventory(menu);
    }

    private ItemStack modeItem(ModeHandler mode) {
        ItemStack stack = new ItemStack(mode.icon());
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(mode.display(), NamedTextColor.YELLOW));
        meta.lore(java.util.Arrays.asList(
                Msg.legacy(Msg.text("menu.mode-lore-1")),
                Msg.legacy(Msg.text("menu.mode-lore-2"))));
        meta.getPersistentDataContainer().set(tagKey, PersistentDataType.STRING, "mode:" + mode.id());
        stack.setItemMeta(meta);
        return stack;
    }

    /** 菜单内点击：按槽位顺序对应 ModeRegistry 注册顺序，非模式槽位返回 null */
    public ModeHandler modeFromSlot(int slot) {
        List<ModeHandler> modes = new ArrayList<>(ModeRegistry.all());
        if (slot < 0 || slot >= modes.size()) return null;
        return modes.get(slot);
    }
}
