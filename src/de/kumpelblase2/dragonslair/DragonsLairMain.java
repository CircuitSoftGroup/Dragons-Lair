package de.kumpelblase2.dragonslair;

import java.io.*;
import java.net.URL;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilderFactory;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.w3c.dom.*;
import de.kumpelblase2.dragonslair.Metrics.Graph;
import de.kumpelblase2.dragonslair.api.EventActionType;
import de.kumpelblase2.dragonslair.api.EventScheduler;
import de.kumpelblase2.dragonslair.api.ItemTracker;
import de.kumpelblase2.dragonslair.api.eventexecutors.*;
import de.kumpelblase2.dragonslair.conversation.ConversationHandler;
import de.kumpelblase2.dragonslair.events.DragonsLairInitializeEvent;
import de.kumpelblase2.dragonslair.logging.LoggingManager;
import de.kumpelblase2.dragonslair.settings.Settings;
import de.kumpelblase2.dragonslair.tasks.CooldownCleanup;

public class DragonsLairMain extends JavaPlugin
{
	public static Logger Log;
	private static DragonsLairMain instance;
	private Connection conn;
	private DungeonManager manager;
	private DLCommandExecutor commandExecutor;
	private DLEventHandler eventHandler;
	private ConversationHandler conversationHandler;
	private LoggingManager logManager;
	private final int DATABASE_REV = 10;
	private boolean citizensEnabled = false;
	private boolean economyEnabled = false;
	private EventScheduler eventScheduler;
	private Economy econ;
	
