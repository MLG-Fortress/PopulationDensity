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

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

public class BlockEventHandler implements Listener
{
    private static Set<Material> alwaysBreakableMaterials = new HashSet<>(Arrays.asList(
            Material.TALL_GRASS,
            Material.ROSE_BUSH,
            Material.LILAC,
            Material.LARGE_FERN,
            Material.PEONY,
            Material.SHORT_GRASS,
            Material.FERN,
            Material.DEAD_BUSH,
            Material.OAK_LOG,
            Material.SPRUCE_LOG,
            Material.BIRCH_LOG,
            Material.JUNGLE_LOG,
            Material.ACACIA_LOG,
            Material.DARK_OAK_LOG,
            Material.CHERRY_LOG,
            Material.MANGROVE_LOG,
            Material.OAK_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES,
            Material.DARK_OAK_LEAVES,
            Material.CHERRY_LEAVES,
            Material.MANGROVE_LEAVES,
            Material.POPPY,
            Material.DANDELION,
            Material.BLUE_ORCHID,
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.RED_TULIP,
            Material.ORANGE_TULIP,
            Material.WHITE_TULIP,
            Material.PINK_TULIP,
            Material.OXEYE_DAISY,
            Material.SPORE_BLOSSOM,
            Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM,
            Material.PINK_PETALS,
            Material.PITCHER_PLANT,
            Material.TORCHFLOWER,
            Material.SNOW_BLOCK
    ));

    private static Set<Material> beds = new HashSet<>(Arrays.asList(
            Material.WHITE_BED,
            Material.ORANGE_BED,
            Material.MAGENTA_BED,
            Material.LIGHT_BLUE_BED,
            Material.YELLOW_BED,
            Material.LIME_BED,
            Material.PINK_BED,
            Material.GRAY_BED,
            Material.LIGHT_GRAY_BED,
            Material.CYAN_BED,
            Material.PURPLE_BED,
            Material.BLUE_BED,
            Material.BROWN_BED,
            Material.GREEN_BED,
            Material.RED_BED,
            Material.BLACK_BED
    ));

