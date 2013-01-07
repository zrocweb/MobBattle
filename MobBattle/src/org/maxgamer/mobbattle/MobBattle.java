package org.maxgamer.mobbattle;

import mc.alk.arena.BattleArena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public class MobBattle extends JavaPlugin{
	public static MobBattle instance;
	@Override
	public void onEnable(){
		instance = this;
		saveDefaultConfig();
		reloadConfig();
		
		BattleArena.registerMatchType(this, "MobBattle", "ma", MobArena.class, new MobArenaExecutor());
	}
	@Override
	public void onDisable(){
		
	}
	
	public static void set(ConfigurationSection cfg, String path, Location loc){
		cfg.set((path == null || path.isEmpty() ? "" : path + ".") + "x", loc.getX());
		cfg.set((path == null || path.isEmpty() ? "" : path + ".") + "y", loc.getY());
		cfg.set((path == null || path.isEmpty() ? "" : path + ".") + "z", loc.getZ());
		cfg.set((path == null || path.isEmpty() ? "" : path + ".") + "yaw", loc.getYaw());
		cfg.set((path == null || path.isEmpty() ? "" : path + ".") + "pitch", loc.getPitch());
		cfg.set((path == null || path.isEmpty() ? "" : path + ".") + "world", loc.getWorld().getName());
	}
	
	public static Location getLocation(ConfigurationSection cfg, String path){
		String worldPath = (path == null || path.isEmpty() ? "" : path + ".") + "world";
		
		World world = Bukkit.getWorld(cfg.getString(worldPath));
		if(world == null) return null;
		
		return new Location(world, cfg.getDouble((path == null || path.isEmpty() ? "" : path + ".") + "x"), cfg.getDouble((path == null || path.isEmpty() ? "" : path + ".") + "y"), cfg.getDouble((path == null || path.isEmpty() ? "" : path + ".") + "z"), (float) cfg.getDouble((path == null || path.isEmpty() ? "" : path + ".") + "yaw"), (float) cfg.getDouble((path == null || path.isEmpty() ? "" : path + ".") + "pitch"));
	}
}