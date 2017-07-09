package com.elmakers.mine.bukkit.batch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.elmakers.mine.bukkit.api.block.ModifyType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.elmakers.mine.bukkit.api.magic.Mage;
import com.elmakers.mine.bukkit.api.wand.Wand;
import com.elmakers.mine.bukkit.block.AutomatonLevel;
import com.elmakers.mine.bukkit.block.MaterialAndData;
import com.elmakers.mine.bukkit.spell.BlockSpell;
import com.elmakers.mine.bukkit.utility.CompatibilityUtils;
import com.elmakers.mine.bukkit.utility.Target;

public class SimulateBatch extends SpellBatch {
	private static BlockFace[] NEIGHBOR_FACES = { BlockFace.NORTH, BlockFace.NORTH_EAST, 
		BlockFace.EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH, BlockFace.SOUTH_WEST, BlockFace.WEST, BlockFace.NORTH_WEST
	};
	private static BlockFace[] DIAGONAL_FACES = {  BlockFace.SOUTH_EAST, BlockFace.NORTH_EAST, BlockFace.SOUTH_WEST, BlockFace.NORTH_WEST };
	private static BlockFace[] MAIN_FACES = {  BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST };
	private static BlockFace[] POWER_FACES = { BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH, BlockFace.DOWN, BlockFace.UP };
	
	private enum SimulationState {
		INITIALIZING, SCANNING, UPDATING, TARGETING, HEART_UPDATE, DELAY, CLEANUP, FINISHED
	};
	
	public enum TargetMode {
		STABILIZE, WANDER, GLIDE, HUNT, FLEE, DIRECTED
	};
	
	public enum TargetType {
		PLAYER, MAGE, MOB, AUTOMATON, ANY
	};

	public static boolean DEBUG = false;
	
	private Mage mage;
	private Block heartBlock;
	private Block heartTargetBlock;
	private TargetMode targetMode = TargetMode.STABILIZE;
	private TargetType targetType = TargetType.PLAYER;
	private String automataName;
	private AutomatonLevel level;
	private String dropItem;
	private Collection<String> dropItems;
	private int dropXp;
	private boolean reverseTargetDistanceScore = false;
	private boolean concurrent = false;
	private int commandMoveRangeSquared = 9;
	private int huntMaxRange = 128;
	private int castRange = 32;
	private int huntMinRange = 4;
	private int birthRangeSquared = 0;
	private int liveRangeSquared = 0;
	private float fovWeight = 100;
	private double huntFov = Math.PI * 1.8;
	private int delay;
	private long delayTimeout;
	private World world;
	private MaterialAndData birthMaterial;
	private Material deathMaterial;
	private Material heartMaterial;
	private boolean isAutomata;
	private int radius;
	private int x;
	private int y;
	private int z;
	private int r;
	private int yRadius;
	private int updatingIndex;
	private ArrayList<Boolean> liveCounts = new ArrayList<>();
	private ArrayList<Boolean> birthCounts = new ArrayList<>();
	private ArrayList<Boolean> diagonalLiveCounts = new ArrayList<>();
	private ArrayList<Boolean> diagonalBirthCounts = new ArrayList<>();
	private SimulationState state;
	private Location center;
	private ModifyType modifyType = ModifyType.NO_PHYSICS;
	private double reflectChance;
	private int maxBlocks = 0;

	private List<Block> deadBlocks = new ArrayList<>();
	private List<Block> bornBlocks = new ArrayList<>();
	private List<Target> potentialHeartBlocks = new LinkedList<>();
	
	public SimulateBatch(BlockSpell spell, Location center, int radius, int yRadius, MaterialAndData birth, Material death, Set<Integer> liveCounts, Set<Integer> birthCounts, String automataName) {
		super(spell);

		this.mage = spell.getMage();
		this.yRadius = yRadius;
		this.radius = radius;
		this.center = center.clone();
		
		this.birthMaterial = birth;
		this.deathMaterial = death;

		mapIntegers(liveCounts, this.liveCounts);
		mapIntegers(birthCounts, this.birthCounts);
		this.world = center.getWorld();
		this.automataName = automataName;
		this.isAutomata = automataName != null;
		if (isAutomata) {
			this.heartBlock = center.getBlock();
		}
		
		state = SimulationState.INITIALIZING;
		undoList.setModifyType(modifyType);
	}

