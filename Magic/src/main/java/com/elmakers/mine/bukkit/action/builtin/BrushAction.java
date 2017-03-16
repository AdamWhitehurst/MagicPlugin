package com.elmakers.mine.bukkit.action.builtin;

import com.elmakers.mine.bukkit.action.CompoundAction;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.block.MaterialBrush;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class BrushAction extends CompoundAction {
    private List<String> brushes = new ArrayList<>();
    private boolean sample = false;

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        super.prepare(context, parameters);
        brushes.clear();

        String materialKey = parameters.getString("brush", null);
        if (materialKey != null) {
            addBrush(materialKey);
        }
        List<String> brushList = parameters.getStringList("brushes");
        if (brushList != null) {
            for (String brushKey : brushList) {
                addBrush(brushKey);
            }
        }
        sample = parameters.getBoolean("sample", false);
    }

    protected void addBrush(String brushKey) {
        brushes.add(brushKey);
    }

    @Override
    public SpellResult step(CastContext context) {
        if (brushes.size() == 0 && !sample) {
            return startActions();
        }

        if (sample)
        {
            Block targetBlock = context.getTargetBlock();
            if (targetBlock != null)
            {
                Mage mage = context.getMage();
                MaterialBrush brush = new MaterialBrush(mage, targetBlock);
                actionContext.setBrush(brush);
            }
        }
        else
        {
            String brushKey = brushes.get(context.getRandom().nextInt(brushes.size()));
            MaterialBrush brush = new MaterialBrush(context.getMage(), context.getLocation(), brushKey);
            actionContext.setBrush(brush);
        }
        return startActions();
    }

    @Override
    public boolean usesBrush() {
        return false;
    }
}
