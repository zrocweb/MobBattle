package org.maxgamer.mobbattle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import mc.alk.arena.events.matches.MatchMessageEvent;
import mc.alk.arena.objects.MatchResult;
import mc.alk.arena.objects.MatchState;
import mc.alk.arena.objects.arenas.Arena;
import mc.alk.arena.objects.events.MatchEventHandler;
import mc.alk.arena.util.EffectUtil;
import mc.alk.arena.util.InventoryUtil;

public class MobArena extends Arena{
	/** The mobs we should spawn in this arena */
	private LinkedList<MobType> enemies = new LinkedList<MobType>();
	/** The list of locations for mobs to spawn */
	private ArrayList<Location> spawnLocs = new ArrayList<Location>(5);
	/** The list of currently alive mobs */
	private HashMap<Entity, MobType> alive = new HashMap<Entity, MobType>();
	
	/** The task which checks mobs are still valid, and requests a new wave when ready */
	private BukkitTask watcher;
	private Random r = new Random();
	
	/** The task which spawns mobs in a few seconds after being activated */
	private BukkitTask spawner;
	
	/** The current round difficulty of this match */
	private double difficulty;
	
	/** The current wave number for this match */
	private int wave;
	/** The maximum number of waves for this match */
	private int numWaves = 5;
	
	/** This match prefix, with color codes pre-translated */
	private String prefix = "";
	
	/** The rate mob experience drops should be multiplied by */
	private double expRate = 1;
	
	/** Higher means rounds are harder. Lower means rounds are easier. */
	private double difficultyMultiplier = 1;
	
	/** The configuration file for this arena */
	private YamlConfiguration cfg;
	
	@Override
	public void init(){
		super.init();
		loadMobs();
		loadConfig();
	}
	
	@Override
	public void onStart(){
		super.onStart();
		
		if(this.spawnLocs.isEmpty()){
			this.getMatch().sendMessage(ChatColor.RED + "Arena " + this.getName() + " has no defined mob spawns! It cannot start!");
			this.getMatch().cancelMatch();
		}
		
		prefix = ChatColor.translateAlternateColorCodes('&', this.getMatch().getParams().getPrefix()) + " ";
		
		difficulty = (this.getMatch().getPlayers().size() * 2 + 2) * difficultyMultiplier + 1;
		wave = 0;
		
		clearMobs(); //Remove any leftovers that we somehow have (?)
		
		//This could also be done whenever a mob dies.
		watcher = Bukkit.getScheduler().runTaskTimer(MobBattle.instance, new Runnable(){
			@Override
			public void run() {
				//Validate all alive mobs to make sure they haven't died weirdly.
				Iterator<Entry<Entity, MobType>> it = alive.entrySet().iterator();
				while(it.hasNext()){
					Entry<Entity, MobType> next = it.next();
					if(!next.getKey().isValid()){
						next.getValue().onDeath(next.getKey());
						it.remove();
						System.out.println("Cleaned mob: " + next.getKey().getType());
					}
				}
				
				//It is the end of a wave
				if(alive.isEmpty() && spawner == null){
					if(wave >= numWaves){
						//All waves defeated
						getMatch().setVictor(getMatch().getTeams());
						return;
					}
					
					//Increase difficulty of the next wave
					difficulty += getMatch().getPlayers().size() * difficultyMultiplier;
					//Spawn the next wave in 7 seconds
					spawner = Bukkit.getScheduler().runTaskLater(MobBattle.instance, new Runnable(){
						@Override
						public void run(){
							wave++;
							spawnWave();
							getMatch().sendMessage(prefix + ChatColor.GREEN + "Wave: " + wave);
							
							spawner.cancel();
							spawner = null;
						}
					}, 140);
				}
			}
		}, 100, 100);
	}
	
	@Override
	public void create(){
		this.getParameters().setMinTeams(1);
		this.save();
	}
	
	@Override
	public void delete(){
		super.delete();
		
		File cfgFile = new File(MobBattle.instance.getDataFolder(), this.getName() + ".yml");
		if(cfgFile.exists()){
			System.out.println("Deleting " + cfgFile.getName());
			cfgFile.delete();
		}
		else{
			System.out.println("No cfg file found: " + cfgFile.getName());
		}
	}
	