	@Override
    public int size() {
		return radius * radius * radius * 8;
	}
	
	@Override
    public int remaining() {
		if (r >= radius) return 0;
		return (radius - r) *  (radius - r) *  (radius - r) * 8;
	}
	
	protected void checkForPotentialHeart(Block block, int distanceSquared) {
		if (isAutomata) {
			if (distanceSquared <= commandMoveRangeSquared) {
				// commandMoveRangeSquared is kind of too big, but it doesn't matter all that much
				// we still look at targets that end up with a score of 0, it just affects the sort ordering.
				potentialHeartBlocks.add(new Target(center, block, huntMinRange, huntMaxRange, huntFov, fovWeight, reverseTargetDistanceScore));
			}
		}
	}
	
	protected void die() {
		String message = spell.getMessage("death_broadcast").replace("$name", automataName);
		if (message.length() > 0) {
			controller.sendToMages(message, center);	
		}
		
		// Kill power block
		if (heartBlock != null) {
			registerForUndo(heartBlock);
			heartBlock.setType(Material.AIR);
		}

		// Drop item
		if (dropItem != null && dropItem.length() > 0) {
			Wand magicItem = controller.createWand(dropItem);
			if (magicItem != null) {
				center.getWorld().dropItemNaturally(center, magicItem.getItem());
			}
		}
		if (dropItems != null && dropItems.size() > 0) {
			for (String dropItemName : dropItems) {
				ItemStack drop = controller.createItem(dropItemName);
				if (drop != null) {
					center.getWorld().dropItemNaturally(center, drop);
				}
			}
		}
		
		// Drop Xp
		if (dropXp > 0) {
			Entity entity = center.getWorld().spawnEntity(center, EntityType.EXPERIENCE_ORB);
			if (entity != null && entity instanceof ExperienceOrb) {
				ExperienceOrb orb = (ExperienceOrb)entity;
				orb.setExperience(dropXp);
			}
		}

		if (level != null) {
			level.onDeath(mage, birthMaterial, heartMaterial);
		}
	}

	@SuppressWarnings("deprecation")
	protected void killBlock(Block block) {
		if (concurrent) {
			registerForUndo(block);
			if (modifyType == ModifyType.FAST) {
				CompatibilityUtils.setBlockFast(block, deathMaterial, 0);
			} else {
				block.setTypeIdAndData(deathMaterial.getId(), (byte)0, false);
			}
			if (reflectChance > 0) {
				com.elmakers.mine.bukkit.block.UndoList.getRegistry().unregisterReflective(block);
			}
		} else {
			deadBlocks.add(block);
		}
	}
	
	protected void birthBlock(Block block) {
		if (concurrent) {
			registerForUndo(block);
			birthMaterial.modify(block, modifyType);
			if (reflectChance > 0) {
				com.elmakers.mine.bukkit.block.UndoList.getRegistry().registerReflective(block, reflectChance);
				undoList.setUndoReflective(true);
			}
		} else {
			bornBlocks.add(block);
		}
	}
	
