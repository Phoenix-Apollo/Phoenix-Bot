package Botcode.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Deletes temporary voice channels shortly after they become empty.
 */
public class TempVoiceDelete extends ListenerAdapter {

  // Tracks channels that were observed empty and the time they were marked.
  private final Map<Long, Long> emptyChannels = new HashMap<>();
  // Maps channel IDs back to guild context needed for deletion lookup.
  private final Map<Long, Guild> channelGuildMap = new HashMap<>();
  private final Timer timer = new Timer();

  /**
   * Tracks voice channel exits and schedules deletion checks for empty channels.
   */
  @Override
  public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
    VoiceChannel channelLeft = (VoiceChannel) event.getChannelLeft();
    // Ignore permanent/control channels that should never be auto-deleted.
    if (channelLeft == null
        || channelLeft.getName().equals("Join to Create")
        || channelLeft.getName().contains("~")) {
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

  /**
   * Runs a delayed delete pass to avoid removing channels during quick reconnects.
   */
  private void scheduleDeletion(Long channelId) {
    timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            Guild guild = channelGuildMap.get(channelId);
            if (guild == null) {
              return;
            }

            VoiceChannel channel = guild.getVoiceChannelById(channelId);
            // Delete only if the channel still exists and remained empty during delay.
            if (channel != null && channel.getMembers().isEmpty()) {
              channel
                  .delete()
                  .queue(
                      success -> {
                        System.out.println("Deleted channel: " + channelId);
                        emptyChannels.remove(channelId);
                        channelGuildMap.remove(channelId);
                      },
                      failure -> System.out.println("Failed to delete channel: " + channelId));
            }
          }
        },
        3000); // 3 seconds
  }
}
