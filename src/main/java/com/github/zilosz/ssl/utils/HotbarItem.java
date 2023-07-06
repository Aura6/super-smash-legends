package com.github.zilosz.ssl.utils;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public class HotbarItem implements Listener {
    @Getter private final Player player;
    @Getter private final ItemStack itemStack;
    @Getter private final int slot;

    @Setter private Consumer<PlayerInteractEvent> action;

    private Integer lastTick;
    private Action lastAction;

    public HotbarItem(Player player, ItemStack itemStack, int slot) {
        this.player = player;
        this.itemStack = itemStack;
        this.slot = slot;
    }

    public void register(Plugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.show();
    }

    public void show() {
        this.player.getInventory().setItem(this.slot, this.itemStack);
    }

    public void destroy() {
        HandlerList.unregisterAll(this);
        this.hide();
    }

    public void hide() {
        this.player.getInventory().setItem(this.slot, null);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getPlayer() != this.player) return;
        if (event.getPlayer().getInventory().getHeldItemSlot() != this.slot) return;

        int tick = (int) this.player.getWorld().getFullTime();
        Action action = event.getAction();

        boolean sameTick = this.lastTick != null && tick == this.lastTick;
        boolean sameAction = this.lastAction == Action.RIGHT_CLICK_BLOCK && action == Action.RIGHT_CLICK_AIR;

        if (sameTick && sameAction) return;

        this.lastTick = tick;
        this.lastAction = action;

        if (this.action != null) {
            this.action.accept(event);
        }

        if (event.getItem() == null) return;

        String name = event.getItem().getType().name();

        if (name.contains("BOOTS") || name.contains("CHESTPLATE") || name.contains("LEGGINGS") || name.contains("HELMET")) {
            event.setCancelled(true);
            event.getPlayer().updateInventory();
        }
    }
}
