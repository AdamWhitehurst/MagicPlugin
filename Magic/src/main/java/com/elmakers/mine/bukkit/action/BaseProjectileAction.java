package com.elmakers.mine.bukkit.action;

import com.elmakers.mine.bukkit.api.action.CastContext;
import com.elmakers.mine.bukkit.api.effect.EffectPlayer;
import com.elmakers.mine.bukkit.api.spell.SpellResult;
import com.elmakers.mine.bukkit.utility.BoundingBox;
import com.elmakers.mine.bukkit.utility.Targeting;
import com.elmakers.mine.bukkit.utility.TextUtils;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseProjectileAction extends CompoundAction {
    private long lifetime;
    private boolean setTarget;
    private String projectileEffectsKey;

    protected boolean track = false;

    private Set<Entity> tracking;
    private long expiration;

    @Override
    public void prepare(CastContext context, ConfigurationSection parameters) {
        super.prepare(context, parameters);
        lifetime = parameters.getLong("lifetime", 10000);
        setTarget = parameters.getBoolean("set_target", false);
        track = parameters.getBoolean("track_projectile", track);
        projectileEffectsKey = parameters.getString("projectile_effects", "projectile");
    }

    @Override
    public void reset(CastContext context) {
        super.reset(context);
        expiration = System.currentTimeMillis() + lifetime;
        tracking = null;
    }

    @Override
    public SpellResult step(CastContext context)
    {
        if (tracking == null || tracking.size() == 0) {
            tracking = null;
            return SpellResult.CAST;
        }

        if (System.currentTimeMillis() > expiration) {
            for (Entity entity : tracking) {
                entity.removeMetadata("track", context.getPlugin());
                entity.removeMetadata("damaged", context.getPlugin());
                entity.removeMetadata("hit", context.getPlugin());
                entity.remove();
            }
            context.getMage().sendDebugMessage(ChatColor.DARK_GRAY + "Projectiles expired", 4);
            tracking = null;
            return SpellResult.NO_TARGET;
        }

        for (Entity entity : tracking)
        {
            if (!entity.isValid() || entity.hasMetadata("hit"))
            {
                tracking.remove(entity);
                Plugin plugin = context.getPlugin();
                Entity targetEntity = null;
                Block targetBlock = null;
                Location targetLocation = entity.getLocation();
                List<MetadataValue> metadata = entity.getMetadata("hit");
                for (MetadataValue value : metadata) {
                    if (value.getOwningPlugin().equals(plugin)) {
                        Object o = value.value();
                        if (o != null && o instanceof WeakReference) {
                            WeakReference<?> reference = (WeakReference<?>)o;
                            o = reference.get();
                            if (o != null && o instanceof Entity) {
                                targetEntity = (Entity)o;
                                targetLocation = targetEntity.getLocation();
                            }
                            break;
                        } else if (o != null && o instanceof Block) {
                            targetBlock = (Block)o;
                            break;
                        }
                    }
                }
                entity.removeMetadata("track", plugin);
                if (targetEntity == null) {
                    context.getMage().sendDebugMessage(ChatColor.GRAY + "Projectile missed", 4);
                } else {
                    context.getMage().sendDebugMessage(ChatColor.GREEN + "Projectile hit " + ChatColor.GOLD + targetEntity.getType());
                }
                entity.removeMetadata("hit", plugin);
                Location sourceLocation = entity.getLocation();

                // So.. projectile X direction is backwards? I tested it with arrows and fireballs... don't really know what's up.
                // Going to adjust for it until I hear otherwise.
                Vector direction = sourceLocation.getDirection();
                direction.setX(-direction.getX());
                sourceLocation.setDirection(direction);

                if (targetBlock != null) {
                    // If we have a target block, try to approximate the hit location but ensure that it's on the edge of the block.
                    // This makes this appear similar to how raycast targeting would work
                    context.getMage().sendDebugMessage(ChatColor.GREEN + "Projectile at "
                            + TextUtils.printLocation(entity.getLocation()) + ChatColor.GREEN + " hit block at "
                            + TextUtils.printBlock(targetBlock)
                            + " facing " + TextUtils.printVector(sourceLocation.getDirection()), 13);

                    // Set previous location, this is mainly so the Teleport action works right.
                    context.setPreviousBlock(sourceLocation.getBlock());
                    targetLocation = targetBlock.getLocation();

                    // raycast from entity through block
                    Vector startPoint = sourceLocation.toVector();
                    Vector endPoint = startPoint.clone().add(direction.clone().normalize().multiply(2));
                    BoundingBox hitbox = new BoundingBox(targetLocation.toVector(), 0.001, 0.998, 0.001, 0.998, 0.001, 0.998);

                    Vector hit = hitbox.getIntersection(startPoint, endPoint);
                    if (hit != null) {
                        targetLocation.setX(hit.getX());
                        targetLocation.setY(hit.getY());
                        targetLocation.setZ(hit.getZ());
                    }
                } else {
                    context.getMage().sendDebugMessage(ChatColor.GRAY + "Projectile hit at " + TextUtils.printLocation(entity.getLocation())
                        + " facing " + TextUtils.printVector(sourceLocation.getDirection()), 132);
                }

                createActionContext(context, context.getMage().getEntity(), sourceLocation, targetEntity, targetLocation);
                actionContext.playEffects("hit");
                SpellResult result = startActions();
                if (targetEntity != null) {
                    result = result.min(SpellResult.CAST);
                } else {
                    result = result.min(SpellResult.NO_TARGET);
                }
                context.addResult(result);
                return result;
            }
        }

        return SpellResult.PENDING;
    }

    @Override
    public boolean next(CastContext context) {
        return tracking != null && tracking.size() > 0;
    }

    protected void track(CastContext context, Entity entity) {
        if (tracking == null) {
            tracking = new HashSet<>();
        }
        tracking.add(entity);
        context.registerForUndo(entity);
        if (setTarget) {
            context.setTargetEntity(entity);
        }
        Collection<EffectPlayer> projectileEffects = context.getEffects(projectileEffectsKey);
        for (EffectPlayer effectPlayer : projectileEffects) {
            effectPlayer.start(entity.getLocation(), entity, null, null);
        }
        if (track) {
            Targeting.track(context.getPlugin(), entity);
        }
    }

    protected SpellResult checkTracking(CastContext context) {
        if (tracking == null) {
            return SpellResult.FAIL;
        }
        if (!track && !hasActions()) {
            // Don't bother tracking if we're not doing anything on hit
            if (!context.hasEffects("hit")) {
                tracking = null;
            }
            return SpellResult.CAST;
        }

        return SpellResult.NO_TARGET;
    }
}