	/**
	 * Does the same as onVictory, except onVictory isn't always called.
	 */
	@Override
	public void onFinish(){
		clearMobs();
		endGame();
	}
	
	/** Fetches a random mob spawn location */
	public Location getRandomMobSpawn(){
		return this.spawnLocs.get(r.nextInt(this.spawnLocs.size()));
	}
	
	/**
	 * Clears mobs as soon as the game ends, so that the chunks are still loaded and the mobs can be removed.
	 */
	@Override
	public void onVictory(MatchResult result){
		endGame();
		clearMobs();
	}
	
	@MatchEventHandler(needsPlayer = false)
	public void onMobDeath(EntityDeathEvent e){
		MobType mt = this.alive.remove(e.getEntity());
		if(mt == null) return; //That wasnt a mob from our arena.
		mt.onDeath(e.getEntity());
		e.setDroppedExp((int) (e.getDroppedExp() * this.expRate));
		e.getDrops().clear();
	}
	
	@MatchEventHandler(needsPlayer = false)
	public void onDamageByExplosion(EntityDamageByEntityEvent e){
		if(e.getEntity() instanceof Player) return; //Players dont get this benefit
		
		//Creeper explosion
		if(e.getCause() == DamageCause.ENTITY_EXPLOSION){
			if(!this.alive.containsKey(e.getDamager())) return; //Not our creeper.
			e.setDamage(0); //No mob damage from creeperes
		}
	}
	@MatchEventHandler(needsPlayer = false)
	public void onCreeperExplode(EntityExplodeEvent e){
		MobType mt = this.alive.remove(e.getEntity());
		if(mt == null) return; //That wasnt a mob from our arena.
		mt.onDeath(e.getEntity()); //It was one of ours
	}
	
	/** Removes all mobs currently tracked by the arena */
	public void clearMobs(){
		for(Entity e : this.alive.keySet()){
			e.remove();
		}
		this.alive.clear();
	}
	
	/**
	 * Stops the watcher task and spawner task.
	 */
	public void endGame(){
		if(watcher != null){
			watcher.cancel();
			watcher = null;
		}
		if(spawner != null){
			spawner.cancel();
			spawner = null;
		}
	}
	
	@MatchEventHandler
	public void onMessage(MatchMessageEvent e){
		if(e.getState() == MatchState.ONMATCHINTERVAL){
			e.setMatchMessage(e.getMatchMessage() + ". Wave: " + wave + "/" + numWaves + ".");
		}
	}
	
	/**
	 * Spawns a new wave, using the global variable difficulty to determine how many of what to spawn.
	 */
	public void spawnWave(){
		int progress = 0;
		int numSpawns = spawnLocs.size();
		int numMobs = this.enemies.size();
		
		int tries = 0;
		int target = (int) difficulty;
		while(progress < target){
			if(tries >= 500){
				System.out.println("Warning! Tried "+tries+" times to get mobs, but failed!");
				System.out.println("Maybe there is no combination of mobs I can spawn?");
				System.out.println("Required: " + (target - progress) + ", On wave: " + wave + ".");
				System.out.println("Giving up -- Breaking");
				break;
			}
			tries++;
			MobType mt = this.enemies.get(r.nextInt(numMobs));
			
			if(mt.getLevel() + progress > target) continue;
			if(mt.getMinWave() > wave) continue;
			if(mt.getMaxWave() < wave) continue;
			Location spawn = spawnLocs.get(r.nextInt(numSpawns));
			alive.put(mt.spawn(spawn), mt);
			
			progress += mt.getLevel();
			tries = 0;
			
		}
	}
	
	/**
	 * Fetches the configuration section of config.yml for this specific arena.
	 * @return The configuration section for this arena.
	 * 
	 * If this is a new arena, calling this method will create the config file using
	 * the default values, and then return it.
	 */
	public ConfigurationSection getConfig(){
		if(cfg != null) return cfg;
		
		cfg = new YamlConfiguration();
		File cfgFile = new File(MobBattle.instance.getDataFolder(), this.getName() + ".yml");
		
		if(!cfgFile.exists()){
			copyDefaultConfig(cfgFile);
		}
		try{
			cfg.load(cfgFile);
		}
		catch(InvalidConfigurationException e){
			System.out.println("Invalid config file:");
			e.printStackTrace();
		}
		catch(IOException e){
			System.out.println("Could not load config file:");
			e.printStackTrace();
		}
		
		return cfg;
	}
	