    //when a player breaks a block...
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent breakEvent)
    {
        Player player = breakEvent.getPlayer();

        PopulationDensity.instance.resetIdleTimer(player);

        Block block = breakEvent.getBlock();

        //if the player is not in managed world, do nothing (let vanilla code and other plugins do whatever)
        if (!player.getWorld().equals(PopulationDensity.ManagedWorld)) return;

        //otherwise figure out which region that block is in
        Location blockLocation = block.getLocation();

        //region posts are at sea level at the lowest, so no need to check build permissions under that
        if (blockLocation.getBlockY() < PopulationDensity.instance.minimumRegionPostY) return;

        //whitelist for blocks which can always be broken (grass cutting, tree chopping)
        if (BlockEventHandler.alwaysBreakableMaterials.contains(block.getType())) return;

        RegionCoordinates blockRegion = RegionCoordinates.fromLocation(blockLocation);

        //if too close to (or above) region post, send an error message
        //note the ONLY way to edit around a region post is to have special permission
        if (!player.hasPermission("populationdensity.buildbreakanywhere") && this.nearRegionPost(blockLocation, blockRegion, PopulationDensity.instance.postProtectionRadius))
        {
            if (PopulationDensity.instance.buildRegionPosts)
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NoBreakPost);
            else
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NoBreakSpawn);
            breakEvent.setCancelled(true);
            return;
        }
    }

    private Location lastLocation = null;
    private Boolean lastResult = null;

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBlockFromTo(BlockFromToEvent event)
    {
        if (event.getFace() == BlockFace.DOWN) return;

        Location from = event.getBlock().getLocation();
        if (lastLocation != null && from.equals(lastLocation))
        {
            event.setCancelled(lastResult);
            return;
        }

        //if not in managed world, do nothing
        if (!from.getWorld().equals(PopulationDensity.ManagedWorld)) return;

        //region posts are at sea level at the lowest, so no need to check build permissions under that
        if (from.getY() < PopulationDensity.instance.minimumRegionPostY) return;

        RegionCoordinates blockRegion = RegionCoordinates.fromLocation(from);
        if (this.nearRegionPost(from, blockRegion, PopulationDensity.instance.postProtectionRadius + 1))
        {
            event.setCancelled(true);
            lastResult = true;
        } else
        {
            lastResult = false;
        }

        lastLocation = from;
    }

    //when a player places a block
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent placeEvent)
    {
        Player player = placeEvent.getPlayer();

        PopulationDensity.instance.resetIdleTimer(player);

        Block block = placeEvent.getBlock();

        //if not in managed world, do nothing
        if (!player.getWorld().equals(PopulationDensity.ManagedWorld)) return;

        Location blockLocation = block.getLocation();

        //if over hopper limit for chunk, send error message
        Material type = null;
        if (!player.hasPermission("populationdensity.unlimitedhoppers"))
        {
            type = block.getType();
            if (type == Material.HOPPER)
            {
                int hopperCount = 0;
                BlockState[] tiles = block.getChunk().getTileEntities();
                for (BlockState tile : tiles)
                {
                    if (tile.getType() == Material.HOPPER && ++hopperCount >= PopulationDensity.instance.config_maximumHoppersPerChunk)
                    {
                        placeEvent.setCancelled(true);
                        PopulationDensity.sendMessage(player, TextMode.Err, Messages.HopperLimitReached, String.valueOf(PopulationDensity.instance.config_maximumHoppersPerChunk));
                        return;
                    }
                }
            }
        }

        RegionCoordinates blockRegion = RegionCoordinates.fromLocation(blockLocation);

        //region posts are at sea level at the lowest, so no need to check build permissions under that
        if (blockLocation.getBlockY() < PopulationDensity.instance.minimumRegionPostY) return;

        //if too close to (or above) region post, send an error message
        if (!player.hasPermission("populationdensity.buildbreakanywhere") && this.nearRegionPost(blockLocation, blockRegion, PopulationDensity.instance.postProtectionRadius))
        {
            if (PopulationDensity.instance.buildRegionPosts)
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NoBuildPost);
            else
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NoBuildSpawn);
            placeEvent.setCancelled(true);
            return;
        }

        //if bed or chest and player has not been reminded about /movein this play session
        if (type == null) type = block.getType();
        if (beds.contains(type) || type == Material.CHEST)
        {
            PlayerData playerData = PopulationDensity.instance.dataStore.getPlayerData(player);
            if (playerData.advertisedMoveInThisSession) return;

            if (!playerData.homeRegion.equals(blockRegion))
            {
                PopulationDensity.sendMessage(player, TextMode.Warn, Messages.BuildingAwayFromHome);
                playerData.advertisedMoveInThisSession = true;
            }
        }

    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerBucketEmpty (PlayerBucketEmptyEvent event)
    {
        Player player = event.getPlayer();

        PopulationDensity.instance.resetIdleTimer(player);

        //if not in managed world, do nothing
        if (!player.getWorld().equals(PopulationDensity.ManagedWorld)) return;

        Block block = event.getBlockClicked().getRelative(event.getBlockFace());

        Location blockLocation = block.getLocation();

        //region posts are at sea level at the lowest, so no need to check build permissions under that
        if (blockLocation.getBlockY() < PopulationDensity.instance.minimumRegionPostY) return;

        RegionCoordinates blockRegion = RegionCoordinates.fromLocation(blockLocation);

        //if too close to (or above) region post, send an error message
        if (!player.hasPermission("populationdensity.buildbreakanywhere") && this.nearRegionPost(blockLocation, blockRegion, PopulationDensity.instance.postProtectionRadius))
        {
            if (PopulationDensity.instance.buildRegionPosts)
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NoBuildPost);
            else
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NoBuildSpawn);
            event.setCancelled(true);
            return;
        }
    }

    //when a player damages a block...
    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent damageEvent)
    {
        Player player = damageEvent.getPlayer();

        Block block = damageEvent.getBlock();
        if (player == null || (!Tag.WALL_SIGNS.isTagged(block.getType()) && !Tag.SIGNS.isTagged(block.getType()))) return;

        //if the player is not in managed world, do nothing
        if (!player.getWorld().equals(PopulationDensity.ManagedWorld)) return;

        if (!this.nearRegionPost(block.getLocation(), RegionCoordinates.fromLocation(block.getLocation()), 1)) return;

        PopulationDensity.sendMessage(player, TextMode.Instr, Messages.HelpMessage1, ChatColor.UNDERLINE + "" + ChatColor.AQUA + "http://bit.ly/mcregions");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPistonExtend(BlockPistonExtendEvent event)
    {
        Block pistonBlock = event.getBlock();

        if (!pistonBlock.getWorld().equals(PopulationDensity.ManagedWorld)) return;

        RegionCoordinates pistonRegion = RegionCoordinates.fromLocation(pistonBlock.getLocation());
        if (this.nearRegionPost(pistonBlock.getLocation(), pistonRegion, PopulationDensity.instance.postProtectionRadius + 12))
        {
            List<Block> blocks = event.getBlocks();
            for (Block block : blocks)
            {
                if (this.nearRegionPost(block.getLocation(), pistonRegion, PopulationDensity.instance.postProtectionRadius + 1))
                {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPistonRetract(BlockPistonRetractEvent event)
    {
        Block pistonBlock = event.getBlock();

        if (!pistonBlock.getWorld().equals(PopulationDensity.ManagedWorld)) return;

        RegionCoordinates pistonRegion = RegionCoordinates.fromLocation(pistonBlock.getLocation());
        if (this.nearRegionPost(pistonBlock.getLocation(), pistonRegion, PopulationDensity.instance.postProtectionRadius + 12))
        {
            List<Block> blocks = event.getBlocks();
            for (Block block : blocks)
            {
                if (this.nearRegionPost(block.getLocation(), pistonRegion, PopulationDensity.instance.postProtectionRadius))
                {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    //determines whether or not you're "near" a region post
    private boolean nearRegionPost(Location location, RegionCoordinates region, int howClose)
    {
        Location postLocation = PopulationDensity.getRegionCenter(region, false);

        //NOTE!  Why not use distance?  Because I want a box to the sky, not a sphere.
        //Why not round?  Below calculation is cheaper than distance (needed for a cylinder or sphere).
        //Why to the sky?  Because if somebody builds a platform above the post, folks will teleport onto that platform by mistake.
        //Also...  lava from above would be bad.
        //Why not below?  Because I can't imagine mining beneath a post as an avenue for griefing.

        return (location.getBlockX() >= postLocation.getBlockX() - howClose &&
                location.getBlockX() <= postLocation.getBlockX() + howClose &&
                location.getBlockZ() >= postLocation.getBlockZ() - howClose &&
                location.getBlockZ() <= postLocation.getBlockZ() + howClose &&
                location.getBlockY() >= PopulationDensity.ManagedWorld.getHighestBlockYAt(postLocation) - 5
        );
    }
}
