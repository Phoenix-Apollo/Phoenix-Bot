package Botcode;

import Botcode.Commands.CommandManager;
import Botcode.listeners.Eventlistener;
import Botcode.listeners.OnJoin;
import Botcode.listeners.TempVoiceDelete;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;

import static net.dv8tion.jda.api.requests.GatewayIntent.*;

/**
 * Bootstraps JDA, registers slash commands, and wires all event listeners.
 *
 * This is the main composition root for the bot runtime.
 */
public class CommsBot {

    private final Dotenv config;
    private final ShardManager shardManager;

    /**
     * Creates the shard manager and registers commands/listeners for each shard.
     */
    public CommsBot() throws LoginException {
        config = Dotenv.configure().load();
        String token = config.get("TOKEN");

        // Preserve all gateway intents so downstream listeners receive required events.
        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createDefault(token)
                .enableIntents(GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS));

        // Runtime presence/cache settings required by member and voice workflows.
        builder.setStatus(OnlineStatus.ONLINE);
        builder.setActivity(Activity.playing("Squadron 42"));
        builder.enableIntents(GUILD_MEMBERS, GUILD_MESSAGES, SCHEDULED_EVENTS, GUILD_PRESENCES, GUILD_VOICE_STATES, MESSAGE_CONTENT);
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);
        builder.enableCache(CacheFlag.SCHEDULED_EVENTS);

        shardManager = builder.build();

        // Register slash commands for each shard instance.
        shardManager.getShards().forEach(jda -> {

            // Commodity command
            jda.upsertCommand("commodity", "Get commodity trading info")
                    .addOption(OptionType.STRING, "name", "Commodity name", true)
                    .queue();

            // Mining command (added)
            jda.upsertCommand("mine", "Analyze a mineable rock")
                    .addOption(OptionType.STRING, "rock", "Rock name", true, true)
                    .addOption(OptionType.STRING, "ship", "Ship name", true, true)
                    .addOption(OptionType.STRING, "laser", "Mining laser", true, true)
                    .addOption(OptionType.STRING, "consumable", "Mining consumable", true, true)
                    .addOption(OptionType.INTEGER, "operators", "Number of lasers", true)
                    .addOption(OptionType.STRING, "format", "brief, full, or specific", true, true)
                    .addOption(OptionType.STRING, "field", "Field for specific mode", false, true)
                    .queue();
        });

        // Attach listeners that handle join flows, temp channels, and slash commands.
        shardManager.addEventListener(new Eventlistener());
        shardManager.addEventListener(new OnJoin());
        shardManager.addEventListener(new TempVoiceDelete());
        shardManager.addEventListener(new CommandManager());
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
     * Application entry point.
     */
    public static void main(String[] args) {
        try {
            new CommsBot();
        } catch (LoginException e) {
            System.out.println("Error With Token");
        }
    }
}