	public YamlConfiguration getYamlConiguration(){
		return cfg;
	}
	
	/**
	 * Copies the default_arena.yml config into the given file.
	 * @param cfgFile The file to store the default config in.
	 */
	private void copyDefaultConfig(File cfgFile){
		try{
			cfgFile.createNewFile();
			InputStream in = MobBattle.instance.getResource("default_arena.yml");
			OutputStream out = new FileOutputStream(cfgFile);
			
			byte[] buffer = new byte[1024];
			int len;
			while ((len = in.read(buffer)) != -1) {
			    out.write(buffer, 0, len);
			}
			in.close();
			out.close();
		}
		catch(IOException e){
			System.out.println("Could not create config file!");
		}
	}
	
	/** Places an additional monster spawn position for this arena */
	public void addSpawnLocation(Location loc){
		this.spawnLocs.add(loc);
		save();
	}
	/** Changes the number of waves this arena will spawn */
	public void setWaves(int num){
		this.numWaves = num;
		save();
	}
	/** Changes the exp rate for this arena */
	public void setExpRate(double rate){
		this.expRate = rate;
		save();
	}
	/** Changes the difficulty multipler for this arena */
	public void setDifficultyMultiplier(double num){
		this.difficultyMultiplier = num;
		save();
	}
	/** Writes this arena to config and saves it. */
	public void save(){
		for(int i = 0; i < this.spawnLocs.size(); i++){
			MobBattle.set(this.getConfig(), "spawns."+i, spawnLocs.get(i));
		}
		this.getConfig().set("waves", numWaves);
		this.getConfig().set("difficulty-multiplier", this.difficultyMultiplier);
		this.getConfig().set("exprate", this.expRate);
		try{
			cfg.save(new File(MobBattle.instance.getDataFolder(), this.getName() + ".yml"));
		}
		catch(IOException e){
			System.out.println("Could not save config file!");
		}
	}
	/** Deletes all mob spawn positions from config and memory */
	public void clearSpawns(){
		this.spawnLocs.clear();
		save();
	}
	
