package io.github.aura6.supersmashlegends.attribute;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import io.github.aura6.supersmashlegends.SuperSmashLegends;
import io.github.aura6.supersmashlegends.kit.Kit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

public abstract class Bow extends PassiveAbility {
    protected int ticksCharging = 0;
    private int bowSlot;

    public Bow(SuperSmashLegends plugin, Section config, Kit kit) {
        super(plugin, config, kit);
    }

    @Override
    public String getUseType() {
        return "Bow";
    }

    public void onStart() {}

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getPlayer() != this.player) return;
        if (!event.hasItem() || event.getItem().getType() != Material.BOW) return;
        if (!event.getPlayer().getInventory().contains(Material.ARROW)) return;
        if (!event.getAction().name().contains("RIGHT")) return;
        if (this.ticksCharging > 0) return;

        this.ticksCharging = 1;
        this.bowSlot = event.getPlayer().getInventory().getHeldItemSlot();
        this.onStart();
    }

    public void onChargeTick() {}

    @Override
    public void run() {
        super.run();

        if (this.ticksCharging > 0) {
            this.onChargeTick();
            this.ticksCharging++;
        }
    }

    public void onFinish() {}

    public void finish() {
        this.ticksCharging = 0;
        this.onFinish();
    }

    public void onShot(double force) {}

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (event.getEntity() != this.player) return;

        event.getProjectile().remove();
        this.onShot(event.getForce());
        this.finish();
    }

    @EventHandler
    public void onSwitchItemSlot(PlayerItemHeldEvent event) {
        if (event.getPlayer() == this.player && this.ticksCharging > 0 && event.getPreviousSlot() == this.bowSlot) {
            this.finish();
        }
    }
}
