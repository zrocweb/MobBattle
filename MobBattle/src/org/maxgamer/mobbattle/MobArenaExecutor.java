package org.maxgamer.mobbattle;

import java.io.File;
import java.io.IOException;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;
import org.maxgamer.mobbattle.MobArena.MobType;

import mc.alk.arena.executors.BAExecutor;
import mc.alk.arena.executors.MCCommand;
import mc.alk.arena.objects.arenas.Arena;

public class MobArenaExecutor extends BAExecutor{
	@MCCommand(cmds={"addspawn"}, inGame=true, admin=true)
	public boolean addSpawn(Player sender, Arena arena){		
		MobArena ma = (MobArena) arena;
		ma.addSpawnLocation(sender.getLocation());
		sender.sendMessage(ChatColor.GREEN + "Added mob spawn.");
		
		return true;
	}
	
	@MCCommand(cmds={"clearspawns"}, admin=true)
	public boolean clearSpawns(CommandSender sender, Arena arena){		
		MobArena ma = (MobArena) arena;
		ma.clearSpawns();
		sender.sendMessage(ChatColor.GREEN + "All mob spawns removed.");
		
		return true;
	}
	
	@MCCommand(cmds={"setwaves"}, admin=true)
	public boolean setWaves(CommandSender sender, Arena arena, Integer waves){		
		MobArena ma = (MobArena) arena;
		if(waves < 1){
			sender.sendMessage(ChatColor.RED + "You're an idiot. > 0");
			return true;
		}
		ma.setWaves(waves);
		sender.sendMessage(ChatColor.GREEN + "Waves set to " + waves);
		
		return true;
	}
	
	@MCCommand(cmds={"setmultiplier"}, admin=true)
	public boolean setMultiplier(CommandSender sender, Arena arena, Double diffic){		
		MobArena ma = (MobArena) arena;
		if(diffic <= 0){
			sender.sendMessage(ChatColor.RED + "You're an idiot. > 0");
			return true;
		}
		ma.setDifficultyMultiplier(diffic);
		sender.sendMessage(ChatColor.GREEN + "Difficulty multiplier set to " + diffic);
		
		return true;
	}
	
	@MCCommand(cmds={"setexprate"}, admin=true)
	public boolean setExpRate(CommandSender sender, Arena arena, Double rate){		
		MobArena ma = (MobArena) arena;
		if(rate <= 0){
			sender.sendMessage(ChatColor.RED + "You're an idiot. > 0");
			return true;
		}
		ma.setExpRate(rate);
		sender.sendMessage(ChatColor.GREEN + "Exp rate set to " + rate);
		
		return true;
	}
	@MCCommand(cmds={"mobs"}, admin=true)
	public boolean debug(CommandSender sender, Arena arena, Integer page){		
		MobArena ma = (MobArena) arena;
		page -= 1;
		
		if(page < 0){
			sender.sendMessage(ChatColor.RED + "Invalid page number.");
			return true;
		}
		
		int perPage = 1;
		MobType[] mobTypes = new MobType[perPage];
		for(int i = 0; i < perPage && (perPage * page + i) < ma.getEnemies().size(); i++){
			mobTypes[i] = ma.getEnemies().get(perPage * page + i);
		}
		
		boolean some = false;
		for(MobType mt : mobTypes){
			if(mt == null) continue;
			some = true;
			sender.sendMessage(mt.debug());
		}
		if(some == false){
			sender.sendMessage(ChatColor.RED + "No more mobs.");
		}
		
		return true;
	}
	
	@MCCommand(cmds={"copy"}, admin=true)
	public boolean copy(CommandSender sender, Arena from1, Arena to1){		
		MobArena from = (MobArena) from1;
		MobArena to = (MobArena) to1;
		
		File file = new File(MobBattle.instance.getDataFolder(), to.getName() + ".yml");
		if(file.exists()){
			sender.sendMessage(ChatColor.RED + "Overwriting previous cfg...");
		}
		
		try {
			from.getYamlConiguration().save(file);
			sender.sendMessage(ChatColor.GREEN + "Success. Copied " + from.getName() + ".yml to " + to.getName() + ".yml!");
			ConfigurationSection spawns = to.getConfig().getConfigurationSection("spawns");
			to.getYamlConiguration().load(file);
			to.getConfig().set("spawns", spawns);
			to.loadConfig();
			to.loadMobs();
			to.save();
		} catch (IOException e) {
			sender.sendMessage(ChatColor.RED + "Copy failed.");
		} catch (InvalidConfigurationException e) {
			e.printStackTrace();
			sender.sendMessage(ChatColor.RED + "Config file is invalid.");
		}
		
		return true;
	}
}