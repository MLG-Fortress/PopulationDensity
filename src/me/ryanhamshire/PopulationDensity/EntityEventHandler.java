/*
    PopulationDensity Server Plugin for Minecraft
    Copyright (C) 2011 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.PopulationDensity;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class EntityEventHandler implements Listener
{
    PopulationDensity instance;

    private Random random = new Random();

    //block types monsters may spawn on when grinders are disabled
    static HashMap<Environment, HashSet<Material>> allowedSpawnBlocks;

    public EntityEventHandler(PopulationDensity populationDensity)
    {
        if (allowedSpawnBlocks == null)
        {
            allowedSpawnBlocks = new HashMap<Environment, HashSet<Material>>();

            allowedSpawnBlocks.put(Environment.NORMAL, new HashSet<>(Arrays.asList(
                    Material.GRASS_BLOCK,
                    Material.SAND,
                    Material.SUSPICIOUS_SAND,
                    Material.GRAVEL,
                    Material.SUSPICIOUS_GRAVEL,
                    Material.STONE,
                    Material.MOSSY_COBBLESTONE,
                    Material.OBSIDIAN)));

            allowedSpawnBlocks.put(Environment.NETHER, new HashSet<>(Arrays.asList(
                    Material.NETHERRACK,
                    Material.NETHER_BRICK)));

            allowedSpawnBlocks.put(Environment.THE_END, new HashSet<>(Arrays.asList(
                    Material.END_STONE,
                    Material.OBSIDIAN)));
        }
        instance = populationDensity;
    }

    //when an entity (includes both dynamite and creepers) explodes...
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent explodeEvent)
    {
        Location location = explodeEvent.getLocation();

        //if it's NOT in the managed world, let it splode (or let other plugins worry about it)
        RegionCoordinates region = RegionCoordinates.fromLocation(location);
        if (region == null) return;

        //otherwise if it's close to a region post
        Location regionCenter = PopulationDensity.getRegionCenter(region, false);
        regionCenter.setY(PopulationDensity.ManagedWorld.getHighestBlockYAt(regionCenter));
        if (regionCenter.distanceSquared(location) < 225)  //225 = 15 * 15
        {
            explodeEvent.blockList().clear(); //All the noise and terror, none of the destruction (whew!).
        }

        //NOTE!  Why not distance?  Because distance squared is cheaper and will be good enough for this.
    }

    private Set<Material> saplings = new HashSet<>(Arrays.asList(
            Material.OAK_SAPLING,
            Material.SPRUCE_SAPLING,
            Material.BIRCH_SAPLING,
            Material.JUNGLE_SAPLING,
            Material.ACACIA_SAPLING,
            Material.DARK_OAK_SAPLING,
            Material.CHERRY_SAPLING,
            Material.MANGROVE_PROPAGULE
    ));

    private Set<Material> logs = new HashSet<>(Arrays.asList(
            Material.OAK_LOG,
            Material.SPRUCE_LOG,
            Material.BIRCH_LOG,
            Material.JUNGLE_LOG,
            Material.ACACIA_LOG,
            Material.DARK_OAK_LOG,
            Material.CHERRY_LOG,
            Material.MANGROVE_LOG
    ));

    //when an item despawns
    //FEATURE: in the newest region only, regrow trees from fallen saplings
    @EventHandler(ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event)
    {
        //respect config option
        if (!PopulationDensity.instance.regrowTrees) return;

        Item entity = event.getEntity();

        //get info about the dropped item
        ItemStack item = event.getEntity().getItemStack();

        //only care about saplings
        if (!saplings.contains(item.getType())) return;

        //only care about the newest region
        if (!PopulationDensity.instance.dataStore.getOpenRegion().equals(RegionCoordinates.fromLocation(entity.getLocation())))
            return;

        //only replace these blocks with saplings
        Block block = entity.getLocation().getBlock();
        if (block.getType() != Material.AIR && block.getType() != Material.SHORT_GRASS && block.getType() != Material.SNOW)
            return;

        //don't plant saplings next to other saplings or logs
        Block[] neighbors = new Block[]{
                block.getRelative(BlockFace.EAST),
                block.getRelative(BlockFace.WEST),
                block.getRelative(BlockFace.NORTH),
                block.getRelative(BlockFace.SOUTH),
                block.getRelative(BlockFace.NORTH_EAST),
                block.getRelative(BlockFace.SOUTH_EAST),
                block.getRelative(BlockFace.SOUTH_WEST),
                block.getRelative(BlockFace.NORTH_WEST)};

        for (Block neighbor : neighbors)
        {
            if (saplings.contains(neighbor.getType()) || logs.contains(neighbor.getType())) return;
        }

        //only plant trees in grass or dirt
        Block underBlock = block.getRelative(BlockFace.DOWN);
        if (underBlock.getType() == Material.GRASS_BLOCK || underBlock.getType() == Material.DIRT)
        {
            block.setType(item.getType());
        }
    }

    //RoboMWM - Used solely for resetting the idle timer, may remove(?)
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event)
    {
        Player attacker = null;
        Entity damageSource = event.getDamager();
        if (damageSource instanceof Player)
        {
            attacker = (Player)damageSource;
        } else if (damageSource instanceof Arrow)
        {
            Arrow arrow = (Arrow)damageSource;
            if (arrow.getShooter() instanceof Player)
            {
                attacker = (Player)arrow.getShooter();
            }
        } else if (damageSource instanceof ThrownPotion)
        {
            ThrownPotion potion = (ThrownPotion)damageSource;
            if (potion.getShooter() instanceof Player)
            {
                attacker = (Player)potion.getShooter();
            }
        }

        if (attacker != null)
        {
            PopulationDensity.instance.resetIdleTimer(attacker);
        }
    }

    private int respawnAnimalCounter = 1;

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntitySpawn(CreatureSpawnEvent event)
    {
        SpawnReason reason = event.getSpawnReason();
        Entity entity = event.getEntity();

        //if lag has prompted PD to turn off monster grinders, limit spawns
        if (PopulationDensity.grindersStopped)
        {
            if (reason == SpawnReason.NETHER_PORTAL || reason == SpawnReason.SPAWNER)
            {
                event.setCancelled(true);
                return;
            } else if (reason == SpawnReason.NATURAL && entity instanceof Monster)
            {
                HashSet<Material> allowedBlockTypes = allowedSpawnBlocks.get(entity.getWorld().getEnvironment());
                if (!allowedBlockTypes.contains(entity.getLocation().getBlock().getRelative(BlockFace.DOWN).getType()))
                {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        //speed limit on monster grinder spawn rates - only affects grinders that rely on naturally-spawning monsters.
        if (instance.nearbyMonsterSpawnLimit > -1 && reason != SpawnReason.SPAWNER_EGG && reason != SpawnReason.SPAWNER && entity instanceof Monster)
        {
            int monstersNearby = 0;
            List<Entity> entities = entity.getNearbyEntities(10, 20, 10);
            for (Entity nearbyEntity : entities)
            {
                if (nearbyEntity instanceof Monster) monstersNearby++;
                if (monstersNearby > PopulationDensity.instance.nearbyMonsterSpawnLimit)
                {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        //natural spawns may cause animal spawns to keep new player resources available
        if (reason == SpawnReason.NATURAL)
        {
            if (PopulationDensity.ManagedWorld == null || event.getLocation().getWorld() != PopulationDensity.ManagedWorld)
                return;

            //when an animal naturally spawns, grow grass around it
            if (entity instanceof Animals && PopulationDensity.instance.regrowGrass)
            {
                this.regrow(entity.getLocation().getBlock(), 4);
            }

            //when a monster spawns, sometimes spawn animals too
            if (entity instanceof Monster && PopulationDensity.instance.respawnAnimals)
            {
                //only do this if the spawn is in the newest region
                if (!PopulationDensity.instance.dataStore.getOpenRegion().equals(RegionCoordinates.fromLocation(entity.getLocation())))
                    return;

                //if it's on grass, there's a 1/100 chance it will also spawn a group of animals
                Block underBlock = event.getLocation().getBlock().getRelative(BlockFace.DOWN);
                if (underBlock.getType() == Material.GRASS_BLOCK && --this.respawnAnimalCounter == 0)
                {
                    this.respawnAnimalCounter = 5;

                    //check for other nearby animals
                    for (Entity nearbyEntity : entity.getNearbyEntities(30, 30, 30))
                    {
                        if (nearbyEntity instanceof Animals) return;
                    }

                    EntityType animalType = null;

                    //decide what to spawn based on the type of monster
                    if (entity.getType() == EntityType.CREEPER)
                    {
                        animalType = EntityType.CHICKEN;
                    } else if (entity.getType() == EntityType.ZOMBIE)
                    {
                        animalType = EntityType.COW;
                    } else if (entity.getType() == EntityType.SPIDER)
                    {
                        animalType = EntityType.SHEEP;
                    } else if (entity.getType() == EntityType.SKELETON)
                    {
                        animalType = EntityType.PIG;
                    } else if (entity.getType() == EntityType.ENDERMAN)
                    {
                        if (random.nextBoolean())
                            animalType = EntityType.HORSE;
                        else
                            animalType = EntityType.WOLF;
                    }

                    //spawn an animal at the entity's location and regrow some grass
                    if (animalType != null)
                    {
                        entity.getWorld().spawnEntity(entity.getLocation(), animalType);
                        this.regrow(entity.getLocation().getBlock(), 4);
                    }
                }
            }
        }
    }

    private void regrow(Block center, int radius)
    {
        Block toHandle;
        for (int x = -radius; x <= radius; x++)
        {
            for (int z = -radius; z <= radius; z++)
            {
                toHandle = center.getWorld().getBlockAt(center.getX() + x, center.getY() + 2, center.getZ() + z);
                while (toHandle.getType() == Material.AIR && toHandle.getY() > center.getY() - 4)
                    toHandle = toHandle.getRelative(BlockFace.DOWN);
                if (toHandle.getType() == Material.GRASS_BLOCK) // Block is grass
                {
                    Block aboveBlock = toHandle.getRelative(BlockFace.UP);
                    if (aboveBlock.getType() == Material.AIR)
                    {
                        aboveBlock.setType(Material.SHORT_GRASS);
                    }
                    continue;
                }
            }
        }
    }


}
