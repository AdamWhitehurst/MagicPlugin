package com.elmakers.mine.bukkit.action.builtin;

import java.util.ArrayList;
import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.FallingBlock;
import org.bukkit.inventory.ItemStack;

import com.elmakers.mine.bukkit.action.BaseSpellAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.block.UndoList;
import com.elmakers.mine.bukkit.api.spell.SpellResult;

public class DropAction extends BaseSpellAction {
    private int dropCount;
    private boolean falling = true;
    private Collection<ItemStack> drops;

    @Override
    public void finish(CastContext context) {
        super.finish(context);
        Location target = context.getTargetLocation();
        if (target == null || drops == null) return;

        for (ItemStack drop : drops) {
            target.getWorld().dropItemNaturally(target, drop);
        }
        drops = null;
    }

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        super.prepare(context, parameters);
        dropCount = parameters.getInt("drop_count", -1);
        falling = parameters.getBoolean("falling", true);
        drops = new ArrayList<>();
    }

    @SuppressWarnings("deprecation")
    @Override
    public SpellResult perform(CastContext context) {
        Block block = context.getTargetBlock();

        if (!context.hasBreakPermission(block)) {
            return SpellResult.INSUFFICIENT_PERMISSION;
        }
        if (!context.isDestructible(block)) {
            return SpellResult.NO_TARGET;
        }
        // Don't allow dropping temporary blocks
        UndoList blockUndoList = com.elmakers.mine.bukkit.block.UndoList.getUndoList(block.getLocation());
        if (blockUndoList != null && blockUndoList.isScheduled()) {
            return SpellResult.NO_TARGET;
        }
        if (dropCount < 0 || drops.size() < dropCount) {
            drops.addAll(block.getDrops());
        } else if (falling) {
            Location blockLocation = block.getLocation();
            FallingBlock falling = block.getWorld().spawnFallingBlock(blockLocation, block.getType(), block.getData());
            falling.setDropItem(false);
        }
        block.setType(Material.AIR);

        return SpellResult.CAST;
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }
}