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

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

class TeleportPlayerUtils
{
    // Private constructor to prevent instantiation
    private TeleportPlayerUtils() {}

    private static final EnumSet<EntityType> BOAT_TYPES = EnumSet.of(
            EntityType.BOAT,
            EntityType.CHEST_BOAT
    );

    /**
     * Detect entities that should be teleported with the player based on these rules:
     * - Villagers: Only if in boat AND config_teleportVillagersInBoat is enabled
     * - Tameable pets (cats, dogs, parrots): Only if owned by player and not sitting
     * - AbstractHorse (horses, camels, etc.): If owned by player OR leashed by player
     * - Other animals: Only if leashed by player
     */
    public static List<Entity> detectEntitiesToTeleport(Player player)
    {
        final UUID playerUUID = player.getUniqueId();
        final boolean teleportVillagersInBoat = PopulationDensity.instance.config_teleportVillagersInBoat;
        final boolean teleportAnimals = PopulationDensity.instance.config_teleportAnimals;

        List<Entity> entitiesToTeleport = new ArrayList<>();
        List<Entity> nearbyEntities = player.getNearbyEntities(5, player.getWorld().getMaxHeight(), 5);

        for (Entity entity : nearbyEntities)
        {
            // Skip non-living entities early
            if (!(entity instanceof LivingEntity) || entity instanceof Player) continue;

            boolean shouldTeleport = false;

            // 1. Handle Villagers - only if in boat and config allows
            if (entity.getType() == EntityType.VILLAGER)
            {
                shouldTeleport = teleportVillagersInBoat && isEntityInBoat(entity);
            }
            // 2. Handle regular tameable pets (excluding AbstractHorse)
            else if (entity instanceof Tameable && !(entity instanceof AbstractHorse))
            {
                shouldTeleport = isTamerOfEntity(entity, playerUUID) && !isSitting(entity);
            }
            // 3. Handle AbstractHorse entities - check both ownership and leashing
            // Do not check riding status, because the canTeleport method already unrides the vehicle
            else if (entity instanceof AbstractHorse)
            {
                // First check ownership (tameable behavior)
                if (isTamerOfEntity(entity, playerUUID) && !isSitting(entity))
                {
                    shouldTeleport = true;
                }
                else
                {
                    // If not owned, check if leashed (mount behavior)
                    LivingEntity creature = (LivingEntity) entity;
                    shouldTeleport = teleportAnimals
                      && creature.isLeashed()
                      && player.equals(creature.getLeashHolder());
                }
            }
            // 4. Handle other living entities - only if leashed
            else if (teleportAnimals)
            {
                LivingEntity creature = (LivingEntity) entity;
                shouldTeleport = creature.isLeashed() && player.equals(creature.getLeashHolder());
            }

            if (shouldTeleport)
            {
                entitiesToTeleport.add(entity);
            }
        }

        return new ArrayList<>(entitiesToTeleport);
    }

    // Check if an entity is currently in a boat
    private static boolean isEntityInBoat(Entity entity) {
        Entity vehicle = entity.getVehicle();
        return vehicle != null && BOAT_TYPES.contains(vehicle.getType());
    }

    // Check if the player is the owner of the tameable entity
    private static boolean isTamerOfEntity(Entity entity, UUID playerUUID)
    {
        if (!(entity instanceof Tameable)) return false;

        Tameable tameable = (Tameable) entity;
        return tameable.isTamed() && tameable.getOwner() != null &&
                playerUUID.equals(tameable.getOwner().getUniqueId());
    }

    // Check if the entity is sitting
    private static boolean isSitting(Entity entity)
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