	protected boolean simulateBlock(int dx, int dy, int dz) {
		int x = center.getBlockX() + dx;
		int y = center.getBlockY() + dy;
		int z = center.getBlockZ() + dz;
		Block block = world.getBlockAt(x, y, z);
		if (!block.getChunk().isLoaded()) {
			return false;
		}
		
		Material blockMaterial = block.getType();
		if (birthMaterial.is(block)) {
			int distanceSquared = liveRangeSquared > 0 || isAutomata ?
					(int)Math.ceil(block.getLocation().distanceSquared(heartBlock.getLocation())) : 0;

			if (liveRangeSquared <= 0 || distanceSquared <= liveRangeSquared) {
				if (diagonalLiveCounts.size() > 0) {
					int faceNeighborCount = getFaceNeighborCount(block, birthMaterial, isAutomata);
					int diagonalNeighborCount = getDiagonalNeighborCount(block, birthMaterial, isAutomata);
					if (faceNeighborCount >= liveCounts.size() || !liveCounts.get(faceNeighborCount)
						|| diagonalNeighborCount >= diagonalLiveCounts.size() || !diagonalLiveCounts.get(diagonalNeighborCount)) {
						killBlock(block);
					} else {
						checkForPotentialHeart(block, distanceSquared);
					}
				} else {
					int neighborCount = getNeighborCount(block, birthMaterial, isAutomata);
					if (neighborCount >= liveCounts.size() || !liveCounts.get(neighborCount)) {
						killBlock(block);
					} else {
						checkForPotentialHeart(block, distanceSquared);
					}
				}
			} else {
				killBlock(block);
			}
		} else if (blockMaterial == deathMaterial) {
			int distanceSquared = birthRangeSquared > 0 || isAutomata ?
					(int)Math.ceil(block.getLocation().distanceSquared(heartBlock.getLocation())) : 0;

			if (birthRangeSquared <= 0 || distanceSquared <= birthRangeSquared) {	
				if (diagonalBirthCounts.size() > 0) {
					int faceNeighborCount = getFaceNeighborCount(block, birthMaterial, isAutomata);
					int diagonalNeighborCount = getDiagonalNeighborCount(block, birthMaterial, isAutomata);
					if (faceNeighborCount < birthCounts.size() && birthCounts.get(faceNeighborCount)
						&& diagonalNeighborCount < diagonalBirthCounts.size() && diagonalBirthCounts.get(diagonalNeighborCount)) {
						birthBlock(block);
						checkForPotentialHeart(block, distanceSquared);
					}
				} else {
					int neighborCount = getNeighborCount(block, birthMaterial, isAutomata);
					if (neighborCount < birthCounts.size() && birthCounts.get(neighborCount)) {
						birthBlock(block);
						checkForPotentialHeart(block, distanceSquared);
					}
				}
			}
		}
		
		return true;
	}
	
	protected boolean simulateBlocks(int x, int y, int z) {
		boolean success = true;
		if (y != 0) {
			success = success && simulateBlock(x, -y, z);
			if (x != 0) success = success && simulateBlock(-x, -y, z);
			if (z != 0) success = success && simulateBlock(x, -y, -z);
			if (x != 0 && z != 0) success = success && simulateBlock(-x, -y, -z);
		}
		success = success && simulateBlock(x, y, z);
		if (x != 0) success = success && simulateBlock(-x, y, z);
		if (z != 0) success = success && simulateBlock(x, y, -z);
		if (z != 0 && x != 0) success = success && simulateBlock(-x, y, -z);
		return success;
	}

