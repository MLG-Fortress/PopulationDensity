/*
    PopulationDensity Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

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

import io.papermc.lib.PaperLib;
import org.apache.commons.lang3.StringUtils;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class PopulationDensity extends JavaPlugin
{
    //for convenience, a reference to the instance of this plugin
    public static PopulationDensity instance;

    DropShipTeleporter dropShipTeleporterInstance;

    //for logging to the console and log file
    //Um wat why are we using the Minecraft logger... Oh shoot static is everywhere here :(
    //private static Logger log = Logger.getLogger("Minecraft");
    private static Logger log;

    //developer configuration, not modifiable by users
    public static final int REGION_SIZE = 400;

    //the world managed by this plugin
    public static World ManagedWorld;

    //the default world, not managed by this plugin
    //(may be null in some configurations)
    public static World CityWorld;

    //this handles data storage, like player and region data
    public DataStore dataStore;

    //tracks server perforamnce
    static float serverTicksPerSecond = 20;
    static int minutesLagging = 0;

    //lag-reducing measures
    static boolean grindersStopped = false;
    static boolean bootingIdlePlayersForLag = false;

    //user configuration, loaded/saved from a config.yml
    public boolean allowTeleportation;
    public boolean teleportFromAnywhere;
    public boolean newPlayersSpawnInHomeRegion;
    public boolean respawnInHomeRegion;
    public String cityWorldName;
    public String managedWorldName;
    public int maxDistanceFromSpawnToUseHomeRegion;
    public double densityRatio;
    public int maxIdleMinutes;
    public boolean enableLoginQueue;
    public int reservedSlotsForAdmins;
    public String queueMessage;
    public boolean automaticallyScanRegions;
    public boolean advancedScanStrategy;
    public int hoursBetweenScans;
    public boolean buildRegionPosts;
    public boolean newestRegionRequiresPermission;
    public boolean regrowGrass;
    public boolean respawnAnimals;
    public boolean regrowTrees;
    public boolean thinAnimalAndMonsterCrowds;
    public boolean thinIronGolemsToo;
    public boolean preciseWorldSpawn;
    public int woodMinimum;
    public int resourceMinimum;
    public Material postMaterialTop = Material.GLOWSTONE;
    public Material postMaterialMidTop = Material.GLOWSTONE;
    public Material postMaterialMidBottom = Material.GLOWSTONE;
    public Material postMaterialBottom = Material.GLOWSTONE;
    public Material outerPlatformMaterial = Material.STONE_BRICKS;
    public Material innerPlatformMaterial = Material.STONE_BRICKS;
    public int nearbyMonsterSpawnLimit;
    public int maxRegionNameLength = 10;
    public boolean abandonedFarmAnimalsDie;
    public boolean unusedMinecartsVanish;
    public boolean markRemovedEntityLocations;
    public boolean removeWildSkeletalHorses;

    public boolean config_captureSpigotTimingsWhenLagging;
    public boolean config_bootIdlePlayersWhenLagging;
    public boolean config_disableGrindersWhenLagging;
    public int config_maximumHoppersPerChunk;
    public boolean config_keepSpawnRegionPostLoaded;
    public boolean config_keepAllRegionPostsLoaded;
    boolean config_launchAndDropPlayers;
    boolean config_launchAndDropNewPlayers;
    boolean config_teleportAnimals = true;
    boolean config_teleportVillagersInBoat = false;

    public int minimumRegionPostY;

    public String[] topSignContent;
    public String[] sideSignContent;
    public String[] instructionsSignContent;
    public String[] mainCustomSignContent;
    public String[] northCustomSignContent;
    public String[] southCustomSignContent;
    public String[] eastCustomSignContent;
    public String[] westCustomSignContent;

    public int postProtectionRadius;

    boolean isSpigotServer = false;

    List<String> config_regionNames;

    public synchronized static void AddLogEntry(String entry)
    {
        log.info(entry);
    }

    //initializes well...   everything
    public void onEnable()
    {
        instance = this;
        log = instance.getLogger();

        try
        {
            Class.forName("org.spigotmc.SpigotConfig");
            isSpigotServer = true;
        }
        catch (ClassNotFoundException e) {}

        //load the config if it exists
        FileConfiguration config = YamlConfiguration.loadConfiguration(new File(DataStore.configFilePath));

        //prepare default setting for managed world...
        String defaultManagedWorldName = "";

        //build a list of normal environment worlds
        List<World> worlds = this.getServer().getWorlds();
        ArrayList<World> normalWorlds = new ArrayList<>();
        for (World world: worlds)
        {
            if (world.getEnvironment() == Environment.NORMAL)
            {
                normalWorlds.add(world);
            }
        }

        //if there's only one, make it the default
        if (normalWorlds.size() == 1)
        {
            defaultManagedWorldName = normalWorlds.get(0).getName();
        }

        //read configuration settings (note defaults)
        this.allowTeleportation = config.getBoolean("PopulationDensity.AllowTeleportation", true);
        this.teleportFromAnywhere = config.getBoolean("PopulationDensity.TeleportFromAnywhere", false);
        this.newPlayersSpawnInHomeRegion = config.getBoolean("PopulationDensity.NewPlayersSpawnInHomeRegion", true);
        this.respawnInHomeRegion = config.getBoolean("PopulationDensity.RespawnInHomeRegion", true);
        this.cityWorldName = config.getString("PopulationDensity.CityWorldName", "");
        this.maxDistanceFromSpawnToUseHomeRegion = config.getInt("PopulationDensity.MaxDistanceFromSpawnToUseHomeRegion", 25);
        this.managedWorldName = config.getString("PopulationDensity.ManagedWorldName", defaultManagedWorldName);
        this.densityRatio = config.getDouble("PopulationDensity.DensityRatio", 1.0);
        this.maxIdleMinutes = config.getInt("PopulationDensity.MaxIdleMinutes", 10);
        this.enableLoginQueue = config.getBoolean("PopulationDensity.LoginQueueEnabled", true);
        this.reservedSlotsForAdmins = config.getInt("PopulationDensity.ReservedSlotsForAdministrators", 1);
        if (this.reservedSlotsForAdmins < 0) this.reservedSlotsForAdmins = 0;
        this.queueMessage = config.getString("PopulationDensity.LoginQueueMessage", "%queuePosition% of %queueLength% in queue.  Reconnect within 3 minutes to keep your place.  :)");
        this.automaticallyScanRegions = config.getBoolean("PopulationDensity.AutomaticallyScanRegions", true);
        this.advancedScanStrategy = config.getBoolean("PopulationDensity.AdvancedScanStrategy", true);
        this.hoursBetweenScans = config.getInt("PopulationDensity.HoursBetweenScans", 6);
        this.buildRegionPosts = config.getBoolean("PopulationDensity.BuildRegionPosts", true);
        this.newestRegionRequiresPermission = config.getBoolean("PopulationDensity.NewestRegionRequiresPermission", false);
        this.regrowGrass = config.getBoolean("PopulationDensity.GrassRegrows", true);
        this.respawnAnimals = config.getBoolean("PopulationDensity.AnimalsRespawn", true);
        this.regrowTrees = config.getBoolean("PopulationDensity.TreesRegrow", true);
        this.config_maximumHoppersPerChunk = config.getInt("PopulationDensity.Maximum Hoppers Per Chunk", 10);
        this.thinAnimalAndMonsterCrowds = config.getBoolean("PopulationDensity.ThinOvercrowdedAnimalsAndMonsters", true);
        this.thinIronGolemsToo = config.getBoolean("PopulationDensity.ThinOvercrowdedIronGolems", this.thinIronGolemsToo);
        this.minimumRegionPostY = config.getInt("PopulationDensity.MinimumRegionPostY", 62);
        this.preciseWorldSpawn = config.getBoolean("PopulationDensity.PreciseWorldSpawn", false);
        this.woodMinimum = config.getInt("PopulationDensity.MinimumWoodAvailableToPlaceNewPlayers", 200);
        this.resourceMinimum = config.getInt("PopulationDensity.MinimumResourceScoreToPlaceNewPlayers", 200);
        this.postProtectionRadius = config.getInt("PopulationDensity.PostProtectionDistance", 2);
        this.maxRegionNameLength = config.getInt("PopulationDensity.Maximum Region Name Length", 10);

        this.config_disableGrindersWhenLagging = config.getBoolean("PopulationDensity.Disable Monster Grinders When Lagging", true);
        this.config_bootIdlePlayersWhenLagging = config.getBoolean("PopulationDensity.Boot Idle Players When Lagging", true);
        this.config_captureSpigotTimingsWhenLagging = config.getBoolean("PopulationDensity.Capture Spigot Timings When Lagging", false);
        this.config_keepSpawnRegionPostLoaded = config.getBoolean("PopulationDensity.KeepSpawnRegionPostLoaded", true);
        this.config_keepAllRegionPostsLoaded = config.getBoolean("PopulationDensity.KeepAllRegionPostsLoaded", false);
        this.config_launchAndDropPlayers = config.getBoolean("PopulationDensity.LaunchAndDropPlayers", true);
        this.config_launchAndDropNewPlayers = config.getBoolean("PopulationDensity.LaunchAndDropNewPlayers", config_launchAndDropPlayers);
        this.config_teleportAnimals = config.getBoolean("PopulationDensity.TeleportAnimals", config_teleportAnimals);
        this.config_teleportVillagersInBoat = config.getBoolean("PopulationDensity.TeleportVillagersInBoat", config_teleportVillagersInBoat);

        String top = config.getString("PopulationDensity.PostDesign.TopBlock", "GLOWSTONE");
        String midTop = config.getString("PopulationDensity.PostDesign.MidTopBlock", "GLOWSTONE");
        String midBottom = config.getString("PopulationDensity.PostDesign.MidBottomBlock", "GLOWSTONE");
        String bottom = config.getString("PopulationDensity.PostDesign.BottomBlock", "GLOWSTONE");
        String outerPlat = config.getString("PopulationDensity.PostDesign.PlatformOuterRing", "STONE_BRICKS");
        String innerPlat = config.getString("PopulationDensity.PostDesign.PlatformInnerRing", "STONE_BRICKS");
        this.nearbyMonsterSpawnLimit = config.getInt("PopulationDensity.Max Monsters In Chunk To Spawn More", 2);
        this.nearbyMonsterSpawnLimit = config.getInt("PopulationDensity.Max Monsters Nearby For More To Spawn", nearbyMonsterSpawnLimit);
        this.abandonedFarmAnimalsDie = config.getBoolean("PopulationDensity.Abandoned Farm Animals Die", true);
        this.unusedMinecartsVanish = config.getBoolean("PopulationDensity.Unused Minecarts Vanish", true);
        this.markRemovedEntityLocations = config.getBoolean("PopulationDensity.MarkRemovedAnimalLocationsWithShrubs", true);
        this.removeWildSkeletalHorses = config.getBoolean("PopulationDensity.Remove Wild Skeletal Horses", true);

        Material resultT = this.processMaterials(top);
        if (resultT != null)
        {
            this.postMaterialTop = resultT;
        }

        Material resultMT = this.processMaterials(midTop);
        if (resultMT != null)
        {
            this.postMaterialMidTop = resultMT;
        }

        Material resultMB = this.processMaterials(midBottom);
        if (resultMB != null)
        {
            this.postMaterialMidBottom = resultMB;
        }

        Material resultB = this.processMaterials(bottom);
        if (resultB != null)
        {
            this.postMaterialBottom = resultB;
        }

        Material resultO = this.processMaterials(outerPlat);
        if (resultO != null)
        {
            this.outerPlatformMaterial = resultO;
        }

        Material resultI = this.processMaterials(innerPlat);
        if (resultI != null)
        {
            this.innerPlatformMaterial = resultI;
        }

        List<String> defaultRegionNames = Arrays.asList(
                "redstone",
                "dew",
                "creeper",
                "sword",
                "wintersebb",
                "fjord",
                "vista",
                "breeze",
                "tide",
                "stream",
                "glenwood",
                "journey",
                "cragstone",
                "pickaxe",
                "axe",
                "hammer",
                "anvil",
                "mist",
                "sunrise",
                "sunset",
                "copper",
                "coal",
                "shovel",
                "minecart",
                "railway",
                "dig",
                "chasm",
                "basalt",
                "agate",
                "boat",
                "grass",
                "gust",
                "ruby",
                "emerald",
                "stone",
                "peak",
                "ore",
                "boulder",
                "hilltop",
                "horizon",
                "fog",
                "cloud",
                "canopy",
                "gravel",
                "torch",
                "obsidian",
                "treetop",
                "storm",
                "gold",
                "canopy",
                "leaf",
                "summit",
                "glade",
                "trail",
                "seed",
                "diamond",
                "armor",
                "sand",
                "flint",
                "field",
                "steel",
                "helm",
                "gorge",
                "campfire",
                "workshop",
                "rubble",
                "iron",
                "chisel",
                "moon",
                "shrub",
                "zombie",
                "stem",
                "vale",
                "pumpkin",
                "lantern",
                "copper",
                "moonBeam",
                "soil",
                "dust"
        );

        this.config_regionNames = new ArrayList<>();
        List<String> regionNames = config.getStringList("PopulationDensity.Region Name List");
        if (regionNames == null || regionNames.isEmpty())
        {
            regionNames = defaultRegionNames;
        }

        for (String regionName : regionNames)
        {
            String error = getRegionNameError(regionName, true);
            if (error != null)
            {
                AddLogEntry("Unable to use region name + '" + regionName + "':" + error);
            } else
            {
                this.config_regionNames.add(regionName);
            }
        }

        //and write those values back and save. this ensures the config file is available on disk for editing
        FileConfiguration outConfig = new YamlConfiguration();
        outConfig.set("PopulationDensity.NewPlayersSpawnInHomeRegion", this.newPlayersSpawnInHomeRegion);
        outConfig.set("PopulationDensity.RespawnInHomeRegion", this.respawnInHomeRegion);
        outConfig.set("PopulationDensity.CityWorldName", this.cityWorldName);
        outConfig.set("PopulationDensity.AllowTeleportation", this.allowTeleportation);
        outConfig.set("PopulationDensity.TeleportFromAnywhere", this.teleportFromAnywhere);
        outConfig.set("PopulationDensity.MaxDistanceFromSpawnToUseHomeRegion", this.maxDistanceFromSpawnToUseHomeRegion);
        outConfig.set("PopulationDensity.ManagedWorldName", this.managedWorldName);
        outConfig.set("PopulationDensity.DensityRatio", this.densityRatio);
        outConfig.set("PopulationDensity.MaxIdleMinutes", this.maxIdleMinutes);
        outConfig.set("PopulationDensity.LoginQueueEnabled", this.enableLoginQueue);
        outConfig.set("PopulationDensity.ReservedSlotsForAdministrators", this.reservedSlotsForAdmins);
        outConfig.set("PopulationDensity.LoginQueueMessage", this.queueMessage);
        outConfig.set("PopulationDensity.AutomaticallyScanRegions", this.automaticallyScanRegions);
        outConfig.set("PopulationDensity.AdvancedScanStrategy", this.advancedScanStrategy);
        outConfig.set("PopulationDensity.HoursBetweenScans", this.hoursBetweenScans);
        outConfig.set("PopulationDensity.BuildRegionPosts", this.buildRegionPosts);
        outConfig.set("PopulationDensity.NewestRegionRequiresPermission", this.newestRegionRequiresPermission);
        outConfig.set("PopulationDensity.GrassRegrows", this.regrowGrass);
        outConfig.set("PopulationDensity.AnimalsRespawn", this.respawnAnimals);
        outConfig.set("PopulationDensity.TreesRegrow", this.regrowTrees);
        outConfig.set("PopulationDensity.Max Monsters Nearby For More To Spawn", this.nearbyMonsterSpawnLimit);
        outConfig.set("PopulationDensity.ThinOvercrowdedAnimalsAndMonsters", this.thinAnimalAndMonsterCrowds);
        outConfig.set("PopulationDensity.ThinOvercrowdedIronGolems", this.thinIronGolemsToo);
        outConfig.set("PopulationDensity.Abandoned Farm Animals Die", this.abandonedFarmAnimalsDie);
        outConfig.set("PopulationDensity.Unused Minecarts Vanish", this.unusedMinecartsVanish);
        outConfig.set("PopulationDensity.MarkRemovedAnimalLocationsWithShrubs", this.markRemovedEntityLocations);
        outConfig.set("PopulationDensity.Remove Wild Skeletal Horses", this.removeWildSkeletalHorses);
        outConfig.set("PopulationDensity.Disable Monster Grinders When Lagging", this.config_disableGrindersWhenLagging);
        outConfig.set("PopulationDensity.Maximum Hoppers Per Chunk", this.config_maximumHoppersPerChunk);
        outConfig.set("PopulationDensity.Boot Idle Players When Lagging", this.config_bootIdlePlayersWhenLagging);
        outConfig.set("PopulationDensity.Capture Spigot Timings When Lagging", this.config_captureSpigotTimingsWhenLagging);
        outConfig.set("PopulationDensity.KeepSpawnRegionPostLoaded", this.config_keepSpawnRegionPostLoaded);
        outConfig.set("PopulationDensity.KeepAllRegionPostsLoaded", this.config_keepAllRegionPostsLoaded);
        outConfig.set("PopulationDensity.LaunchAndDropPlayers", this.config_launchAndDropPlayers);
        outConfig.set("PopulationDensity.LaunchAndDropNewPlayers", this.config_launchAndDropNewPlayers);
        outConfig.set("PopulationDensity.TeleportAnimals", this.config_teleportAnimals);
        outConfig.set("PopulationDensity.TeleportVillagersInBoat", this.config_teleportVillagersInBoat);
        outConfig.set("PopulationDensity.MinimumRegionPostY", this.minimumRegionPostY);
        outConfig.set("PopulationDensity.PreciseWorldSpawn", this.preciseWorldSpawn);
        outConfig.set("PopulationDensity.MinimumWoodAvailableToPlaceNewPlayers", this.woodMinimum);
        outConfig.set("PopulationDensity.MinimumResourceScoreToPlaceNewPlayers", this.resourceMinimum);
        outConfig.set("PopulationDensity.PostProtectionDistance", this.postProtectionRadius);
        outConfig.set("PopulationDensity.Maximum Region Name Length", this.maxRegionNameLength);
        outConfig.set("PopulationDensity.PostDesign.TopBlock", top);
        outConfig.set("PopulationDensity.PostDesign.MidTopBlock", midTop);
        outConfig.set("PopulationDensity.PostDesign.MidBottomBlock", midBottom);
        outConfig.set("PopulationDensity.PostDesign.BottomBlock", bottom);
        outConfig.set("PopulationDensity.PostDesign.PlatformOuterRing", outerPlat);
        outConfig.set("PopulationDensity.PostDesign.PlatformInnerRing", innerPlat);
        outConfig.set("PopulationDensity.Region Name List", regionNames);

        //this is a combination load/preprocess/save for custom signs on the region posts
        this.topSignContent = this.initializeSignContentConfig(config, outConfig, "PopulationDensity.Signs.Top", new String[]{"", "%regionName%", "Region", ""});
        this.sideSignContent = this.initializeSignContentConfig(config, outConfig, "PopulationDensity.Signs.Side", new String[]{"<--", "%regionName%", "Region", "<--"});
        this.instructionsSignContent = this.initializeSignContentConfig(config, outConfig, "PopulationDensity.Signs.Instructions", new String[]{"Teleport", "From Here!", "Punch For", "Instructions"});

        this.mainCustomSignContent = this.initializeSignContentConfig(config, outConfig, "PopulationDensity.CustomSigns.Main", new String[]{"", "Population", "Density", ""});
        this.northCustomSignContent = this.initializeSignContentConfig(config, outConfig, "PopulationDensity.CustomSigns.North", new String[]{"", "", "", ""});
        this.southCustomSignContent = this.initializeSignContentConfig(config, outConfig, "PopulationDensity.CustomSigns.South", new String[]{"", "", "", ""});
        this.eastCustomSignContent = this.initializeSignContentConfig(config, outConfig, "PopulationDensity.CustomSigns.East", new String[]{"", "", "", ""});
        this.westCustomSignContent = this.initializeSignContentConfig(config, outConfig, "PopulationDensity.CustomSigns.West", new String[]{"", "", "", ""});

        try
        {
            outConfig.save(DataStore.configFilePath);
        }
        catch (IOException exception)
        {
            AddLogEntry("Unable to write to the configuration file at \"" + DataStore.configFilePath + "\"");
        }

        //get a reference to the managed world
        if (this.managedWorldName == null || this.managedWorldName.isEmpty())
        {
            PopulationDensity.AddLogEntry("Please specify a world to manage in config.yml.");
            return;
        }
        ManagedWorld = this.getServer().getWorld(this.managedWorldName);
        if (ManagedWorld == null)
        {
            PopulationDensity.AddLogEntry("Could not find a world named \"" + this.managedWorldName + "\".  Please update your config.yml.");
            return;
        }

        //when datastore initializes, it loads player and region data, and posts some stats to the log
        this.dataStore = new DataStore(this.config_regionNames);

        //register for events
        PluginManager pluginManager = this.getServer().getPluginManager();

        //set up region name tab completers
        PluginCommand visitCommand = this.getCommand("visit");
        visitCommand.setTabCompleter(this.dataStore);

        //player events, to control spawn, respawn, disconnect, and region-based notifications as players walk around
        PlayerEventHandler playerEventHandler = new PlayerEventHandler(this.dataStore, this);
        pluginManager.registerEvents(playerEventHandler, this);

        //block events, to limit building around region posts and in some other cases (config dependent)
        BlockEventHandler blockEventHandler = new BlockEventHandler();
        pluginManager.registerEvents(blockEventHandler, this);

        //entity events, to protect region posts from explosions
        EntityEventHandler entityEventHandler = new EntityEventHandler(this);
        pluginManager.registerEvents(entityEventHandler, this);

        //world events, to generate region posts when chunks load
        WorldEventHandler worldEventHandler = new WorldEventHandler();
        pluginManager.registerEvents(worldEventHandler, this);

        //Only load listeners if LaunchAndDrop is enabled in config
        if (config_launchAndDropPlayers || config_launchAndDropNewPlayers)
        {
            this.dropShipTeleporterInstance = new DropShipTeleporter(this);
            pluginManager.registerEvents(dropShipTeleporterInstance, this);
        }

        //make a note of the spawn world.  may be NULL if the configured city world name doesn't match an existing world
        CityWorld = this.getServer().getWorld(this.cityWorldName);
        if (!this.cityWorldName.isEmpty() && CityWorld == null)
        {
            PopulationDensity.AddLogEntry("Could not find a world named \"" + this.cityWorldName + "\".  Please update your config.yml.");
        }

        //scan the open region for resources and open a new one as necessary
        //may open and close several regions before finally leaving an "acceptable" region open
        //this will repeat every six hours
        if (this.automaticallyScanRegions)
        {
            // the scan open region task check the scan strategy and calls the appropriate scan method
            this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new ScanOpenRegionTask(), 5L, this.hoursBetweenScans * 60 * 60 * 20L);
        }

        //start monitoring performance
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new MonitorPerformanceTask(), 1200L, 1200L);

        //animals which appear abandoned on chunk load get the grandfather clause treatment
        for (World world : this.getServer().getWorlds())
        {
            for (Chunk chunk : world.getLoadedChunks())
            {
                Entity[] entities = chunk.getEntities();
                for (Entity entity : entities)
                {
                    if (WorldEventHandler.isAbandoned(entity))
                    {
                        entity.setTicksLived(1);
                    }
                }
            }
        }
        try
        {
            Metrics metrics = new Metrics(this, 3343);
            metrics.addCustomChart(new SimplePie("bukkit_impl", this::getVersionImplementation));
        }
        catch (Exception ignored)
        {
            PopulationDensity.AddLogEntry("Failed to submit metrics data.");
        }
    }

    private String getVersionImplementation() {
        return getServer().getVersion().split("-")[1];
    }

    String getRegionNameError(String name, boolean console)
    {
        if (name.length() > this.maxRegionNameLength)
        {
            if (console)
                return "Name too long.";
            else
                return this.dataStore.getMessage(Messages.RegionNameLength, String.valueOf(maxRegionNameLength));
        }

        for (int i = 0; i < name.length(); i++)
        {
            char c = name.charAt(i);
            if (!Character.isLetter(c) && !Character.isDigit(c) && c != ' ')
            {
                if (console)
                    return "Name includes symbols or puncutation.";
                else
                    return this.dataStore.getMessage(Messages.RegionNamesOnlyLettersAndNumbers);
            }
        }

        return null;
    }

    public String[] initializeSignContentConfig(FileConfiguration config, FileConfiguration outConfig, String configurationNode, String[] defaultLines)
    {
        //read what's in the file
        List<String> linesFromConfig = config.getStringList(configurationNode);

        //if nothing, replace with default
        int i = 0;
        if (linesFromConfig == null || linesFromConfig.isEmpty())
        {
            for (; i < defaultLines.length && i < 4; i++)
            {
                linesFromConfig.add(defaultLines[i]);
            }
        }

        //fill any blanks
        for (i = linesFromConfig.size(); i < 4; i++)
        {
            linesFromConfig.add("");
        }

        //write it back to the config file
        outConfig.set(configurationNode, linesFromConfig);

        //would the sign be empty?
        boolean emptySign = true;
        for (i = 0; i < 4; i++)
        {
            if (linesFromConfig.get(i).length() > 0)
            {
                emptySign = false;
                break;
            }
        }

        //return end result
        if (emptySign)
        {
            return null;
        } else
        {
            String[] returnArray = new String[4];
            for (i = 0; i < 4 && i < linesFromConfig.size(); i++)
            {
                returnArray[i] = linesFromConfig.get(i);
            }

            return returnArray;
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
    {
        Player player = null;
        PlayerData playerData = null;
        if (sender instanceof Player)
        {
            player = (Player)sender;

            if (ManagedWorld == null)
            {
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NoManagedWorld);
                return true;
            }

            playerData = this.dataStore.getPlayerData(player);
        }

        if (cmd.getName().equalsIgnoreCase("visit") && player != null)
        {
            if (args.length < 1) return false;

            CanTeleportResult result = this.playerCanTeleport(player, false);
            if (!result.canTeleport) return true;

            @SuppressWarnings("deprecation")
            Player targetPlayer = this.getServer().getPlayerExact(args[0]);
            if (targetPlayer != null && player.canSee(targetPlayer))
            {
                PlayerData targetPlayerData = this.dataStore.getPlayerData(targetPlayer);
                if (playerData.inviter != null && playerData.inviter.getName().equals(targetPlayer.getName()))
                {
                    if (result.nearPost && this.launchPlayer(player))
                    {
                        this.teleportPlayer(player, targetPlayerData.homeRegion, 1);
                    } else
                    {
                        this.teleportPlayer(player, targetPlayerData.homeRegion, 0);
                    }

                } else if (this.dataStore.getRegionName(targetPlayerData.homeRegion) == null)
                {
                    PopulationDensity.sendMessage(player, TextMode.Err, Messages.InvitationNeeded, targetPlayer.getName());
                    return true;
                } else
                {
                    if (this.launchPlayer(player))
                    {
                        this.teleportPlayer(player, targetPlayerData.homeRegion, 1);
                    } else
                    {
                        this.teleportPlayer(player, targetPlayerData.homeRegion, 0);
                    }
                }

                PopulationDensity.sendMessage(player, TextMode.Success, Messages.VisitConfirmation, targetPlayer.getName());
            } else
            {
                //find the specified region, and send an error message if it's not found
                String name = String.join(" ", args);
                RegionCoordinates region = this.dataStore.getRegionCoordinates(name);
                if (region == null)
                {
                    PopulationDensity.sendMessage(player, TextMode.Err, Messages.DestinationNotFound, name);
                    return true;
                }

                //otherwise, teleport the user to the specified region
                if (this.launchPlayer(player))
                {
                    this.teleportPlayer(player, region, 1);
                } else
                {
                    this.teleportPlayer(player, region, 0);
                }
            }

            return true;
        } else if (cmd.getName().equalsIgnoreCase("newestregion") && player != null)
        {
            //check permission, if necessary
            if (this.newestRegionRequiresPermission && !player.hasPermission("populationdensity.newestregion"))
            {
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NeedNewestRegionPermission);
                return true;
            }

            CanTeleportResult result = this.playerCanTeleport(player, false);
            if (!result.canTeleport) return true;

            //teleport the user to the open region
            RegionCoordinates openRegion = this.dataStore.getOpenRegion();
            if (result.nearPost && this.launchPlayer(player))
            {
                this.teleportPlayer(player, openRegion, 1);
            } else
            {
                this.teleportPlayer(player, openRegion, 0);
            }

            PopulationDensity.sendMessage(player, TextMode.Success, Messages.NewestRegionConfirmation);

            return true;
        } else if (cmd.getName().equalsIgnoreCase("whichregion") && player != null)
        {
            RegionCoordinates currentRegion = RegionCoordinates.fromLocation(player.getLocation());
            if (currentRegion == null)
            {
                PopulationDensity.sendMessage(player, TextMode.Warn, Messages.NotInRegion);
                return true;
            }

            String regionName = this.dataStore.getRegionName(currentRegion);
            if (regionName == null)
            {
                PopulationDensity.sendMessage(player, TextMode.Info, Messages.UnnamedRegion);
            } else
            {
                PopulationDensity.sendMessage(player, TextMode.Info, Messages.WhichRegion, capitalize(regionName));
            }
            Location postLocation = getRegionCenter(currentRegion, true);
            player.sendMessage(ChatColor.AQUA + "The region post can be found at (X: " 
                + Math.round(postLocation.getX()) + ", Y: " + Math.round(postLocation.getY())
                + ", Z: " + Math.round(postLocation.getZ()) + ")" + ".");

            return true;
        } else if (cmd.getName().equalsIgnoreCase("listregions"))
        {
            PopulationDensity.sendMessage(player, TextMode.Info, this.dataStore.getRegionNames());

            return true;
        } else if (cmd.getName().equalsIgnoreCase("nameregion") && player != null)
        {
            return this.nameRegion(player, args, false);
        } else if (cmd.getName().equalsIgnoreCase("renameregion") && player != null)
        {
            return this.nameRegion(player, args, true);
        } else if (cmd.getName().equalsIgnoreCase("addregionpost") && player != null)
        {
            RegionCoordinates currentRegion = RegionCoordinates.fromLocation(player.getLocation());
            if (currentRegion == null)
            {
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NotInRegion);
                return true;
            }

            try
            {
                this.dataStore.AddRegionPost(currentRegion);
            }
            catch (ChunkLoadException e) {}  //ignore.  post will be auto-built when the chunk is loaded later

            return true;
        } else if (cmd.getName().equalsIgnoreCase("homeregion") && player != null)
        {
            return this.handleHomeCommand(player, playerData);
        } else if (cmd.getName().equalsIgnoreCase("cityregion") && player != null)
        {
            //if city world isn't defined, send the player home
            if (CityWorld == null)
            {
                return this.handleHomeCommand(player, playerData);
            }

            //otherwise teleportation is enabled, so consider config, player location, player permissions
            CanTeleportResult result = this.playerCanTeleport(player, true);
            if (result.canTeleport)
            {
                Location spawn = CityWorld.getSpawnLocation();

                Block block = spawn.getBlock();
                while (block.getType().isSolid())
                {
                    block = block.getRelative(BlockFace.UP);
                }

                List<Entity> entitiesToTeleport = TeleportPlayerUtils.detectEntitiesToTeleport(player);
                if (result.nearPost && this.launchPlayer(player))
                    new TeleportPlayerTask(player, block.getLocation(), false, instance, entitiesToTeleport).runTaskLater(this, 20L);
                else
                    new TeleportPlayerTask(player, block.getLocation(), false, instance, entitiesToTeleport).runTaskLater(this, 0L);
            }

            return true;
        } else if (cmd.getName().equalsIgnoreCase("randomregion") && player != null)
        {
            CanTeleportResult result = this.playerCanTeleport(player, false);
            if (!result.canTeleport) return true;

            RegionCoordinates randomRegion = this.dataStore.getRandomRegion(RegionCoordinates.fromLocation(player.getLocation()));

            if (randomRegion == null)
            {
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NoMoreRegions);
                return true;
            } 
            
            if (result.nearPost && this.launchPlayer(player))
            {
                this.teleportPlayer(player, randomRegion, 1);
            } else
            {
                this.teleportPlayer(player, randomRegion, 0);
            }

            return true;
        } else if (cmd.getName().equalsIgnoreCase("invite") && player != null)
        {
            if (args.length < 1) return false;

            //send a notification to the invitee, if he's available
            Player invitee = this.getServer().getPlayer(args[0]);
            if (invitee != null && player.canSee(invitee))
            {
                playerData = this.dataStore.getPlayerData(invitee);
                if (playerData.inviter == player)
                {
                    PopulationDensity.sendMessage(player, TextMode.Success, Messages.InviteAlreadySent, invitee.getName(), player.getName());
                    return true;
                }
                playerData.inviter = player;
                PopulationDensity.sendMessage(player, TextMode.Success, Messages.InviteConfirmation, invitee.getName(), player.getName());
                PopulationDensity.sendMessage(invitee, TextMode.Success, Messages.InviteNotification, player.getName());
                PopulationDensity.sendMessage(invitee, TextMode.Instr, Messages.InviteInstruction, player.getName());
            } else
            {
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.PlayerNotFound, args[0]);
            }

            return true;
        } else if (cmd.getName().equalsIgnoreCase("sendregion"))
        {
            if (args.length < 1) return false;

            Player targetPlayer = this.getServer().getPlayerExact(args[0]);
            if (targetPlayer != null)
            {
                playerData = this.dataStore.getPlayerData(targetPlayer);
                RegionCoordinates destination = playerData.homeRegion;
                if (args.length > 1)
                {
                    String name = PopulationDensity.join(args, 1);
                    destination = this.dataStore.getRegionCoordinates(name);
                    if (destination == null)
                    {
                        PopulationDensity.sendMessage(player, TextMode.Err, Messages.DestinationNotFound, name);
                        return true;
                    }
                }

                //otherwise, teleport the target player to the destination region                  
                this.teleportPlayer(targetPlayer, destination, 0);
                PopulationDensity.sendMessage(player, TextMode.Success, Messages.PlayerMoved);
            } else
            {
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.PlayerNotFound, args[0]);
            }

            return true;
        } else if (cmd.getName().equalsIgnoreCase("sethomeregion") && player != null)
        {
            //if not in the managed world, /movein doesn't make sense
            if (!player.getWorld().equals(ManagedWorld))
            {
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.NotInRegion);
                return true;
            }

            playerData.homeRegion = RegionCoordinates.fromLocation(player.getLocation());
            this.dataStore.savePlayerData(player, playerData);
            PopulationDensity.sendMessage(player, TextMode.Success, Messages.SetHomeConfirmation);
            PopulationDensity.sendMessage(player, TextMode.Instr, Messages.SetHomeInstruction1);
            PopulationDensity.sendMessage(player, TextMode.Instr, Messages.SetHomeInstruction2);

            return true;
        } else if (cmd.getName().equalsIgnoreCase("addregion") && player != null)
        {
            PopulationDensity.sendMessage(player, TextMode.Success, Messages.AddRegionConfirmation);

            RegionCoordinates newRegion = this.dataStore.addRegion();
            // the scan open region task check the scan strategy and calls the appropriate scan method
            this.scanRegion(newRegion, true);

            return true;
        } else if (cmd.getName().equalsIgnoreCase("scanregion") && player != null)
        {
            PopulationDensity.sendMessage(player, TextMode.Success, Messages.ScanStartConfirmation);
            // the scan open region task check the scan strategy and calls the appropriate scan method
            this.scanRegion(RegionCoordinates.fromLocation(player.getLocation()), false);

            return true;
        }  else if (cmd.getName().equalsIgnoreCase("scanregionoriginal") && player != null)
        {
            PopulationDensity.sendMessage(player, TextMode.Success, Messages.ScanStartConfirmation);
            this.scanRegionOriginal(RegionCoordinates.fromLocation(player.getLocation()), false);

            return true;
        } else if (cmd.getName().equalsIgnoreCase("loginpriority"))
        {
            //requires exactly two parameters, the other player's name and the priority
            if (args.length != 2 && args.length != 1) return false;

            PlayerData targetPlayerData = null;
            OfflinePlayer targetPlayer = null;
            if (args.length > 0)
            {
                //find the specified player
                targetPlayer = this.resolvePlayer(args[0]);
                if (targetPlayer == null)
                {
                    PopulationDensity.sendMessage(player, TextMode.Err, Messages.PlayerNotFound, args[0]);
                    return true;
                }

                targetPlayerData = this.dataStore.getPlayerData(targetPlayer);

                PopulationDensity.sendMessage(player, TextMode.Info, Messages.LoginPriorityCheck, targetPlayer.getName(), String.valueOf(targetPlayerData.loginPriority));

                if (args.length < 2) return false;  //usage displayed

                //parse the adjustment amount
                int priority;
                try
                {
                    priority = Integer.parseInt(args[1]);
                }
                catch (NumberFormatException numberFormatException)
                {
                    return false;  //causes usage to be displayed
                }

                //set priority
                if (priority > 100) priority = 100;
                else if (priority < 0) priority = 0;

                targetPlayerData.loginPriority = priority;
                this.dataStore.savePlayerData(targetPlayer, targetPlayerData);

                //confirmation message
                PopulationDensity.sendMessage(player, TextMode.Success, Messages.LoginPriorityUpdate, targetPlayer.getName(), String.valueOf(priority));

                return true;
            }
        } else if (cmd.getName().equalsIgnoreCase("thinentities"))
        {
            if (player != null)
            {
                PopulationDensity.sendMessage(player, TextMode.Success, Messages.ThinningConfirmation);
            }

            MonitorPerformanceTask.thinEntities();

            return true;
        } else if (cmd.getName().equalsIgnoreCase("simlag") && player == null)
        {
            float tps;
            try
            {
                tps = Float.parseFloat(args[0]);
            }
            catch (NumberFormatException e)
            {
                return false;
            }

            MonitorPerformanceTask.treatLag(tps);
            return true;
        } else if (cmd.getName().equalsIgnoreCase("lag"))
        {
            this.reportTPS(player);

            return true;
        } else if (cmd.getName().equalsIgnoreCase("deleteRegion"))
        {
            if (args.length == 0)
                return false;

            String name = PopulationDensity.join(args, 1);
            RegionCoordinates targetRegion = this.dataStore.getRegionCoordinates(name);
            if (targetRegion == null)
            {
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.DestinationNotFound, name);
                return true;
            }
            dataStore.deleteRegion(targetRegion);
            sender.sendMessage(name + " deleted.");
            return true;
        }

        return false;
    }

    private boolean nameRegion(Player player, String[] args, boolean allowRename)
    {
        RegionCoordinates currentRegion = RegionCoordinates.fromLocation(player.getLocation());
        if (currentRegion == null)
        {
            PopulationDensity.sendMessage(player, TextMode.Warn, Messages.NotInRegion);
            return true;
        }

        if (!allowRename && this.dataStore.getRegionName(currentRegion) != null)
        {
            PopulationDensity.sendMessage(player, TextMode.Err, Messages.RegionAlreadyNamed);
            return true;
        }

        //validate argument
        if (args.length < 1) return false;
        String name = String.join(" ", args);

        if (this.dataStore.getRegionCoordinates(name) != null)
        {
            PopulationDensity.sendMessage(player, TextMode.Err, Messages.RegionNameConflict);
            return true;
        }

        //name region
        try
        {
            this.dataStore.nameRegion(currentRegion, name);
        }
        catch (RegionNameException e)
        {
            PopulationDensity.sendMessage(player, TextMode.Err, e.getMessage());
            return true;
        }

        //update post
        try
        {
            this.dataStore.AddRegionPost(currentRegion);
        }
        catch (ChunkLoadException e) {}  //ignore.  post will be auto-rebuilt when the chunk is loaded later

        return true;
    }

    void reportTPS(Player player)
    {
        String message = PopulationDensity.instance.dataStore.getMessage(Messages.PerformanceScore, String.valueOf(Math.round((serverTicksPerSecond / 20) * 100)));
        if (serverTicksPerSecond > 19)
        {
            message = PopulationDensity.instance.dataStore.getMessage(Messages.PerformanceScore_NoLag) + message;
        } else
        {
            message += PopulationDensity.instance.dataStore.getMessage(Messages.PerformanceScore_Lag);
        }

        if (player != null)
        {
            player.sendMessage(ChatColor.GOLD + message);
        } else
        {
            AddLogEntry(message);
        }
    }

    private static String join(String[] args, int offset)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = offset; i < args.length; i++)
        {
            builder.append(args[i]).append(" ");
        }

        return builder.toString().trim();
    }

    private boolean handleHomeCommand(Player player, PlayerData playerData)
    {
        //consider config, player location, player permissions
        CanTeleportResult result = this.playerCanTeleport(player, true);
        if (result.canTeleport)
        {
            RegionCoordinates homeRegion = playerData.homeRegion;
            if (result.nearPost && this.launchPlayer(player))
            {
                this.teleportPlayer(player, homeRegion, 1);
            } else
            {
                this.teleportPlayer(player, homeRegion, 0);
            }
            return true;
        }

        return true;
    }

    public void onDisable()
    {
        AddLogEntry("PopulationDensity disabled.");
    }

    //examines configuration, player permissions, and player location to determine whether or not to allow a teleport
    @SuppressWarnings("deprecation")
    private CanTeleportResult playerCanTeleport(Player player, boolean isHomeOrCityTeleport)
    {
        //if the player has the permission for teleportation, always allow it
        if (player.hasPermission("populationdensity.teleportanywhere")) return new CanTeleportResult(true);

        //disallow spamming commands to hover in the air
        if (!player.isOnGround())
        {
            if (player.getVehicle() == null)
            {
                return new CanTeleportResult(false);
            }
            // Dismount them if they're riding
            player.leaveVehicle();
        }

        //if teleportation from anywhere is enabled, always allow it
        if (this.teleportFromAnywhere) return new CanTeleportResult(true);

        //avoid teleporting from other worlds
        if (!player.getWorld().equals(ManagedWorld) && (CityWorld == null || !player.getWorld().equals(CityWorld)))
        {
            PopulationDensity.sendMessage(player, TextMode.Err, Messages.NoTeleportThisWorld);
            return new CanTeleportResult(false);
        }

        //when teleportation isn't allowed, the only exceptions are city to home, and home to city
        if (!this.allowTeleportation)
        {
            if (!isHomeOrCityTeleport)
            {
                PopulationDensity.sendMessage(player, TextMode.Err, Messages.OnlyHomeCityHere);
                return new CanTeleportResult(false);
            }

            //if city is defined and close to city post, go for it
            if (nearCityPost(player))
            {
                CanTeleportResult result = new CanTeleportResult(true);
                result.nearCityPost = true;
                return result;
            }

            //if close to home post, go for it
            PlayerData playerData = this.dataStore.getPlayerData(player);
            Location homeCenter = getRegionCenter(playerData.homeRegion, false);
            Location location = player.getLocation();
            if (location.getBlockY() >= PopulationDensity.instance.minimumRegionPostY && Math.abs(location.getBlockX() - homeCenter.getBlockX()) < 2 && Math.abs(location.getBlockZ() - homeCenter.getBlockZ()) < 2 && location.getBlock().getLightFromSky() > 0)
            {
                CanTeleportResult result = new CanTeleportResult(true);
                result.nearPost = true;
                return result;
            }

            PopulationDensity.sendMessage(player, TextMode.Err, Messages.NoTeleportHere);
            return new CanTeleportResult(false);
        }

        //otherwise, any post is acceptable to teleport from or to
        else
        {
            if (nearCityPost(player))
            {
                CanTeleportResult result = new CanTeleportResult(true);
                result.nearCityPost = true;
                return result;
            }

            RegionCoordinates currentRegion = RegionCoordinates.fromLocation(player.getLocation());
            Location currentCenter = getRegionCenter(currentRegion, false);
            Location location = player.getLocation();
            if (location.getBlockY() >= PopulationDensity.instance.minimumRegionPostY && Math.abs(location.getBlockX() - currentCenter.getBlockX()) < 3 && Math.abs(location.getBlockZ() - currentCenter.getBlockZ()) < 3 && location.getBlock().getLightFromSky() > 0)
            {
                CanTeleportResult result = new CanTeleportResult(true);
                result.nearPost = true;
                return result;
            }

            PopulationDensity.sendMessage(player, TextMode.Err, Messages.NotCloseToPost);
            PopulationDensity.sendMessage(player, TextMode.Instr, Messages.HelpMessage1, ChatColor.UNDERLINE + "" + ChatColor.AQUA + "http://bit.ly/mcregions");
            return new CanTeleportResult(false);
        }
    }

    private boolean nearCityPost(Player player)
    {
        if (CityWorld == null || !player.getWorld().equals(CityWorld)) return false;

        //max distance == 0 indicates no distance maximum
        if (this.maxDistanceFromSpawnToUseHomeRegion < 1) return true;

        return player.getLocation().distance(CityWorld.getSpawnLocation()) < this.maxDistanceFromSpawnToUseHomeRegion;
    }

    //teleports a player to a specific region of the managed world, notifying players of arrival/departure as necessary
    //players always land at the region's region post, which is placed on the surface at the center of the region
    public void teleportPlayer(Player player, RegionCoordinates region, int delaySeconds)
    {
        teleportPlayerToRegion(player, region, delaySeconds, config_launchAndDropPlayers);
    }

    public void teleportPlayerToRegion(Player player, RegionCoordinates region, int delaySeconds, Boolean doDrop)
    {
        //where specifically to send the player?
        Location teleportDestination = getRegionCenter(region, true);
        teleportDestination.setX(teleportDestination.getBlockX() + 0.5D);
        teleportDestination.setY(teleportDestination.getBlockY() + 1D);
        teleportDestination.setZ(teleportDestination.getBlockZ() + 0.5D);

        // Check the world border
        WorldBorder border = ManagedWorld.getWorldBorder();
        double size = border.getSize() / 2;
        Location center = border.getCenter();
        double x = teleportDestination.getBlockX() - center.getX(),
                z = teleportDestination.getBlockZ() - center.getZ();
        if ((x > size || (-x) > size) || (z > size || (-z) > size))
        {
            PopulationDensity.sendMessage(player, TextMode.Err, Messages.OutsideWorldBorder);
            return;
        }

        //drop the player from the sky //RoboMWM - only if LaunchAndDropPlayers is enabled
        if (doDrop && !player.getGameMode().equals(GameMode.SPECTATOR))
            teleportDestination = new Location(ManagedWorld, teleportDestination.getX(), ManagedWorld.getMaxHeight() + 10, teleportDestination.getZ(), player.getLocation().getYaw(), 90);

        List<Entity> entities = TeleportPlayerUtils.detectEntitiesToTeleport(player);
        new TeleportPlayerTask(player, teleportDestination, doDrop, instance, dropShipTeleporterInstance, entities).runTaskLater(this, delaySeconds * 20L);

        //kill bad guys in the area
        removeMonstersAround(teleportDestination);
    }

    //scans the open region for resources and may close the region (and open a new one) if accessible resources are low
    //may repeat itself if the regions it opens are also not acceptably rich in resources
    public void scanRegion(RegionCoordinates region, boolean openNewRegions) {
        AddLogEntry("Examining available resources in region \"" + region.toString() + "\"...");
        int timeoutForCompletion = 30000;

        // Get the center of the region
        Location regionCenter = getRegionCenter(region, false);

        // Calculate the boundaries of the region
        int minX = regionCenter.getBlockX() - REGION_SIZE / 2;
        int maxX = regionCenter.getBlockX() + REGION_SIZE / 2;
        int minZ = regionCenter.getBlockZ() - REGION_SIZE / 2;
        int maxZ = regionCenter.getBlockZ() + REGION_SIZE / 2;

        // Asynchronously load the chunks at the region boundaries
        // Example for region center at world (x, y, z) = (200, y, 200)
        // lesserBoundaryChunk = (x = 0, z = 0)
        // greaterBoundaryChunk = (x = 25, z = 25)
        CompletableFuture<Chunk> lesserBoundaryChunkFuture = PaperLib.getChunkAtAsync(new Location(ManagedWorld, minX, 1, minZ));
        CompletableFuture<Chunk> greaterBoundaryChunkFuture = PaperLib.getChunkAtAsync(new Location(ManagedWorld, maxX, 1, maxZ));

        // Wait for both boundary chunks to be loaded
        CompletableFuture<Void> boundaryChunksFuture = CompletableFuture.allOf(lesserBoundaryChunkFuture, greaterBoundaryChunkFuture)
            .orTimeout(timeoutForCompletion, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                AddLogEntry("Failed to load boundary chunks for region \"" + region + "\" . Error: " + ex.getMessage());
                return null;
            });

        boundaryChunksFuture.thenAcceptAsync(ignored -> {
            try {
                // Retrieve the loaded boundary chunks from the futures
                Chunk lesserBoundaryChunk = lesserBoundaryChunkFuture.get();
                Chunk greaterBoundaryChunk = greaterBoundaryChunkFuture.get();

                // Get number of chunks in a region for the 2d chunk array
                int chunkArrayWidth = greaterBoundaryChunk.getX() - lesserBoundaryChunk.getX() + 1;
                int chunkArrayHeight = greaterBoundaryChunk.getZ() - lesserBoundaryChunk.getZ() + 1;

                // Create a 2d array to hold the chunk snapshots
                ChunkSnapshot[][] snapshots = new ChunkSnapshot[chunkArrayWidth][chunkArrayHeight];
                // Create an array to hold the futures for the snapshots
                CompletableFuture<?>[] snapshotFutures = new CompletableFuture[chunkArrayWidth * chunkArrayHeight];

                // Iterate over the chunks in the region
                for (int chunkX = lesserBoundaryChunk.getX(); chunkX <= greaterBoundaryChunk.getX(); chunkX++) {
                    for (int chunkZ = lesserBoundaryChunk.getZ(); chunkZ <= greaterBoundaryChunk.getZ(); chunkZ++) {

                        // Get the chunk location
                        // worldX = chunkX * (16 for chunk size) + ( 8 for center of the chunk)
                        // worldZ = chunkZ * (16 for chunk size) + ( 8 for center of the chunk)
                        Location chunkLocation = new Location(ManagedWorld, chunkX * 16 + 8, 1, chunkZ * 16 + 8);

                        // Calculate the index of the chunk in the 2d array
                        // Example for world (x, y, z) = (200, y, 200)
                        // lesserBoundaryChunk.getX() = 0, greaterBoundaryChunk.getX() = 25
                        // lesserBoundaryChunk.getZ() = 0, greaterBoundaryChunk.getZ() = 25
                        // (arrayX, arrayZ) = (0, 0) ... (0, 25) ... (1, 0) ... (25, 25)
                        final int arrayX = chunkX - lesserBoundaryChunk.getX();
                        final int arrayZ = chunkZ - lesserBoundaryChunk.getZ();

                        // flat the 2D array of chunks using index = x * height + z
                        snapshotFutures[arrayX * chunkArrayHeight + arrayZ] = PaperLib.getChunkAtAsync(chunkLocation)
                                .thenAccept(chunk -> {
                                    synchronized (snapshots)
                                    {
                                        if (snapshots[arrayX][arrayZ] == null)
                                        {
                                            ChunkSnapshot snapshot = chunk.getChunkSnapshot();
                                            if (snapshot != null) {
                                                snapshots[arrayX][arrayZ] = snapshot;
                                            } else {
                                                AddLogEntry("Snapshot is null for chunk at: " + chunk.getX() + ", " + chunk.getZ());
                                            }
                                        } else {
                                            AddLogEntry("Skipped redundant snapshot creation for chunk at: " + chunk.getX() + ", " + chunk.getZ());
                                        }
                                    }
                                })
                                .exceptionally(ex -> {
                                    AddLogEntry("Failed to load snapshot for chunk at index: " + (arrayX * chunkArrayHeight + arrayZ) + ". Error:" + ex.getMessage());
                                    return null;
                                });
                    }
                }

                // When all snapshots are loaded, start the region scan task
                CompletableFuture.allOf(snapshotFutures)
                .orTimeout(timeoutForCompletion, TimeUnit.MILLISECONDS)
                .thenRun(() -> {
                    AbstractScanRegionTask scanTask;
                    if (advancedScanStrategy) 
                    {
                        AddLogEntry("You are using the advanced scan strategy. Please consider switching to the simplified scan strategy if your server has limited hardware resources.");
                        scanTask = new ScanRegionTaskAdvanced(snapshots, openNewRegions);
                    } else 
                    {
                        AddLogEntry("You are using the simplified scan strategy. Please consider switching to the advanced scan strategy if your server has sufficient hardware resources.");
                        scanTask = new ScanRegionTask(snapshots, openNewRegions);
                    }
                    scanTask.execute();

                }).exceptionally( ex -> {
                        AddLogEntry("Failed to load all snapshots for region \"" + region.toString() + "\". Error:" + ex.getMessage());
                        return null;
                    }
                );

            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });
    }

    //scans the open region for resources and may close the region (and open a new one) if accessible resources are low
    //may repeat itself if the regions it opens are also not acceptably rich in resources
    // deprecated and will be removed in a future version
    //TODO: use Paper API to get chunks async (with fallback to spigot/CB)
    public void scanRegionOriginal(RegionCoordinates region, boolean openNewRegions)
    {
        AddLogEntry("This method is deprecated and will be removed in a future version. Please use scanRegion which" +
                " use PopulationDensity.AdvancedScanStrategy to determine the scan strategy.");
        AddLogEntry("Original: Examining available resources in region \"" + region.toString() + "\"...");

        Location regionCenter = getRegionCenter(region, false);
        int min_x = regionCenter.getBlockX() - REGION_SIZE / 2;
        int max_x = regionCenter.getBlockX() + REGION_SIZE / 2;
        int min_z = regionCenter.getBlockZ() - REGION_SIZE / 2;
        int max_z = regionCenter.getBlockZ() + REGION_SIZE / 2;

        Chunk lesserBoundaryChunk = ManagedWorld.getChunkAt(new Location(ManagedWorld, min_x, 1, min_z));
        Chunk greaterBoundaryChunk = ManagedWorld.getChunkAt(new Location(ManagedWorld, max_x, 1, max_z));

        ChunkSnapshot[][] snapshots = new ChunkSnapshot[greaterBoundaryChunk.getX() - lesserBoundaryChunk.getX() + 1][greaterBoundaryChunk.getZ() - lesserBoundaryChunk.getZ() + 1];
        for (int x = 0; x < snapshots.length; x++)
        {
            for (int z = 0; z < snapshots[0].length; z++)
            {
                snapshots[x][z] = null;
            }
        }

        for (int x = 0; x < snapshots.length; x++)
        {
            for (int z = 0; z < snapshots[0].length; z++)
            {
                //skip chunks that we already have snapshots for
                if (snapshots[x][z] != null) continue;

                //get the chunk, load it, generate it if necessary
                Chunk chunk = ManagedWorld.getChunkAt(x + lesserBoundaryChunk.getX(), z + lesserBoundaryChunk.getZ());
                if (chunk.isLoaded() || chunk.load(true))
                {
                    //take a snapshot
                    ChunkSnapshot snapshot = chunk.getChunkSnapshot();
                    snapshots[x][z] = snapshot;
                }
            }
        }

        // create a new task with this information, which will more completely scan the content of all the snapshots
        ScanRegionTaskAdvanced task = new ScanRegionTaskAdvanced(snapshots, openNewRegions);

        task.setPriority(Thread.MIN_PRIORITY);

        // run it in a separate thread
        task.start();
    }

    //ensures a piece of the managed world is loaded into server memory
    //(generates the chunk if necessary)
    //these coordinate params are BLOCK coordinates, not CHUNK coordinates
    public static void GuaranteeChunkLoaded(int x, int z) throws ChunkLoadException
    {
        Location location = new Location(ManagedWorld, x, 5, z);
        Chunk chunk = ManagedWorld.getChunkAt(location);
        if (!chunk.isLoaded())
        {
            if (!chunk.load(true))
            {
                throw new ChunkLoadException();
            }
        }
    }

    //determines the center of a region (as a Location) given its region coordinates
    //keeping all regions the same size and aligning them in a grid keeps this calculation simple and fast
    public static Location getRegionCenter(RegionCoordinates region, boolean computeY)
    {
        int x, z;
        x = region.x * REGION_SIZE + REGION_SIZE / 2;
        z = region.z * REGION_SIZE + REGION_SIZE / 2;

        Location center = new Location(ManagedWorld, x, PopulationDensity.instance.minimumRegionPostY, z);

        if (computeY) center = ManagedWorld.getHighestBlockAt(center).getLocation();

        return center;
    }

    //capitalizes a string, used to make region names pretty
    public static String capitalize(String string)
    {
        if (string == null || string.length() == 0)

            return string;

        if (string.length() == 1)

            return string.toUpperCase();

        return StringUtils.capitalize(string);
    }

    public void resetIdleTimer(Player player)
    {
        //if idle kick is disabled, don't do anything here
        if (PopulationDensity.instance.maxIdleMinutes < 1) return;

        PlayerData playerData = this.dataStore.getPlayerData(player);

        //if there's a task already in the queue for this player, cancel it
        if (playerData.afkCheckTaskID >= 0)
        {
            PopulationDensity.instance.getServer().getScheduler().cancelTask(playerData.afkCheckTaskID);
        }

        //queue a new task for later
        //note: 20L ~ 1 second
        playerData.afkCheckTaskID = PopulationDensity.instance.getServer().getScheduler().scheduleSyncDelayedTask(PopulationDensity.instance, new AfkCheckTask(player, playerData), 20L * 60 * PopulationDensity.instance.maxIdleMinutes);
    }

    private OfflinePlayer resolvePlayer(String name)
    {
        Player player = this.getServer().getPlayer(name);
        if (player != null) return player;

        OfflinePlayer[] offlinePlayers = this.getServer().getOfflinePlayers();
        for (int i = 0; i < offlinePlayers.length; i++)
        {
            if (offlinePlayers[i].getName().equalsIgnoreCase(name))
            {
                return offlinePlayers[i];
            }
        }

        return null;
    }

    static void removeMonstersAround(Location location)
    {
        PaperLib.getChunkAtAsync(location).thenApply(centerChunk ->
        {
            World world = location.getWorld();

            for (int x = centerChunk.getX() - 2; x <= centerChunk.getX() + 2; x++)
            {
                for (int z = centerChunk.getZ() - 2; z <= centerChunk.getZ() + 2; z++)
                {
                    PaperLib.getChunkAtAsync(world, x, z).thenApply(chunk ->
                    {
                        for (Entity entity : chunk.getEntities())
                        {
                            if (entity instanceof Monster && entity.getCustomName() == null && ((Monster)entity).getRemoveWhenFarAway() && !((Monster)entity).isLeashed())
                            {
                                entity.remove();
                            }
                        }
                        return null;
                    });

                }
            }
            return null;
        });

    }

    private Material processMaterials(String string)
    {
        Material material = Material.getMaterial(string);
        if (material == null)
        {
            AddLogEntry("Error: Couldn't resolve material \"" + string + "\". Please update your config.yml.");
        }
        return material;
    }

    //sends a color-coded message to a player
    static void sendMessage(Player player, ChatColor color, Messages messageID, String... args)
    {
        sendMessage(player, color, messageID, 0, args);
    }

    //sends a color-coded message to a player
    static void sendMessage(Player player, ChatColor color, Messages messageID, long delayInTicks, String... args)
    {
        String message = PopulationDensity.instance.dataStore.getMessage(messageID, args);
        sendMessage(player, color, message, delayInTicks);
    }

    //sends a color-coded message to a player
    static void sendMessage(Player player, ChatColor color, String message)
    {
        if (message == null || message.length() == 0) return;

        if (player == null)
        {
            PopulationDensity.AddLogEntry(color + message);
        } else
        {
            player.sendMessage(color + message);
        }
    }

    static void sendMessage(Player player, ChatColor color, String message, long delayInTicks)
    {
        SendPlayerMessageTask task = new SendPlayerMessageTask(player, color, message);
        if (delayInTicks > 0)
            PopulationDensity.instance.getServer().getScheduler().runTaskLater(PopulationDensity.instance, task, delayInTicks);
        else
            task.run();
    }

    boolean launchPlayer(Player player)
    {
        if (!config_launchAndDropPlayers) return false;
        if (player.isFlying()) return false;
        if (!((Entity)player).isOnGround()) return false;
        dropShipTeleporterInstance.makeEntityFallDamageImmune(player);
        Location newViewAngle = player.getLocation();
        newViewAngle.setPitch(90);
        player.teleport(newViewAngle);
        player.setVelocity(new Vector(0, 4, 0));
        player.playSound(player.getEyeLocation(), Sound.ENTITY_GHAST_SHOOT, .75f, 1f);
        player.setGliding(false);
        return true;
    }
}
