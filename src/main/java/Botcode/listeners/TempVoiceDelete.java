package Botcode.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class TempVoiceDelete extends ListenerAdapter {

    private final Map<Long, Long> emptyChannels = new HashMap<>();
    private final Map<Long, Guild> channelGuildMap = new HashMap<>();
    private final Timer timer = new Timer();

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        VoiceChannel channelLeft = (VoiceChannel) event.getChannelLeft();
        if (channelLeft == null || channelLeft.getName().equals("Join to Create") || channelLeft.getName().contains("~")) {
            return; // Exclude Join to Create channels and channels with *
        }

        long channelId = channelLeft.getIdLong();

        if (emptyChannels.containsKey(channelId)) {
            emptyChannels.remove(channelId);
            channelGuildMap.remove(channelId);
        }

        if (channelLeft.getMembers().isEmpty()) {
            emptyChannels.put(channelId, System.currentTimeMillis());
            channelGuildMap.put(channelId, event.getGuild());
            scheduleDeletion(channelId);
        }
    }

    private void scheduleDeletion(Long channelId) {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Guild guild = channelGuildMap.get(channelId);
                if (guild == null) return;

                VoiceChannel channel = guild.getVoiceChannelById(channelId);
                if (channel != null && channel.getMembers().isEmpty()) {
                    channel.delete().queue(
                            success -> {
                                System.out.println("Deleted channel: " + channelId);
                                emptyChannels.remove(channelId);
                                channelGuildMap.remove(channelId);
                            },
                            failure -> System.out.println("Failed to delete channel: " + channelId)
                    );
                }
            }
        }, 3000); // 3 seconds
    }
}