package org.maxgamer.mobbattle;

import java.util.ArrayList;
import java.util.List;

import mc.alk.arena.BattleArena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class MobBattle extends JavaPlugin implements Listener{
	public static MobBattle instance;
	
	@Override
	public void onEnable(){
		instance = this;
		saveDefaultConfig();
		reloadConfig();
		getServer().getPluginManager().registerEvents(this, this);
		BattleArena.registerCompetition(this, "MobBattle", "ma", MobArena.class, new MobArenaExecutor());
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
	
	@EventHandler
	public void onCraft(CraftItemEvent e){
		CraftingInventory bench = e.getInventory();
		
		for(ItemStack i : bench.getContents()){
			if(i == null || i.getType() == Material.AIR) continue;;
			ItemMeta meta = i.getItemMeta();
			List<String> lore = meta.getLore();
			if(lore == null) continue;
			if(lore.contains("MobBattle Item")){
				meta = e.getCurrentItem().getItemMeta();
				lore = meta.getLore();
				if(lore == null) lore = new ArrayList<String>();
				lore.add("MobBattle Item");
				meta.setLore(lore);
				ItemStack stack = e.getCurrentItem();
				stack.setItemMeta(meta);
				e.setCurrentItem(stack);
				return;
			}
		}
	}
}