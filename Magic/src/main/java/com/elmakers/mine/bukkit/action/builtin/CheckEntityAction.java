package com.elmakers.mine.bukkit.action.builtin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;

import com.elmakers.mine.bukkit.action.CompoundAction;
import com.elmakers.mine.bukkit.api.action.ActionHandler;
import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.utility.ConfigurationUtils;

public class CheckEntityAction extends CompoundAction {
    private boolean allowCaster;
    private boolean onlyCaster;
    private Set<EntityType> allowedTypes;
    private Set<EntityType> deniedTypes;
    
    @Override
    public void prepare(CastContext context, ConfigurationSection parameters)
    {
        super.prepare(context, parameters);
        allowCaster = parameters.getBoolean("allow_caster", true);
        onlyCaster = parameters.getBoolean("only_caster", false);

        if (parameters.contains("allowed_entities")) {
            List<String> keys = ConfigurationUtils.getStringList(parameters, "allowed_entities");
            allowedTypes = new HashSet<>();
            for (String key : keys) {
                try {
                    EntityType entityType = EntityType.valueOf(key.toUpperCase());
                    allowedTypes.add(entityType);
                } catch (Exception ex) {
                    context.getLogger().warning("Invalid entity type in CheckEntity configuration: " + key);
                }
            }
        }
        if (parameters.contains("denied_entities")) {
            List<String> keys = ConfigurationUtils.getStringList(parameters, "denied_entities");
            deniedTypes = new HashSet<>();
            for (String key : keys) {
                try {
                    EntityType entityType = EntityType.valueOf(key.toUpperCase());
                    deniedTypes.add(entityType);
                } catch (Exception ex) {
                    context.getLogger().warning("Invalid entity type in CheckEntity configuration: " + key);
                }
            }
        }
    }
    
    protected boolean isAllowed(CastContext context) {
        Entity targetEntity = context.getTargetEntity();
        if (targetEntity == null) return false;
        boolean isCaster = targetEntity == context.getEntity();
        if (!allowCaster && isCaster) {
            return false;
        }
        if (onlyCaster && !isCaster) {
            return false;
        }
        if (allowedTypes != null && !allowedTypes.contains(targetEntity.getType())) {
            return false;
        }
        if (deniedTypes != null && deniedTypes.contains(targetEntity.getType())) {
            return false;
        }
        return true;
    }
    
    @Override
    public SpellResult step(CastContext context) {
        boolean allowed = isAllowed(context);
        ActionHandler actions = getHandler("actions");
        if (actions == null || actions.size() == 0) {
            return allowed ? SpellResult.CAST : SpellResult.STOP;
        }
        
        if (!allowed) {
            return SpellResult.NO_TARGET;
        }
        return startActions();
    }

    @Override
    public boolean requiresTarget() {
        return true;
    }

    @Override
    public boolean requiresTargetEntity() {
        return true;
    }
}