	/** Loads all mobs */
	public void loadMobs(){
		System.out.println("Loading mobs.");
		enemies.clear();
		
		ConfigurationSection mobs = getConfig().getConfigurationSection("mobs");
		if(mobs == null) mobs = getConfig().createSection("mobs");
		
		for(String name : mobs.getKeys(false)){
			int difficulty = mobs.getInt(name + ".difficulty");
			int minWave = mobs.getInt(name + ".minWave");
			int maxWave = mobs.getInt(name + ".maxWave", Integer.MAX_VALUE);
			boolean charge = mobs.getBoolean(name + ".charge");
			
			MobType mobType;
			try{
				Class<? extends LivingEntity> type = Class.forName("org.bukkit.entity." + mobs.getString(name + ".type")).asSubclass(LivingEntity.class);
				mobType = new MobType(type, difficulty, minWave, maxWave);
			}
			catch(ClassNotFoundException e){
				System.out.println("Invalid mob type: " + mobs.getName() + "." + name + ".type" + " -> " + mobs.getString(name + ".type") + "!");
				continue;
			}
			
			mobType.setCharged(charge);
			
			ItemStack helm, chestplate, leggings, boots, weapon;
			helm = chestplate = leggings = boots = weapon = null;
			
			//Helmet
			try{
				helm = InventoryUtil.parseItem(mobs.getString(name + ".helm", "AIR"));
				mobType.setHelm(helm);
			}
			catch(Exception e){
				System.out.println("Invalid Cfg: " + mobs.getCurrentPath() + "." + name + ".helm -> " + mobs.getString(name + ".helm", "AIR"));
			}
			
			//Chestplate
			try{
				chestplate = InventoryUtil.parseItem(mobs.getString(name + ".chestplate", "AIR"));
				mobType.setChestplate(chestplate);
			}
			catch(Exception e){
				System.out.println("Invalid Cfg: " + mobs.getCurrentPath() + "." + name + ".chestplate -> " + mobs.getString(name + ".chestplate", "AIR"));
			}
			
			//Leggings
			try{
				leggings = InventoryUtil.parseItem(mobs.getString(name + ".leggings", "AIR"));
				mobType.setLeggings(leggings);
			}
			catch(Exception e){
				System.out.println("Invalid Cfg: " + mobs.getCurrentPath() + "." + name + ".leggings -> " + mobs.getString(name + ".leggings", "AIR"));
			}
			
			//Boots
			try{
				boots = InventoryUtil.parseItem(mobs.getString(name + ".boots", "AIR"));
				mobType.setBoots(boots);
			}
			catch(Exception e){
				System.out.println("Invalid Cfg: " + mobs.getCurrentPath() + "." + name + ".boots -> " + mobs.getString(name + ".boots", "AIR"));
			}
			
			//Weapon
			try{
				weapon = InventoryUtil.parseItem(mobs.getString(name + ".weapon", "AIR"));
				mobType.setWeapon(weapon);
			}
			catch(Exception e){
				System.out.println("Invalid Cfg: " + mobs.getCurrentPath() + "." + name + ".weapon -> " + mobs.getString(name + ".weapon", "AIR"));
			}
			
			//Drop type
			DropType dropType = DropType.valueOf(mobs.getString(name + ".dropType", "ALL"));
			if(dropType != null) mobType.setDropType(dropType);
			//Else, defaults to drop all.
			
			//Load drops
			for(Object value : mobs.getList(name + ".drops")){
				String info = value.toString();
				try{
					ItemStack i = InventoryUtil.parseItem(info);
					mobType.addDrop(i);
				}
				catch(Exception e){
					System.out.println("Invalid item: " + mobs.getCurrentPath() + "." + name + ".drops -> " + info);
				}
			}
			
			//Load effects
			List<String> pots = mobs.getStringList(name + ".effects");
			for(String info : pots){
				PotionEffect effect = EffectUtil.parseArg(info, 1, 900);
				mobType.addEffect(effect);
			}
			mobType.setName(name);
			
			this.enemies.add(mobType);
		}
		
		boolean safe = false; 
		for(MobType mt : this.enemies){
			if(mt.getLevel() == 1){
				safe = true;
				break;
			}
		}
		if(safe == false){
			System.out.println("Warning! No mobs have a difficulty of 1.");
			System.out.println("This means the server could freeze.");
			System.out.println("I am creating a dummy zombie with difficulty of 1 to save you.");
			MobType mt = new MobType(Zombie.class, 1);
			this.enemies.add(mt);
		}
	}
	
	/** loads the config from scratch.  Must reload config first using MobBattle.instance.reloadConfig() */
	public void loadConfig(){
		System.out.println("Loading config...");

		//Clear old spawns
		this.spawnLocs.clear();
		
		//Fetch config section for spawns
		ConfigurationSection spawns = getConfig().getConfigurationSection("spawns");
		if(spawns == null) spawns = getConfig().createSection("spawns");
		
		//Load new spawns from config
		for(String key : spawns.getKeys(false)){
			Location loc = MobBattle.getLocation(spawns, key);
			if(loc != null && loc.getWorld() != null){
				this.spawnLocs.add(loc);
			}
			else{
				System.out.println("Couldnt load spawn");
			}
		}
		
		//Load waves, ensure it is > 0.
		if(this.getConfig().getInt("waves") > 0){
			this.numWaves = this.getConfig().getInt("waves");
		}
		else{
			this.numWaves = 5;
		}
		
		//Load exp rate
		this.expRate = this.getConfig().getDouble("exprate", this.expRate);
		//Load difficulty multiplier
		this.difficultyMultiplier = this.getConfig().getDouble("difficulty-multiplier", 1);
		//Ensure difficulty is reasonable
		if(difficultyMultiplier <= 0){
			System.out.println("Difficulty multiplier must be positive! Resetting to 1.");
			difficultyMultiplier = 1;
		}
	}
	
	public LinkedList<MobType> getEnemies(){
		return this.enemies;
	}
	