	@Override
	public int process(int maxBlocks) {
		int processedBlocks = 0;
		if (state == SimulationState.INITIALIZING) {
			// Reset state
			x = 0;
			y = 0;
			z = 0;
			r = 0;
			updatingIndex = 0;
			bornBlocks.clear();
			deadBlocks.clear();

			// Process the casting first, and only if specially configured to do so.
			if (isAutomata) {
				// Look for a target
				target();

				// We are going to rely on the block toggling to kick this back to life when the chunk
				// reloads, so for now just bail and hope the timing works out.
				if (heartBlock == null || !heartBlock.getChunk().isLoaded()) {
					finish();
					return processedBlocks;
				}
				
				// Check for death since activation (e.g. during delay period)
				if (heartBlock.getType() != heartMaterial) {
					if (DEBUG) {
						controller.getLogger().info("DIED, no Heart at : " + heartBlock);
					}
					die();
					finish();
					return processedBlocks;
				}

				// Reset potential new locations
				potentialHeartBlocks.clear();
			}
			
			processedBlocks++;
			state = SimulationState.SCANNING;
		}
		
		while (state == SimulationState.SCANNING && processedBlocks <= maxBlocks) {
			// Make the heart a normal block so the sim will process it
			registerForUndo(heartBlock);
			birthMaterial.modify(heartBlock);

			if (!simulateBlocks(x, y, z)) {
				// TODO: Is this the right thing to do?
				finish();
				return processedBlocks;
			}
			
			y++;
			if (y > yRadius) {
				y = 0;
				if (x < radius) {
					x++;
				} else {
					z--;
					if (z < 0) {
						r++;
						z = r;
						x = 0;
					}
				}
			}
			
			if (r > radius) 
			{
				state = SimulationState.UPDATING;
			}
		}
		
		while (state == SimulationState.UPDATING && processedBlocks <= maxBlocks) {
			int deadIndex = updatingIndex;
			if (deadIndex >= 0 && deadIndex < deadBlocks.size()) {
				Block killBlock = deadBlocks.get(deadIndex);
				if (!killBlock.getChunk().isLoaded()) {
					killBlock.getChunk().load();
					return processedBlocks;
				}
				
				if (birthMaterial.is(killBlock)) {
					registerForUndo(killBlock);
					killBlock.setType(deathMaterial);
				} else {
					// If this block was destroyed while we were processing,
					// avoid spawning a random birth block.
					// This tries to make it so automata don't "cheat" when
					// getting destroyed. A bit hacky though, I'm not about
					// to re-simulate...
					if (bornBlocks.size() > 0) {
						bornBlocks.remove(bornBlocks.size() - 1);
					}
				}
				processedBlocks++;
			}
			
			int bornIndex = updatingIndex - deadBlocks.size();
			if (bornIndex >= 0 && bornIndex < bornBlocks.size()) {
				Block birthBlock = bornBlocks.get(bornIndex);
				if (!birthBlock.getChunk().isLoaded()) {
					birthBlock.getChunk().load();
					return processedBlocks;
				}
				registerForUndo(birthBlock);
				birthMaterial.modify(birthBlock, modifyType);
			}
			
			updatingIndex++;
			if (updatingIndex >= deadBlocks.size() + bornBlocks.size()) {
				state = SimulationState.TARGETING;
				
				// Wait at least a tick
				return maxBlocks;
			}
		}
		
		// Each of the following states will end in this tick
		if (state == SimulationState.TARGETING) {
			if (isAutomata && potentialHeartBlocks.size() > 0) {
				switch (targetMode) {
				case HUNT:
					Collections.sort(potentialHeartBlocks);
					break;
				case FLEE:
					Collections.sort(potentialHeartBlocks);
					break;
				default:
					Collections.shuffle(potentialHeartBlocks);
					break;
				}
				
				// Find a valid block for the command
				heartTargetBlock = null;
				Block backupBlock = null;
				while (heartTargetBlock == null && potentialHeartBlocks.size() > 0) {
					Block block = potentialHeartBlocks.remove(0).getBlock();
					if (block != null && birthMaterial.is(block)) {
						heartTargetBlock = block;
					}
				}
				
				// If we didn't find any powerable blocks, but we did find at least one valid sim block
				// just use that one.
				if (heartTargetBlock == null) heartTargetBlock = backupBlock;

                // Search for a power block
                if (heartTargetBlock == null && DEBUG) {
					controller.getLogger().info("Could not find a valid command block location");
                }
			}
			if (DEBUG) {
				if (heartTargetBlock != null) {
					controller.getLogger().info("MOVED: " + heartTargetBlock.getLocation().toVector().subtract(center.toVector()));
				}
			}
			state = SimulationState.HEART_UPDATE;
		}
		
		if (state == SimulationState.HEART_UPDATE) {
			if (isAutomata) {
				if (heartTargetBlock != null) {
					if (!heartTargetBlock.getChunk().isLoaded()) {
						heartTargetBlock.getChunk().load();
						return processedBlocks;
					}

					if (reflectChance > 0) {
						com.elmakers.mine.bukkit.block.UndoList.getRegistry().unregisterReflective(heartTargetBlock);
					}
					registerForUndo(heartTargetBlock);
					heartTargetBlock.setType(heartMaterial);
					heartBlock = heartTargetBlock;
					Location newLocation = heartTargetBlock.getLocation();
					newLocation.setPitch(center.getPitch());
					newLocation.setYaw(center.getYaw());
					center = newLocation;
					mage.setLocation(newLocation);
				} else {
					die();
				}
			}
			delayTimeout = System.currentTimeMillis() + delay;
			state = delay > 0 ? SimulationState.DELAY : SimulationState.CLEANUP;
		}

		if (state == SimulationState.DELAY) {
			processedBlocks++;
			if (heartBlock != null && heartBlock.getType() != heartMaterial) {
				if (DEBUG) {
					controller.getLogger().info("DIED, no Heart at : " + heartBlock);
				}
				die();
				finish();
			} else {
				if (System.currentTimeMillis() > delayTimeout) {
					state = SimulationState.CLEANUP;
				}
			}

			return processedBlocks;
		}

		if (state == SimulationState.CLEANUP) {
			if (this.maxBlocks <= 0) {
				state = SimulationState.FINISHED;
			} else {
				boolean undid = false;
				while (processedBlocks <= maxBlocks && undoList.size() > this.maxBlocks) {
					if (undoList.undoNext(false) == null) break;
					undid = true;
				}
				// make sure we didn't undo the heart
				if (undid && heartBlock != null) {
					registerForUndo(heartBlock);
					heartBlock.setType(heartMaterial);
				}
				if (undoList.size() <= this.maxBlocks) {
					state = SimulationState.FINISHED;
				}
			}
		}
		
		if (state == SimulationState.FINISHED) {
			if (isAutomata) {
				state = SimulationState.INITIALIZING;
			} else {
				finish();
			}
		}
		
		return processedBlocks;
	}
	
