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
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.entity.Cat;

//teleports a player.  useful as scheduled task so that a joining player may be teleported (otherwise error)
class TeleportPlayerTask extends BukkitRunnable
{
    private PopulationDensity instance;
    private Player player;
    private Location destination;
    private boolean makeFallDamageImmune;
    DropShipTeleporter dropShipTeleporter;

    public TeleportPlayerTask(Player player, Location destination, boolean makeFallDamageImmune, PopulationDensity plugin, DropShipTeleporter dropShipTeleporter)
    {
        this.player = player;
        this.destination = destination;
        this.makeFallDamageImmune = makeFallDamageImmune;
        this.instance = plugin;
        this.dropShipTeleporter = dropShipTeleporter;
    }

    public TeleportPlayerTask(Player player, Location destination, boolean makeFallDamageImmune, PopulationDensity plugin)
    {
        this(player, destination, makeFallDamageImmune, plugin, null);
    }

    @Override
    public void run()
    {
        ArrayList<Entity> entitiesToTeleport = new ArrayList<>();

        List<Entity> nearbyEntities = player.getNearbyEntities(5, this.player.getWorld().getMaxHeight(), 5);

        for (Entity entity : nearbyEntities)
        {
            if (entity instanceof Tameable && !(entity instanceof AbstractHorse))
            {
                if (isTamerOfEntity(entity, player) && !isSitting(entity))
                {
                    entitiesToTeleport.add(entity);
                }
            } else if (entity instanceof Animals && !(entity instanceof AbstractHorse))
            {
                entitiesToTeleport.add(entity);
            }

            if (entity instanceof LivingEntity)
            {
                LivingEntity creature = (LivingEntity) entity;
                boolean isPlayerRiding = false;
                for (Entity passenger : creature.getPassengers())
                {
                    if (passenger instanceof Player && player.equals(passenger))
                    {
                        isPlayerRiding = true;
                    }
                }
                boolean isLeashedByPlayer = creature.isLeashed() && player.equals(creature.getLeashHolder());
                if (isLeashedByPlayer || isPlayerRiding)
                {
                    entitiesToTeleport.add(creature);
                }
            }
        }

        player.teleport(destination, TeleportCause.PLUGIN);
        if (this.makeFallDamageImmune)
        {
            dropShipTeleporter.makeEntityFallDamageImmune(player);
        }

        //sound effect
        player.playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        if (PopulationDensity.instance.config_teleportAnimals)
        {
            for (Entity entity : entitiesToTeleport)
            {
                if (!(entity instanceof LivingEntity)) continue;
                LivingEntity livingEntity = (LivingEntity)entity;
                if (this.makeFallDamageImmune)
                    dropShipTeleporter.makeEntityFallDamageImmune(livingEntity);
                entity.teleport(destination, TeleportCause.PLUGIN);
            }
        }
    }

    // Check if the player is the owner of the tameable entity
    private boolean isTamerOfEntity(Entity entity, Player player)
    {
        Tameable tameable = (Tameable) entity;
        return tameable.isTamed() && tameable.getOwner() != null &&
            player.getUniqueId().equals(tameable.getOwner().getUniqueId());
    }

    // Check if the entity is sitting
    private boolean isSitting(Entity entity)
    {
        EntityType type = entity.getType();
        switch (type)
        {
            case WOLF:
                return ((Wolf) entity).isSitting();
            case CAT:
                return ((Cat) entity).isSitting();
            case PARROT:
                return ((Parrot) entity).isSitting();
            default:
                return false;
        }
    }
}