	public class MobType{
		/** The class that this MobType will spawn. E.g. org.bukkit.entity.Zombie or org.bukkit.entity.Cow */
		private Class<? extends LivingEntity> mob;
		/** The list of item stacks these mobs will drop */
		private ArrayList<ItemStack> drops = new ArrayList<ItemStack>(2);
		/** The list of potion effects these mobs spawn with */
		private LinkedList<PotionEffect> effects = new LinkedList<PotionEffect>();
		/** The difficulty of this enemy */
		private int lvl = 1;
		/** The minimum wave for this enemy to spawn */
		private int minWave = 0;
		/** The maximum wave for this enemy to spawn */
		private int maxWave = Integer.MAX_VALUE;
		
		/** The helm this enemy should wear */
		private ItemStack helm;
		/** The chestplate this enemy should wear */
		private ItemStack chestplate;
		/** The leggings this enemy should wear */
		private ItemStack leggings;
		/** The boots this enemy should wear */
		private ItemStack boots;
		/** The weapon this enemy should wield */
		private ItemStack weapon;
		
		/** Should we super charge this? Only works for creepers */
		private boolean charge = false;
		
		/** Name for output */
		private String name = "Unknown";
		
		/** Drop Type */
		private DropType dropType = DropType.ALL;
		
		/**
		 * Outputs various info about this entity, it's variables, items and effects.
		 * Debug purposese.
		 */
		public String debug(){
			StringBuilder sb = new StringBuilder();
			sb.append(ChatColor.DARK_PURPLE + "---------");
			sb.append(ChatColor.RED + "\nname: " + ChatColor.GREEN + name);
			sb.append(ChatColor.RED + "\nType: " + ChatColor.GREEN + mob.getSimpleName());
			sb.append(ChatColor.RED + "\nminWave: " + ChatColor.GREEN + minWave);
			sb.append(ChatColor.RED + "\nmaxWave: " + ChatColor.GREEN + maxWave);
			sb.append(ChatColor.RED + "\nlvl: " + ChatColor.GREEN + lvl);
			sb.append(ChatColor.RED + "\ncharge: " + ChatColor.GREEN + charge);
			sb.append(ChatColor.RED + "\nhelm: " + ChatColor.GREEN + helm);
			sb.append(ChatColor.RED + "\nchestplate: " + ChatColor.GREEN + chestplate);
			sb.append(ChatColor.RED + "\nleggings: " + ChatColor.GREEN + leggings);
			sb.append(ChatColor.RED + "\nboots: " + ChatColor.GREEN + boots);
			sb.append(ChatColor.RED + "\nweapon: " + ChatColor.GREEN + weapon);
			for(PotionEffect effect : effects){
				sb.append(ChatColor.RED + "\neffect: " + ChatColor.GREEN + effect.getType().toString() + " tier: " + effect.getAmplifier() + " ticks: " + effect.getDuration());
			}
			for(ItemStack drop : drops){
				sb.append(ChatColor.RED + "\ndrop: " + ChatColor.GREEN + drop);
			}
			return sb.toString();
		}
		/**
		 * Represents a new enemy type
		 * @param mob The class that should be spawned, e.g. org.bukkit.entity.Zombie or org.bukkit.entity.Cow
		 * @param lvl The 'difficulty' of this mob to kill
		 * The same as MobType(mob, lvl, 0);
		 */
		public MobType(Class<? extends LivingEntity> mob, int lvl){
			this.mob = mob;
			this.lvl = lvl;
		}
		/**
		 * Represents a new enemy type
		 * @param mob The class that should be spawned, e.g. org.bukkit.entity.Zombie or org.bukkit.entity.Cow
		 * @param lvl The 'difficulty' of this mob to kill
		 * @param minWave The minimum wave for this mob to spawn
		 * The same as MobType(mob, lvl, minWave, Integer.MAX_VALUE);
		 */
		public MobType(Class<? extends LivingEntity> mob, int lvl, int minWave){
			this(mob, lvl);
			this.minWave = minWave;
		}
		/**
		 * Represents a new enemy type
		 * @param mob The class that should be spawned, e.g. org.bukkit.entity.Zombie or org.bukkit.entity.Cow
		 * @param lvl The 'difficulty' of this mob to kill
		 * @param minWave The minimum wave for this mob to spawn
		 * @param maxWave The maximum wave for this mob to spawn
		 */
		public MobType(Class<? extends LivingEntity> mob, int lvl, int minWave, int maxWave){
			this(mob, lvl, minWave);
			this.maxWave = maxWave;
		}
		/** The minimum wave for this mob to spawn */
		public int getMinWave(){
			return this.minWave;
		}
		/** Sets the drop type for choosing what items this mob should drop */
		public void setDropType(DropType dt){
			if(dt == null) dt = DropType.ALL;
			this.dropType = dt;
		}
		/** Names this type of mob. Used for printing out messages to console and players */
		public void setName(String name){
			this.name = name;
		}
		/** The maximum wave this mob can spawn at */
		public int getMaxWave(){
			return this.maxWave;
		}
		/** The difficulty rating of this mob */
		public int getLevel(){
			return this.lvl;
		}
		public void setHelm(ItemStack i){
			helm = i;
		}
		public void setChestplate(ItemStack i){
			chestplate = i;
		}
		public void setLeggings(ItemStack i){
			leggings = i;
		}
		public void setBoots(ItemStack i){
			boots = i;
		}
		public void setWeapon(ItemStack i){
			weapon = i;
		}
		/** Adds the given potion effect to this mob when it spawns */
		public void addEffect(PotionEffect effect){
			this.effects.add(effect);
		}
		public Class<? extends LivingEntity> getMob(){
			return mob;
		}
		/**
		 * Sets this creature to be charged.
		 * Only works for creepers.
		 * @param charged Whether or not the creeper should be supercharged (E.g. Struck by lightning)
		 */
		public void setCharged(boolean charged){
			charge = charged;
		}
		/**
		 * Spawns an entity using this template at the given location
		 * @param loc The location to spawn
		 * @return The LivingEntity spawned
		 */
		public LivingEntity spawn(Location loc){
			LivingEntity e = loc.getWorld().spawn(loc, this.getMob());
			
			EntityEquipment gear = e.getEquipment();
			
			gear.setBoots(boots);
			gear.setHelmet(helm);
			gear.setChestplate(chestplate);
			gear.setLeggings(leggings);
			gear.setItemInHand(weapon);
			
			gear.setHelmetDropChance(0);
			gear.setBootsDropChance(0);
			gear.setChestplateDropChance(0);
			gear.setLeggingsDropChance(0);
			gear.setItemInHandDropChance(0);
			
			for(PotionEffect effect : this.effects){
				effect.apply(e);
			}
			
			if(e instanceof Creeper){
				((Creeper) e).setPowered(this.charge);
			}
			
			return e;
		}
		/** Called when this mob dies */
		public void onDeath(Entity e){
			for(ItemStack drop : dropType.getDrops(drops)){
				e.getLocation().getWorld().dropItem(e.getLocation(), drop);
			}
		}
		/** Adds an item to drop when this mob dies */
		public void addDrop(ItemStack i){
			this.drops.add(i);
		}
	}
	
