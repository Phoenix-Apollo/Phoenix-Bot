package Botcode;


import Botcode.listeners.Eventlistener;
import Botcode.listeners.OnJoin;
import Botcode.listeners.TempVoiceDelete;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import javax.security.auth.login.LoginException;

import static net.dv8tion.jda.api.requests.GatewayIntent.*;

public class CommsBot {

    private final Dotenv config;
    private final ShardManager shardManager;

    public CommsBot() throws LoginException {
        config = Dotenv.configure().load();
        String token = config.get("TOKEN");

        DefaultShardManagerBuilder builder = DefaultShardManagerBuilder.createDefault(token);
        builder.setStatus(OnlineStatus.ONLINE);
        builder.setActivity(Activity.playing("Squadron 42"));
        builder.enableIntents(GatewayIntent.GUILD_MEMBERS, GUILD_MESSAGES, SCHEDULED_EVENTS, GUILD_PRESENCES, GUILD_VOICE_STATES, MESSAGE_CONTENT);
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.ALL);
        builder.enableCache(CacheFlag.SCHEDULED_EVENTS);
        shardManager = builder.build();

        // Initialize and configure the Eventlistener
        Eventlistener eventListener = new Eventlistener();
        shardManager.addEventListener(eventListener);
        OnJoin onJoin = new OnJoin();
        shardManager.addEventListener(onJoin);
        TempVoiceDelete tempVoiceDelete = new TempVoiceDelete();
        shardManager.addEventListener(tempVoiceDelete);


    }

    public Dotenv getConfig() {
        return config;
    }

    public ShardManager getShardManager() {
        return shardManager;
    }

    public static void main(String[] args) {
        try {
            CommsBot bot = new CommsBot();
        } catch (LoginException e) {
            System.out.println("Error With Token");
        }
    }
}
