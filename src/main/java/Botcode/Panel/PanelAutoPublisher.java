package Botcode.Panel;

import Botcode.Utils.ChannelConfig;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Automatically posts (or refreshes) the panel in all configured GUI channels.
 *
 * <p>Configuration: set env var {@code PANEL_CHANNEL_IDS} to comma-separated Discord text channel
 * IDs. Can also be configured via {@code @bot addguichannel} and {@code @bot removeguichannel}
 * commands. On startup, the previous panel messages are deleted (if known) and fresh ones are
 * posted, making the panel the latest message in each configured channel.
 */
public class PanelAutoPublisher extends ListenerAdapter {

  private static final AtomicBoolean PANEL_POSTED_THIS_BOOT = new AtomicBoolean(false);
  private static final Map<Long, Long> LAST_BUMP_EPOCH_BY_CHANNEL = new ConcurrentHashMap<>();
  private static final long BUMP_COOLDOWN_SECONDS =
      readLongEnv("BOT_PANEL_BUMP_COOLDOWN_SECONDS", 30L);

  @Override
  public void onReady(ReadyEvent event) {
    if (PANEL_POSTED_THIS_BOOT.get()) {
      return;
    }

    // Get all configured GUI channels from ChannelConfig
    var guiChannels = ChannelConfig.getGuiChannels();
    if (guiChannels.isEmpty()) {
      System.out.println(
          "[PanelAutoPublisher] No GUI channels configured. Panel will not be posted.");
      return;
    }

    // Ensure only one shard posts the panels
    if (!PANEL_POSTED_THIS_BOOT.compareAndSet(false, true)) {
      return;
    }

    System.out.println(
        "[PanelAutoPublisher] Posting panel to " + guiChannels.size() + " configured channel(s).");

    for (long channelId : guiChannels) {
      TextChannel channel = event.getJDA().getTextChannelById(channelId);
      if (channel == null) {
        System.out.println("[PanelAutoPublisher] Channel not found on this shard: " + channelId);
        continue;
      }

      refreshChannelPanel(channel);
    }
  }

  @Override
  public void onMessageReceived(MessageReceivedEvent event) {
    if (!event.isFromGuild()) {
      return;
    }
    if (event.getAuthor().isBot()) {
      return;
    }
    if (!(event.getChannel() instanceof TextChannel channel)) {
      return;
    }

    long channelId = channel.getIdLong();
    if (!ChannelConfig.isGuiChannel(channelId)) {
      return;
    }
    if (!canBump(channelId)) {
      return;
    }

    refreshChannelPanel(channel);
  }

  public static void refreshChannelPanel(TextChannel channel) {
    if (channel == null) {
      return;
    }
    PanelStateStore.PanelState state = PanelStateStore.loadForChannel(channel.getIdLong());
    postPanelToChannel(channel, state);
  }

  /**
   * Posts the panel to a specific channel, deleting the old one if it exists.
   */
  private static void postPanelToChannel(TextChannel channel, PanelStateStore.PanelState state) {
    Runnable postFreshPanel =
        () ->
            channel
                .sendMessageEmbeds(PanelEmbeds.mainPanel())
                .addComponents(PanelButtons.mainPanelRows())
                .queue(
                    sent -> {
                      PanelStateStore.save(channel.getIdLong(), sent.getIdLong());
                      LAST_BUMP_EPOCH_BY_CHANNEL.put(
                          channel.getIdLong(), System.currentTimeMillis() / 1000L);
                      System.out.println(
                          "[PanelAutoPublisher] Posted panel in #" + channel.getName());
                    },
                    err -> {
                      System.out.println(
                          "[PanelAutoPublisher] Failed to post panel in #"
                              + channel.getName()
                              + ": "
                              + err.getMessage());
                    });

    // If we know the old panel message, delete it first so new panel is the latest message.
    if (state != null
        && state.channelId > 0
        && state.messageId > 0
        && state.channelId == channel.getIdLong()) {
      channel
          .retrieveMessageById(state.messageId)
          .queue(
              msg -> msg.delete().queue(ok -> postFreshPanel.run(), err -> postFreshPanel.run()),
              err -> postFreshPanel.run());
    } else {
      postFreshPanel.run();
    }
  }

  private static boolean canBump(long channelId) {
    long now = System.currentTimeMillis() / 1000L;
    long last = LAST_BUMP_EPOCH_BY_CHANNEL.getOrDefault(channelId, 0L);
    return now - last >= Math.max(10L, BUMP_COOLDOWN_SECONDS);
  }

  private static long readLongEnv(String key, long defaultValue) {
    try {
      String raw = System.getenv(key);
      if (raw == null || raw.isBlank()) {
        return defaultValue;
      }
      return Long.parseLong(raw.trim());
    } catch (Exception ignored) {
      return defaultValue;
    }
  }
}
