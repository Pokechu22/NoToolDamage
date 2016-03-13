package pokechu22;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin that disables durability loss on tools in specific areas.
 */
public class NoToolDamagePlugin extends JavaPlugin implements Listener {
	/**
	 * All areas.
	 */
	private List<Area> areas;
	
	@Override
	public void onEnable() {
		this.loadAreas(getServer().getConsoleSender());
		getServer().getPluginManager().registerEvents(this, this);
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {
		if (args.length != 1) {
			return false;
		}
		if (args[0].equalsIgnoreCase("info")) {
			sender.sendMessage("No tool damage plugin, by pokechu22");
			sender.sendMessage("Allows a server admin to disable tool and armor durability loss in specific areas.");
			sender.sendMessage("Source code: https://github.com/Pokechu22/NoToolDamage");
			return true;
		} else if (args[0].equalsIgnoreCase("reload")) {
			if (!sender.hasPermission("notooldamage.reloadConfig")) {
				sender.sendMessage("§cYou don't have permission!");
				getLogger().info(sender + " was denied access to the command.");
				return true;
			}
			loadAreas(sender);
			sender.sendMessage("§aConfiguration reloaded.");
			return true;
		}
		
		return false;
	}
	
	/**
	 * Loads the list of areas.
	 * 
	 * @param warnTo CommandSender to warn to if there are errors in the config.
	 */
	private void loadAreas(CommandSender warnTo) {
		saveDefaultConfig();
		reloadConfig();
		this.areas = new ArrayList<>();
		
		if (!getConfig().isSet("areas")) {
			warnTo.sendMessage("§cInvalid config - missing 'areas'");
			return;
		} else if (!getConfig().isConfigurationSection("areas")) {
			warnTo.sendMessage("§cInvalid config - 'areas' is not a section.");
			return;
		}
		
		ConfigurationSection areasSection = getConfig().getConfigurationSection("areas");
		for (String key : areasSection.getKeys(false)) {
			if (!areasSection.isConfigurationSection(key)) {
				warnTo.sendMessage("§cArea '" + key + "' must be a configuration section.");
				continue;
			}
			ConfigurationSection areaSection = areasSection.getConfigurationSection(key);
			if (!Area.validate(areaSection, warnTo)) {
				continue;
			}
			Area area = Area.create(areaSection);
			
			areas.add(area);
		}
		
		warnTo.sendMessage("§aLoaded " + areas.size() + " areas: " + areas);
	}
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command command,
			String alias, String[] args) {
		if (args.length == 0) {
			return Arrays.asList("info", "reload");
		} else if (args.length == 1) {
			List<String> returned = new ArrayList<>();
			if ("info".startsWith(args[0].toLowerCase())) {
				returned.add("info");
			}
			if ("reload".startsWith(args[0].toLowerCase())) {
				returned.add("reload");
			}
			
			return returned;
		} else {
			return new ArrayList<>();
		}
	}
	
	@EventHandler
	public void onItemDamage(PlayerItemDamageEvent event) {
		Player player = event.getPlayer();
		for (Area area : areas) {
			if (area.isPlayerInside(player)) {
				event.setCancelled(true);
				return;
			}
		}
	}
	