	public enum DropType{
		GUARANTEE_ONE(),
		QUARTER(),
		HALF(),
		THREE_QUARTER(),
		ALL();
		
		private static Random r = new Random();
		
		public LinkedList<ItemStack> getDrops(List<ItemStack> options){
			LinkedList<ItemStack> result = new LinkedList<ItemStack>();
			if(options.isEmpty()){
				return result; //No loot to choose from.
			}
			
			switch(this){
			case GUARANTEE_ONE:
				result.add(options.get(r.nextInt(options.size())));
				break;
			case QUARTER:
				for(ItemStack iStack : options){
					if(r.nextBoolean() && r.nextBoolean()){ //1/2 * 1/2 = 1/4 = Quarter
						result.add(iStack);
					}
				}
				break;
			case HALF:
				for(ItemStack iStack : options){
					if(r.nextBoolean()){ //1/2 = Half
						result.add(iStack);
					}
				}
				break;
			case THREE_QUARTER:
				for(ItemStack iStack : options){
					if(!(r.nextBoolean()) && r.nextBoolean()){ //1/2 * 1/2 = 1/4.  1 - 1/4 = 75% = Three quarters
						result.add(iStack);
					}
				}
				break;
			case ALL:
				result.addAll(options);
				break;
			}
			return result;
		}
	}
}