package Botcode;

import Botcode.Commands.CommandManager;
import Botcode.Panel.PanelModule;
import Botcode.StarCitizen.StarCitizenUpdateManager;
import Botcode.Utils.DatasetCache;
import Botcode.listeners.Eventlistener;
import Botcode.listeners.OnJoin;
import Botcode.listeners.TempVoiceDelete;
import Botcode.Security.DatabaseEncryptionManager;
import Botcode.Security.BackupService;
import Botcode.Monitoring.MetricsCollector;
import Botcode.Database.DatabaseOptimizer;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.StatusChangeEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static net.dv8tion.jda.api.requests.GatewayIntent.*;

/**
 * Bootstraps JDA, registers slash commands, and wires all event listeners.
 * <p>
 * This is the main composition root for the bot runtime.
 */
public class CommsBot {

  /**
   * App process start timestamp (ms since epoch), used for uptime reporting.
   */
  private static final long START_EPOCH_MS = System.currentTimeMillis();
  private static final String DEFAULT_DB_PATH = "data/starcitizen.db";

  private final Dotenv config;
  private final ShardManager shardManager;

  /**
   * Creates the shard manager and registers commands/listeners for each shard.
   */
  public CommsBot() throws LoginException {
    System.out.println("[Startup] Loading environment...");
    config = Dotenv.configure()
        .ignoreIfMalformed()
        .ignoreIfMissing()
        .load();

    String token = config.get("TOKEN");
    if (token == null || token.isBlank()) {
      throw new IllegalStateException(
          "Missing TOKEN. Create a .env file in project root with TOKEN=your_discord_bot_token");
    }

    System.out.println("[Startup] TOKEN loaded successfully.");

    // Initialize security & monitoring modules BEFORE building JDA.
    System.out.println("[Security] Initializing security modules...");
    String dbPath = config.get("SC_DB_PATH");
    if (dbPath == null || dbPath.isBlank()) {
      dbPath = DEFAULT_DB_PATH;
    }
    try {
      if (Botcode.AI.AIUtils.BotConfig.DB_ENCRYPTION_ENABLED
          && DatabaseEncryptionManager.isEncryptionEnabled()) {
        DatabaseEncryptionManager.getOrGenerateEncryptionKey();
        System.out.println("[Security] Database encryption key initialized");
      }

      try (Connection dbConnection = openDatabaseConnection(dbPath)) {
        DatabaseOptimizer.configureOptimalPragmas(dbConnection);
        DatabaseOptimizer.createOptimalIndexes(dbConnection);
        System.out.println("[Security] Database indexes optimized");
      }

      if (Botcode.AI.AIUtils.BotConfig.BACKUP_ENABLED) {
        BackupService backupService = new BackupService();
        backupService.start(dbPath);
        System.out.println("[Security] Backup service started");
      }

      MetricsCollector.initialize();
      System.out.println("[Security] Metrics collection started");

    } catch (Exception e) {
      System.out.println("[Security] Warning: Security module initialization failed: " + e.getMessage());
      e.printStackTrace();
    }

    // Preserve all gateway intents so downstream listeners receive required events.
    DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createDefault(token)
        .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS));

    builder.addEventListeners(new ListenerAdapter() {
      @Override
      public void onReady(ReadyEvent event) {
        System.out.println("[JDA] READY as " + event.getJDA().getSelfUser().getName()
            + " | Guilds: " + event.getJDA().getGuilds().size());
        MetricsCollector.recordEvent("bot_ready", 1);
      }

      @Override
      public void onStatusChange(StatusChangeEvent event) {
        System.out.println("[JDA] Status: " + event.getOldStatus() + " -> " + event.getNewStatus());
      }

      @Override
      public void onSessionDisconnect(SessionDisconnectEvent event) {
        System.out.println("[JDA] Session disconnect. CloseCode=" + event.getCloseCode()
            + " | ClosedByServer=" + event.isClosedByServer());
      }
    });

    // Runtime presence/cache settings required by member and voice workflows.
    builder.setStatus(OnlineStatus.ONLINE);
    builder.setActivity(Activity.playing("Squadron 42"));
    builder.enableIntents(GUILD_MEMBERS, GUILD_MESSAGES, SCHEDULED_EVENTS, GUILD_PRESENCES,
        GUILD_VOICE_STATES, MESSAGE_CONTENT);
    builder.setMemberCachePolicy(MemberCachePolicy.ALL);
    builder.setChunkingFilter(ChunkingFilter.ALL);
    builder.enableCache(CacheFlag.SCHEDULED_EVENTS);

    System.out.println("[Startup] Building shard manager...");
    shardManager = builder.build();
    System.out.println(
        "[Startup] Shard manager created. Current shard objects: " + shardManager.getShards()
            .size());

    // Initialize SQLite cache and load initial data from JSON files.
    System.out.println("[Startup] Initializing dataset cache...");
    DatasetCache.initializeAtStartup();

    // Pull fresh web snapshots on startup for supported datasets (UEX/Erkul).
    refreshStarCitizenSnapshots();

    // Register slash commands for each shard instance.
    shardManager.getShards().forEach(jda -> {

      // Help command
      jda.upsertCommand("help", "Show all available commands").queue();

      // Panel command — posts the interactive operations console
      jda.upsertCommand("panel", "Post the interactive operations console").queue();

      // Commodity command
      jda.upsertCommand("commodity", "Get commodity trading info")
          .addOption(OptionType.STRING, "name", "Commodity name", true)
          .queue();

      // Mining command
      jda.upsertCommand("mine", "Analyze a mineable rock")
          .addOption(OptionType.STRING, "rock", "Rock name", true, true)
          .addOption(OptionType.STRING, "ship", "Ship name", true, true)
          .addOption(OptionType.STRING, "laser", "Mining laser", true, true)
          .addOption(OptionType.STRING, "consumable", "Mining consumable", true, true)
          .addOption(OptionType.INTEGER, "operators", "Number of lasers", true)
          .addOption(OptionType.STRING, "format", "brief, full, or specific", true, true)
          .addOption(OptionType.STRING, "field", "Field for specific mode", false, true)
          .queue();

      // Ship lookup command
      jda.upsertCommand("ship", "Get ship specifications")
          .addOption(OptionType.STRING, "name", "Ship name", true)
          .queue();

      // Weapon lookup command
      jda.upsertCommand("weapon", "Get weapon DPS and stats")
          .addOption(OptionType.STRING, "name", "Weapon name", true)
          .queue();

      // Trade route command
      jda.upsertCommand("trade", "Find profitable trade routes")
          .addOption(OptionType.STRING, "mode", "top or commodity", true)
          .addOption(OptionType.INTEGER, "cargo", "Cargo capacity in SCU", true)
          .addOption(OptionType.STRING, "commodity", "Commodity name (for mode=commodity)", false)
          .addOption(OptionType.INTEGER, "count", "Number of routes (for mode=top)", false)
          .queue();

      // Missing data report command
      jda.upsertCommand("reportmissing",
              "Report missing Star Citizen data and trigger a live check")
          .addOption(OptionType.STRING, "dataset",
              "Dataset to check (e.g., commodity, trade, mining, ship, weapon,"
                  + " component, location)", true)
          .addOption(OptionType.STRING, "item", "Name that appears to be missing", true)
          .addOption(OptionType.STRING, "notes", "Optional extra context", false)
          .queue();

      // GDPR & Security Commands
      jda.upsertCommand("gdpr-export-my-data",
              "Export all your personal data (Data Subject Access Request)")
          .queue();

      jda.upsertCommand("gdpr-delete-my-data",
              "Request permanent deletion of your personal data (Right to be Forgotten)")
          .queue();

      jda.upsertCommand("backup-status",
              "View backup status and recovery options")
          .queue();

      jda.upsertCommand("bot-health",
              "View bot health metrics and uptime")
          .queue();

      jda.upsertCommand("bot-version",
              "Display bot version and build info")
          .queue();
    });

    // Attach listeners that handle join flows, temp channels, and slash commands.
    shardManager.addEventListener(new Eventlistener());
    shardManager.addEventListener(new OnJoin());
    shardManager.addEventListener(new TempVoiceDelete());
    shardManager.addEventListener(new CommandManager());

    // Panel module — fully self-contained, remove this line + Botcode/Panel/ to disable.
    PanelModule.register(shardManager);
  }

  /**
   * Exposes environment configuration loaded from .env.
   */
  public Dotenv getConfig() {
    return config;
  }

  /**
   * Exposes the active shard manager for external integrations.
   */
  public ShardManager getShardManager() {
    return shardManager;
  }

  /**
   * Returns process start timestamp in milliseconds since epoch.
   */
  public static long getStartEpochMs() {
    return START_EPOCH_MS;
  }

  /**
   * Returns app version from package metadata when available.
   */
  public static String getVersion() {
    Package pkg = CommsBot.class.getPackage();
    String impl = pkg != null ? pkg.getImplementationVersion() : null;
    return (impl == null || impl.isBlank()) ? "dev" : impl;
  }

  /**
   * Refreshes datasets that have implemented website fetchers.
   *
   * <p>Current supported upstream pull list:
   * commodities, trade_routes, ships, weapons, components.
   */
  private void refreshStarCitizenSnapshots() {
    String[] datasets = {
        "commodities",
        "ships",
        "weapons",
        "components",
        "mining",
        "refinery",
        "refinery_stations",
        "locations",
        "salvage",
        "armor"
    };

    int updatedOrUnchanged = 0;
    int failed = 0;
    for (String dataset : datasets) {
      StarCitizenUpdateManager.UpdateResult result = StarCitizenUpdateManager.updateDetailed(dataset);
      if (result.isSuccess()) {
        updatedOrUnchanged++;
      } else {
        failed++;
      }
    }
    System.out.println(
        "[Startup] StarCitizen snapshot refresh complete: "
            + updatedOrUnchanged
            + "/"
            + datasets.length
            + " succeeded, "
            + failed
            + " failed.");
  }

  private static Connection openDatabaseConnection(String dbPath) throws Exception {
    if (Botcode.AI.AIUtils.BotConfig.DB_ENCRYPTION_ENABLED
        && DatabaseEncryptionManager.isEncryptionEnabled()) {
      try {
        return DatabaseEncryptionManager.getEncryptedConnection(dbPath);
      } catch (Exception e) {
        System.out.println(
            "[Security] Encrypted DB connection unavailable; falling back to standard SQLite: "
                + e.getMessage());
      }
    }
    return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
  }

  /**
   * Application entry point.
   */
  public static void main(String[] args) {
    try {
      new CommsBot();
    } catch (LoginException e) {
      System.out.println("Error with Discord token (LoginException). Check TOKEN in .env");
    } catch (Exception e) {
      System.out.println("Startup failed: " + (e.getMessage() != null ? e.getMessage()
          : e.getClass().getSimpleName()));
      e.printStackTrace();
    }
  }
}