	/**
	 * Represents an area in a world.
	 */
	private static class Area {
		/**
		 * Check if the given section is a valid Area.
		 * 
		 * @param section
		 *            The section to validate
		 * @param warnTo
		 *            A player (or the server console) to complain to if
		 *            something's wrong.
		 * @return True if the section is a valid Area, false if it's invalid
		 *         and no Area should be created from it.
		 */
		public static boolean validate(ConfigurationSection section,
				CommandSender warnTo) {
			boolean hasGoodCoords = true;
			hasGoodCoords &= checkCoord(section, warnTo, "x1");
			hasGoodCoords &= checkCoord(section, warnTo, "y1");
			hasGoodCoords &= checkCoord(section, warnTo, "z1");
			hasGoodCoords &= checkCoord(section, warnTo, "x2");
			hasGoodCoords &= checkCoord(section, warnTo, "y2");
			hasGoodCoords &= checkCoord(section, warnTo, "z2");
			
			if (!hasGoodCoords) {
				return false;
			}
			
			if (!section.isSet("world")) {
				warnTo.sendMessage("§cArea '" + section.getName() + "' has no world!");
				return false;
			} else if (!section.isString("world")) {
				warnTo.sendMessage("§cArea '" + section.getName() + "' has an invalid world: world must be a String!");
				return false;
			} else if (Bukkit.getWorld(section.getString("world")) == null) {
				warnTo.sendMessage("§eArea '" + section.getName() + "' has a world which coresponds with a world that doesn't exist!");
			}
			
			boolean hasMismatchedCoords = false;
			if (section.getInt("x1") > section.getInt("x2")) {
				warnTo.sendMessage("§cArea '" + section.getName() + "' has invalid x coords: x1 must be less than x2!");
				hasMismatchedCoords = true;
			}
			if (section.getInt("y1") > section.getInt("y2")) {
				warnTo.sendMessage("§cArea '" + section.getName() + "' has invalid y coords: y1 must be less than y2!");
				hasMismatchedCoords = true;
			}
			if (section.getInt("z1") > section.getInt("z2")) {
				warnTo.sendMessage("§cArea '" + section.getName() + "' has invalid z coords: z1 must be less than z2!");
				hasMismatchedCoords = true;
			}
			
			if (hasMismatchedCoords) {
				return false;
			}
			
			List<String> keys = new ArrayList<>(section.getKeys(false));
			keys.removeAll(Arrays.asList("world", "x1", "x2", "y1", "y2", "z1", "z2"));
			if (!keys.isEmpty()) {
				warnTo.sendMessage("§eArea '" + section.getName() + "' has unknown values: " + keys);
			}
			
			return true;
		}
		
		/**
		 * Checks the given coordinate, making sure it is set and is an int.
		 * 
		 * @param section
		 *            The section to validate
		 * @param warnTo
		 *            A player (or the server console) to complain to if
		 *            something's wrong.
		 * @param name
		 *            The name of the coord (eg 'x1')
		 * @return True if it is a valid coordinate, false otherwise.
		 */
		private static boolean checkCoord(ConfigurationSection section,
				CommandSender warnTo, String name) {
			if (!section.isSet(name)) {
				warnTo.sendMessage("§cArea '" + section.getName()
						+ "' is missing an x1!");
				return false;
			} else if (!section.isInt(name)) {
				warnTo.sendMessage("§cArea '" + section.getName()
						+ "' has an invalid x1: x1 must be an integer!");
				return false;
			}
			return true;
		}
		
		/**
		 * Creates a new Area from the given configuration section.
		 * 
		 * The configuration section should be validated beforehand.
		 */
		public static Area create(ConfigurationSection section) {
			String world = section.getString("world");
			int x1 = section.getInt("x1");
			int y1 = section.getInt("y1");
			int z1 = section.getInt("z1");
			int x2 = section.getInt("x2");
			int y2 = section.getInt("y2");
			int z2 = section.getInt("z2");
			
			return new Area(world, x1, y1, z1, x2, y2, z2);
		}
		
		public Area(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
			this.world = world;
			this.x1 = x1;
			this.y1 = y1;
			this.z1 = z1;
			this.x2 = x2;
			this.y2 = y2;
			this.z2 = z2;
		}
		
		/**
		 * The world this area is in.
		 */
		public final String world;
		/**
		 * Lower point
		 */
		public final int x1, y1, z1;
		/**
		 * Upper point
		 */
		public final int x2, y2, z2;
		
		/**
		 * Is the given player inside this area?
		 */
		public boolean isPlayerInside(Player player) {
			int x = player.getLocation().getBlockX();
			int y = player.getLocation().getBlockY();
			int z = player.getLocation().getBlockZ();
			
			if (!player.getLocation().getWorld().getName().equals(world)) {
				return false;
			}
			if (x < x1 || x > x2) {
				return false;
			}
			if (y < y1 || y > y2) {
				return false;
			}
			if (z < z1 || z > z2) {
				return false;
			}
			
			return true;
		}

		@Override
		public String toString() {
			return "Area [world=" + world + ", x1=" + x1 + ", y1=" + y1
					+ ", z1=" + z1 + ", x2=" + x2 + ", y2=" + y2 + ", z2=" + z2
					+ "]";
		}
	}
}