	public void onEnable()
	{
		Log = this.getLogger();
		instance = this;
		if(!this.setupDatabase())
		{
			instance = null;
			return;
		}
		this.checkDatabase();
		this.logManager = new LoggingManager();
		
		this.manager = new DungeonManager();
		this.conversationHandler = new ConversationHandler();
		this.economyEnabled = this.setupEconomy();
		this.commandExecutor = new DLCommandExecutor();
		this.eventHandler = new DLEventHandler();
		this.eventScheduler = new EventScheduler();
		getCommand("dragonslair").setExecutor(this.commandExecutor);
		Bukkit.getPluginManager().registerEvents(this.eventHandler, this);
		
		Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable()
		{
			@Override
			public void run()
			{
				DragonsLairInitializeEvent initializeEvent = new DragonsLairInitializeEvent(DragonsLairMain.getInstance());
				Bukkit.getPluginManager().callEvent(initializeEvent);
				if(initializeEvent.isCancelled())
				{
					Bukkit.getPluginManager().disablePlugin(DragonsLairMain.getInstance());
					return;
				}
				
				manager.getSettings().loadAll();
				logManager.loadEntries();
				eventScheduler.load();
				manager.spawnNPCs();
				eventHandler.reloadTriggers();
				manager.setEventExecutor(EventActionType.NPC_DIALOG, new NPCDialogEventExecutor());
				manager.setEventExecutor(EventActionType.ITEM_REMOVE, new ItemRemoveEventExecutor());
				manager.setEventExecutor(EventActionType.NPC_SPAWN, new NPCSpawnEventExecutor());
				manager.setEventExecutor(EventActionType.NPC_DESPAWN, new NPCDespawnExecutor());
				manager.setEventExecutor(EventActionType.BLOCK_CHANGE, new BlockChangeEventExecutor());
				manager.setEventExecutor(EventActionType.CHAPTER_COMPLETE, new ChapterCompleteEventExecutor());
				manager.setEventExecutor(EventActionType.DUNGEON_END, new DungeonEndEventExecutor());
				manager.setEventExecutor(EventActionType.DUNGEON_REGISTER, new DungeonRegisterEventExecutor());
				manager.setEventExecutor(EventActionType.DUNGEON_START, new DungeonStartEventExecutor());
				manager.setEventExecutor(EventActionType.ITEM_ADD, new ItemAddEventExecutor());
				manager.setEventExecutor(EventActionType.MOB_SPAWN, new MobSpawnEventExecutor());
				manager.setEventExecutor(EventActionType.OBJECTIVE_COMPLETE, new ObjectiveCompleteEventExecutor());
				manager.setEventExecutor(EventActionType.PLAYER_WARP, new PlayerTeleportEventExecutor());
				manager.setEventExecutor(EventActionType.NPC_ATTACK, new NPCAttackEventExecutor());
				manager.setEventExecutor(EventActionType.NPC_STOP_ATTACK, new NPCStopAttackEventExecutor());
				manager.setEventExecutor(EventActionType.NPC_WALK, new NPCWalkToEventExecutor());
				manager.setEventExecutor(EventActionType.ITEM_SPAWN, new ItemSpawnEventExecutor());
				manager.setEventExecutor(EventActionType.BROADCAST_MESSAGE, new BroadcastEventExecutor());
				manager.setEventExecutor(EventActionType.SAY, new SayEventExecutor());
				manager.setEventExecutor(EventActionType.ADD_POTION_EFFECT, new AddPotionEffectEventExecutor());
				manager.setEventExecutor(EventActionType.REMOVE_POTION_EFFECT, new RemovePotionEffectEventExecutor());
				manager.setEventExecutor(EventActionType.CHANGE_LEVEL, new ChangeLevelEventExecutor());
				manager.setEventExecutor(EventActionType.CHANGE_HEALTH, new ChangeHealthEventExecutor());
				manager.setEventExecutor(EventActionType.CHANGE_HUNGER, new ChangeHungerEventExecutor());
				manager.setEventExecutor(EventActionType.EXECUTE_COMMAND, new ExecuteCommandEventExecutor());
				manager.setEventExecutor(EventActionType.START_SCHEDULED_EVENT, new ScheduledEventStartEventExecutor());
				manager.setEventExecutor(EventActionType.STOP_SCHEDULED_EVENT, new ScheduledEventStopEventExecutor());
				
				
				createMetricsData();
				if(checkCitizen())
					Bukkit.getPluginManager().registerEvents(new DLCitizenHandler(), DragonsLairMain.getInstance());
				
				if(isInDebugMode())
				{
					debugLog("Loaded " + manager.getSettings().getNPCs().size() + " NPCs");
					debugLog("Loaded " + manager.getSettings().getDungeons().size() + " dungeons");
					debugLog("Loaded " + manager.getSettings().getTriggers().size() + " triggers");
					debugLog("Loaded " + manager.getSettings().getEvents().size() + " events");
					debugLog("Loaded " + manager.getSettings().getDialogs().size() + " dialogs");
					debugLog("Loaded " + manager.getSettings().getObjectives().size() + " objectivess");
					debugLog("Loaded " + manager.getSettings().getChapters().size() + " chapters");
					debugLog("Loaded " + eventScheduler.getEvents().size() + " scheduled events.");
				}
				Log.info("Done.");
				Bukkit.getScheduler().scheduleSyncRepeatingTask(DragonsLairMain.getInstance(), new CooldownCleanup(), 200L, 200L);
				startUpdateCheck();
			}
		});
		
	}

	private boolean setupEconomy()
	{
		if (getServer().getPluginManager().getPlugin("Vault") == null)
		{
            return false;
        }
		
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null)
        {
        	debugLog("No economy found.");
            return false;
        }
        econ = rsp.getProvider();
        
        if(econ != null)
        {
        	debugLog("Economy system found: " + econ.getName() +  ".");
        	return true;
        }
        else
        {
        	debugLog("No economy found.");
        	return false;
        }
	}
	
	public boolean isEconomyEnabled()
	{
		return this.economyEnabled;
	}
	
	public Economy getEconomy()
	{
		return this.econ;
	}

	private boolean checkCitizen()
	{
		Plugin p = Bukkit.getServer().getPluginManager().getPlugin("Citizens");
		if(p == null)
			return false;
		
		if(!p.isEnabled())
			return false;
		
		citizensEnabled = true;
		return true;
	}

	public void onDisable()
	{
		Bukkit.getScheduler().cancelTasks(this);
		
		if(this.manager != null)
		{
			this.manager.stopDungeons();
			this.manager.saveCooldowns();
		}
		try
		{
			if(conn != null)
				conn.close();
			debugLog("Disconnected database.");
		}
		catch (SQLException e)
		{
			Log.warning("Error closing MySQL connection.");
			e.printStackTrace();
		}
		instance = null;
	}
	
	public static DragonsLairMain getInstance()
	{
		return instance;
	}

	public static DungeonManager getDungeonManager()
	{
		return getInstance().getDungeonManagerInstance();
	}
	
	public Connection getMysqlConnection()
	{
		return this.conn;
	}
	
	private boolean connectToDB(String type, String host, int port, String db, String user, String pass)
	{
		try
		{
			if(type.equals("mysql"))
			{
				Class.forName("com.mysql.jdbc.Driver");
				conn = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + db, user, pass);
				conn.setAutoCommit(true);
				debugLog("Connected to database.");
				return true;
			}
			else if(type.equals("sqlite"))
			{
				Class.forName("org.sqlite.JDBC");
				conn = DriverManager.getConnection("jdbc:sqlite:"+ this.getDataFolder().getAbsolutePath() + "/" + host);
				debugLog("Connected to database.");
				return true;
			}
			else
			{
				Log.warning("A database of that type isn't supported");
				getServer().getPluginManager().disablePlugin(this);
				return false;
			}
			
		}
		catch (ClassNotFoundException e)
		{
			Log.warning("Couldn't start SQL Driver. Stopping...\n" + e.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
		catch (SQLException e)
		{
			Log.warning("Couldn't connect to SQL database. Stopping...\n" + e.getMessage());
			getServer().getPluginManager().disablePlugin(this);
			return false;
		}
	}
	
	private void checkDatabase()
	{
		if(this.getConfig().getInt("db.rev") == 0)
		{
			debugLog("Creating table structure because it doesn't exist.");
			for(Tables t : Tables.values())
			{
				try
				{
					PreparedStatement st;
					if(this.getConfig().getString("db.type").equals("mysql"))
						st = createStatement(t.getCreatingQuery());
					else
						st = createStatement(t.getSQLiteCreatingQuery());
					st.execute();
				}
				catch(Exception e)
				{
					Log.warning("Unable to create table '" + t.toString() + "'.");
				}
			}
			this.getConfig().set("db.rev", 1);
			this.saveConfig();
		}
		
		if(DATABASE_REV > this.getConfig().getInt("db.rev"))
		{
			debugLog("Database is outdated. Updating...");
			int currentRev = this.getConfig().getInt("db.rev");
			while(currentRev < DATABASE_REV)
			{
				currentRev++;
				this.updateDatabase(currentRev);
			}
			debugLog("Database update finished.");
			this.getConfig().set("db.rev", currentRev);
			this.saveConfig();
		}
	}
	
	private void updateDatabase(int nextRev)
	{
		try
		{
			InputStream stream = this.getConfig().getString("db.type").equals("mysql") ? DragonsLairMain.class.getResourceAsStream("/resources/rev" + nextRev + ".txt") : DragonsLairMain.class.getResourceAsStream("/resources/rev" + nextRev + "_sqlite.txt");
			if(stream == null && !this.getConfig().getString("db.type").equals("mysql"))
			{
				if(DragonsLairMain.class.getResourceAsStream("/resources/rev" + nextRev + ".txt") != null)
					return;
			}
			
			BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
			String s = "";
			while((s = reader.readLine()) != null)
			{
				createStatement(s).execute();
			}
			reader.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public static PreparedStatement createStatement(String query)
	{
		Connection conn = getInstance().getMysqlConnection();
		try
		{
			if(conn == null || !isDatabaseAlive())
				DragonsLairMain.getInstance().setupDatabase();

			return conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
		}
		catch (SQLException e)
		{
			Log.warning("Error creating query '" + query + "'.");
			e.printStackTrace();
		}
		return null;
	}
	
	public DungeonManager getDungeonManagerInstance()
	{
		return this.manager;
	}
	
	private boolean setupDatabase()
	{
		this.setupConfig();
		debugLog("Connecting to database...");
		return this.connectToDB(getConfig().getString("db.type"), getConfig().getString("db.host"), getConfig().getInt("db.port"), getConfig().getString("db.database"), getConfig().getString("db.user"), getConfig().getString("db.pass"));
	}
	
	public static Settings getSettings()
	{
		return DragonsLairMain.getDungeonManager().getSettings();
	}
	
	public ConversationHandler getConversationHandler()
	{
		return this.conversationHandler;
	}
	
	public DLEventHandler getEventHandler()
	{
		return this.eventHandler;
	}
	
	public LoggingManager getLoggingManager()
	{
		return this.logManager;
	}
	
	private void createMetricsData()
	{
		try
		{
			Metrics m = new Metrics(this);
			m.createGraph("Dungeons").addPlotter(new Metrics.Plotter()
			{
				@Override
				public int getValue()
				{
					return DragonsLairMain.getSettings().getDungeons().size();
				}
				
				@Override
				public String getColumnName()
				{
					return "Amount";
				}
			});
			
			Graph g = m.createGraph("NPCs");
			g.addPlotter(new Metrics.Plotter()
			{
				@Override
				public int getValue()
				{
					return DragonsLairMain.getSettings().getNPCs().size();
				}
				
				@Override
				public String getColumnName()
				{
					return "Amount";
				}
			});
			
			g.addPlotter(new Metrics.Plotter()
			{
				@Override
				public int getValue()
				{
					return DragonsLairMain.getDungeonManager().getNPCManager().getSpawnedNPCIDs().size();
				}
				
				@Override
				public String getColumnName()
				{
					return "Spawned";
				}
			});
			
			m.start();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	public boolean isCitizenEnabled()
	{
		return this.citizensEnabled;
	}
	
	public void setupConfig()
	{
		this.reloadConfig();
		this.getConfig().set("db.type", this.getConfig().getString("db.type", "mysql"));
		this.getConfig().set("db.host", this.getConfig().getString("db.host", "localhost"));
		this.getConfig().set("db.port", this.getConfig().getInt("db.port", 3306));
		this.getConfig().set("db.database", this.getConfig().getString("db.database", "dragonslair"));
		this.getConfig().set("db.user", this.getConfig().getString("db.user", "root"));
		this.getConfig().set("db.pass", this.getConfig().getString("db.pass", ""));
		this.getConfig().set("db.rev", this.getConfig().getInt("db.rev", 0));
		this.getConfig().set("update-notice", this.getConfig().getBoolean("update-notice", false));
		this.getConfig().set("update-notice-interval", this.getConfig().getInt("update-notice-interval", 10));
		if(this.getConfig().contains("verbose-start"))
		{
			this.getConfig().set("debug-mode", this.getConfig().getBoolean("verbose-start"));
			this.getConfig().set("verbose-start", null);
		}
		else
		{
			this.getConfig().set("debug-mode", this.getConfig().getBoolean("debug-mode", false));
		}
		if(this.getConfig().getBoolean("debug-mode"))
			Bukkit.getServer().getLogger().setLevel(Level.FINER);
		this.getConfig().set("resurrect_money", this.getConfig().getInt("resurrect", 500));
		this.getConfig().set("interacting_between_players", this.getConfig().getBoolean("interacting_between_players", false));
		if(!this.getConfig().getKeys(false).contains("enabled-worlds"))
			this.getConfig().set("enabled-worlds", new ArrayList<String>(Arrays.asList(new String[] { "world" })));
		this.saveConfig();
	}
	
	public void startUpdateCheck()
	{
		if(this.getConfig().getBoolean("update-notice"))
		{
			Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, new Runnable()
				{
					@Override
					public void run()
					{
						checkForUpdates();
					}
				}, 1L, this.getConfig().getInt("update-notice-interval") * 20 * 60);
		}
	}
	
	public void checkForUpdates()
	{
		try
		{
			URL url = new URL("http://dev.bukkit.org/server-mods/dragonslair/files.rss");
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(url.openStream());
			Node currentVersion = doc.getElementsByTagName("item").item(0);
			if(currentVersion.getNodeType() == Node.ELEMENT_NODE)
			{
				NodeList description = ((Element)currentVersion).getElementsByTagName("title");
				Node titleNode = ((Element)description.item(0)).getFirstChild();
				String title = titleNode.getNodeValue();
				title = title.replace("Dragons", "").replace("Lair", "").replace("v", "").replace(" - ", "").replace("Beta", "").replace(" ", "");
				if(this.getDescription().getVersion().equals(title))
					return;
				
				double currentVer = getVersionValue(this.getDescription().getVersion());
				double newVer = getVersionValue(title);
				if(newVer > currentVer)
				{
					Log.warning("You're running an old version of Dragons Lair. Your version: " + this.getDescription().getVersion() + ". New version: " + title);
					Log.warning("Make sure to update to prevent errors.");
				}
			}
		}
		catch (Exception e)
		{
			Log.info("There was an issue while trying to check for updates therefore it has been cancelled. " + e.getMessage());
		}
	}
	
	private double getVersionValue(String version)
	{
		String[] split = version.split("\\.");
		String newVer = "";
		for(int i = 0; i < split.length; i++)
		{
			newVer += (i == 0) ? split[i] +  "." : split[i];
		}
		
		try
		{
			return Double.parseDouble(newVer);
		}
		catch(Exception e)
		{
			return -1;
		}
	}
	
	public static boolean isWorldEnabled(String inName)
	{
		return getInstance().getConfig().getStringList("enabled-worlds").contains(inName);
	}
	
	private static boolean isDatabaseAlive()
	{
		try
		{
			if(!getInstance().getMysqlConnection().isValid(3))
				return false;
		}
		catch (SQLException e)
		{
			return false;
		}

		return true;
	}
	
	public static EventScheduler getEventScheduler()
	{
		return getInstance().eventScheduler;
	}
	
	public static ItemTracker getItemTracker()
	{
		return getDungeonManager().getItemTracker();
	}
	
	public static boolean canPlayersInteract()
	{
		return getInstance().getConfig().getBoolean("interacting_between_players");
	}
	
	public static boolean isInDebugMode()
	{
		return getInstance().getConfig().getBoolean("debug-mode");
	}
	
	public static void debugLog(String input)
	{
		if(isInDebugMode())
			Log.info("[DEBUG] " + input);
	}
}