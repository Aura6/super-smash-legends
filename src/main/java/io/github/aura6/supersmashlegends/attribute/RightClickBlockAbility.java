package io.github.aura6.supersmashlegends.attribute;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import io.github.aura6.supersmashlegends.SuperSmashLegends;
import io.github.aura6.supersmashlegends.kit.Kit;
import org.bukkit.event.player.PlayerInteractEvent;

public abstract class RightClickBlockAbility extends RightClickAbility {

    public RightClickBlockAbility(SuperSmashLegends plugin, Section config, Kit kit) {
        super(plugin, config, kit);
    }

    @Override
    public boolean invalidate(PlayerInteractEvent event) {
        return super.invalidate(event) || event.getClickedBlock() == null;
    }

    @Override
    public String getUseType() {
        return "Right Click Block";
    }
}
