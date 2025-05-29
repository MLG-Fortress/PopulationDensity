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
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/**
 * Teleports a player. Useful as scheduled task so that a joining player may be teleported (otherwise error)
 */
class TeleportPlayerTask extends BukkitRunnable
{
    private PopulationDensity instance;
    private Player player;
    private Location destination;
    private boolean makeFallDamageImmune;
    DropShipTeleporter dropShipTeleporter;
    private List<Entity> entitiesToTeleport;

    public TeleportPlayerTask(Player player, Location destination, boolean makeFallDamageImmune, PopulationDensity plugin, DropShipTeleporter dropShipTeleporter, List<Entity> entitiesToTeleport)
    {
        this.player = player;
        this.destination = destination;
        this.makeFallDamageImmune = makeFallDamageImmune;
        this.instance = plugin;
        this.dropShipTeleporter = dropShipTeleporter;
        this.entitiesToTeleport = entitiesToTeleport;
    }

    public TeleportPlayerTask(Player player, Location destination, boolean makeFallDamageImmune, PopulationDensity plugin, List<Entity> entitiesToTeleport)
    {
        this(player, destination, makeFallDamageImmune, plugin, null, entitiesToTeleport);
    }

    @Override
    public void run()
    {
        // validate entities
        List<Entity> validEntities = entitiesToTeleport.stream().filter(
            entity -> entity instanceof LivingEntity && entity.isValid() && !entity.isDead()
        ).toList();

        // Teleport player first
        player.teleport(destination, TeleportCause.PLUGIN);
        if (this.makeFallDamageImmune && dropShipTeleporter != null)
        {
            dropShipTeleporter.makeEntityFallDamageImmune(player);
        }

        // Sound effect
        player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        // Teleport entities (config check already done in detectEntitiesToTeleport)
        for (Entity entity : validEntities)
        {
            LivingEntity livingEntity = (LivingEntity)entity;
            if (this.makeFallDamageImmune && dropShipTeleporter != null)
                dropShipTeleporter.makeEntityFallDamageImmune(livingEntity);
            entity.teleport(destination, TeleportCause.PLUGIN);
        }
    }
}