	public void setDrop(String dropName, int dropXp, Collection<String> drops) {
		this.dropItem = dropName;
		this.dropXp = dropXp;
		this.dropItems = drops;
	}
	
	public void setLevel(AutomatonLevel level) {
		this.level = level;
		this.commandMoveRangeSquared = level.getMoveRangeSquared(commandMoveRangeSquared);
		this.dropXp = level.getDropXp(dropXp);
		this.liveRangeSquared = level.getLiveRangeSquared(liveRangeSquared);
		this.birthRangeSquared = level.getBirthRangeSquared(birthRangeSquared);
		this.radius = level.getRadius(radius);
		this.yRadius = level.getYRadius(yRadius);
	}
	
	public void setBirthRange(int range) {
		birthRangeSquared = range * range;
	}

	public void setLiveRange(int range) {
		liveRangeSquared = range * range;
	}
	
	public void setMaxHuntRange(int range) {
		huntMaxRange = range;
	}
	
	public void setCastRange(int range) {
		castRange = range;
	}

	public void setMinHuntRange(int range) {
		huntMinRange = range;
	}
	
	public void setTargetType(TargetType targetType) {
		this.targetType = targetType;
	}
	
	public void target() {
		switch (targetMode)
		{
		case FLEE:
		case HUNT:
		case DIRECTED:
			Target bestTarget = null;
			reverseTargetDistanceScore = true;
			if (targetType == TargetType.ANY || targetType == TargetType.MOB)
			{
				List<Entity> entities = CompatibilityUtils.getNearbyEntities(center, huntMaxRange, huntMaxRange, huntMaxRange);
				for (Entity entity : entities)
				{
					// We'll get the players from the Mages list
					if (entity instanceof Player || !(entity instanceof LivingEntity) || entity.isDead()) continue;
					if (!entity.getLocation().getWorld().equals(center.getWorld())) continue;
					LivingEntity li = (LivingEntity)entity;
					if (li.hasPotionEffect(PotionEffectType.INVISIBILITY)) continue;
					Target newScore = new Target(center, entity, huntMinRange, huntMaxRange, huntFov, 100, false);
					int score = newScore.getScore();
					if (bestTarget == null || score > bestTarget.getScore()) {
						bestTarget = newScore;
					}
				}
			}
			if (targetType == TargetType.MAGE || targetType == TargetType.AUTOMATON || targetType == TargetType.ANY || targetType == TargetType.PLAYER)
			{
				Collection<Mage> mages = controller.getMages();
				for (Mage mage : mages)
				{
					if (mage == this.mage) continue;
					if (targetType == TargetType.AUTOMATON && !mage.isAutomaton()) continue;
					if (targetType == TargetType.PLAYER && mage.getPlayer() == null) continue;
					if (mage.isDead() || !mage.isOnline() || !mage.hasLocation() || mage.isSuperProtected()) continue;
					if (!mage.getLocation().getWorld().equals(center.getWorld())) continue;

					LivingEntity li = mage.getLivingEntity();
					if (li != null && li.hasPotionEffect(PotionEffectType.INVISIBILITY)) continue;
					
					Target newScore = new Target(center, mage, huntMinRange, huntMaxRange, huntFov, 100, false);
					int score = newScore.getScore();
					if (bestTarget == null || score > bestTarget.getScore()) {
						bestTarget = newScore;
					}
				}
			}
			
			if (bestTarget != null) 
			{
				String targetDescription = bestTarget.getEntity() == null ? "NONE" :
					((bestTarget instanceof Player) ? ((Player)bestTarget.getEntity()).getName() : bestTarget.getEntity().getType().name());
				
				if (DEBUG) {
					controller.getLogger().info(" *Tracking " + targetDescription + 
				 		" score: " + bestTarget.getScore() + " location: " + center + " -> " + bestTarget.getLocation() + " move " + commandMoveRangeSquared);
				}
				Vector direction = null;
				
				if (targetMode == TargetMode.DIRECTED) {
					direction = bestTarget.getLocation().getDirection();
					if (DEBUG) {
						controller.getLogger().info(" *Directed: " + direction);
					}
				} else {
					Location targetLocation = bestTarget.getLocation();
					direction = targetLocation.toVector().subtract(center.toVector());
				}
				
				if (direction != null) {
					center.setDirection(direction);
					mage.setLocation(center);
				}
				
				// Check for obstruction
				// TODO Think about this more..
				/*
				Block block = spell.getInteractBlock();
				if (block.getType() != Material.AIR && block.getType() != POWER_MATERIAL && !!birthMaterial.is(block)) {
					// TODO: Use location.setDirection in 1.7+
					center = CompatibilityUtils.setDirection(center, new Vector(0, 1, 0));
				}
				*/
				
				if (level != null && center.distanceSquared(bestTarget.getLocation()) < castRange * castRange) {
					level.onTick(mage, birthMaterial, heartMaterial);
				}
				
				// After ticking, re-position for movement. This way spells still fire towards the target.
				if (targetMode == TargetMode.FLEE) {
					direction = direction.multiply(-1);
					// Don't Flee upward
					if (direction.getY() > 0) {
						direction.setY(-direction.getY());
					}
				}
			}
			break;
		case GLIDE:
			reverseTargetDistanceScore = true;
			break;
		default:
			reverseTargetDistanceScore = false;
		}
	}
	
	public void setMoveRange(int commandRadius) {
		commandMoveRangeSquared = commandRadius * commandRadius;
	}
	
	protected int getNeighborCount(Block block, MaterialAndData liveMaterial, boolean includeCommands) {
        return getDiagonalNeighborCount(block, liveMaterial, includeCommands) + getFaceNeighborCount(block, liveMaterial, includeCommands);
	}

    protected int getFaceNeighborCount(Block block, MaterialAndData liveMaterial, boolean includeCommands) {
        int liveCount = 0;
        BlockFace[] faces = yRadius > 0 ? POWER_FACES : MAIN_FACES;
        for (BlockFace face : faces) {
            if (liveMaterial.is(block.getRelative(face))) {
                liveCount++;
            }
        }
        return liveCount;
    }

    protected int getDiagonalNeighborCount(Block block, MaterialAndData liveMaterial, boolean includeCommands) {
        int liveCount = 0;
        for (BlockFace face : DIAGONAL_FACES) {
            if (liveMaterial.is(block.getRelative(face))) {
                liveCount++;
            }
        }

        if (yRadius > 0) {
            Block upBlock = block.getRelative(BlockFace.UP);
            for (BlockFace face : NEIGHBOR_FACES) {
                if (liveMaterial.is(upBlock.getRelative(face))) {
                    liveCount++;
                }
            }

            Block downBlock = block.getRelative(BlockFace.DOWN);
            for (BlockFace face : NEIGHBOR_FACES) {
                if (liveMaterial.is(downBlock.getRelative(face))) {
                    liveCount++;
                }
            }
        }
        return liveCount;
    }

	public void setConcurrent(boolean concurrent) {
		this.concurrent = concurrent;
	}
	
	@Override
	public void finish() {
		if (isAutomata && !mage.isPlayer()) {
			controller.forgetMage(mage);
		}
		state = SimulationState.FINISHED;
		super.finish();
	}
	
	protected void mapIntegers(Collection<Integer> flags, List<Boolean> flagMap) {
		for (Integer flag : flags) {
			while (flagMap.size() <= flag) {
				flagMap.add(false);
			}
			flagMap.set(flag, true);
		}
	}
	
	public void setDiagonalLiveRules(Collection<Integer> rules) {
		mapIntegers(rules, this.diagonalLiveCounts);
	}
	
	public void setDiagonalBirthRules(Collection<Integer> rules) {
		mapIntegers(rules, this.diagonalBirthCounts);
	}

	public void setReflectChange(double reflectChance) {
		this.reflectChance = reflectChance;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

	public void setTargetMode(TargetMode mode) {
		this.targetMode = mode;
	}

	public void setMaxBlocks(int maxBlocks) {
		this.maxBlocks = maxBlocks;
	}

	public void setHeartMaterial(Material material) {
		this.heartMaterial = material;
	}
}
