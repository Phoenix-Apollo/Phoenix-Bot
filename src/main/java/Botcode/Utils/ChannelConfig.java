package Botcode.Utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the set of channels where the bot responds freely (without needing an @mention), and the
 * set of channels where the GUI panel is displayed.
 *
 * <p><b>AI Response Behavior (three-tier):</b>
 *
 * <ul>
 *   <li><b>Category-based scope:</b> If BOT_AI_CATEGORY_ID is set, bot responds in all channels
 *       within that category (primary default). Currently configured for Phoenix Industries.
 *   <li><b>Channel whitelist:</b> Explicit channels listed via BOT_AI_CHANNEL_IDS or {@code @bot
 *       allowchannel} command.
 *   <li><b>Free-response channels:</b> Channel IDs persisted to {@code data/bot_channels.json}
 * </ul>
 *
 * <p><b>GUI Panel Behavior:</b> Configured channels receive the interactive panel on startup.
 * Managed via PANEL_CHANNEL_IDS env var and {@code @bot addguichannel} / {@code @bot
 * removeguichannel} commands. GUI channels persisted to {@code data/gui_channels.json}.
 *
 * <p>All changes take effect immediately — no restart required.
 *
 * <p><b>Admin commands (via Eventlistener):</b><br>
 * {@code @bot allowchannel} — add current channel to free-response list<br>
 * {@code @bot denychannel} — remove current channel from free-response list<br>
 * {@code @bot channels} — list all configured free-response channels<br>
 * {@code @bot addguichannel} — add current channel to GUI panel display list<br>
 * {@code @bot removeguichannel} — remove current channel from GUI panel display list<br>
 * {@code @bot guichannels} — list all configured GUI panel channels
 */
public class ChannelConfig {

  private static final String FREE_CHANNELS_FILE = "data/bot_channels.json";
  private static final String GUI_CHANNELS_FILE = "data/gui_channels.json";

  private static final ObjectMapper mapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  /**
   * Set of channel IDs where the bot responds freely (no @mention required).
   */
  private static final Set<Long> freeChannels = ConcurrentHashMap.newKeySet();

  /**
   * Set of channel IDs where the GUI panel is displayed.
   */
  private static final Set<Long> guiChannels = ConcurrentHashMap.newKeySet();

  /**
   * Explicit AI interaction channels loaded from BOT_AI_CHANNEL_IDS.
   */
  private static final Set<Long> aiChannels = ConcurrentHashMap.newKeySet();

  /**
   * Optional category-wide AI scope loaded from BOT_AI_CATEGORY_ID.
   */
  private static volatile Long aiCategoryId;

  static {
    loadFreeChannels();
    loadGuiChannels();
    bootstrapFromEnvironment();
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Returns {@code true} if the bot should respond freely in the given channel.
   */
  public static boolean isFreeChannel(long channelId) {
    return freeChannels.contains(channelId);
  }

  /**
   * Adds a channel to the free-response list.
   *
   * @return {@code true} if the channel was newly added, {@code false} if already present.
   */
  public static boolean allowChannel(long channelId) {
    boolean added = freeChannels.add(channelId);
    if (added) {
      saveFreeChannels();
      System.out.println("[ChannelConfig] Free-response enabled for channel " + channelId);
    }
    return added;
  }

  /**
   * Removes a channel from the free-response list.
   *
   * @return {@code true} if removed, {@code false} if it was not in the list.
   */
  public static boolean denyChannel(long channelId) {
    boolean removed = freeChannels.remove(channelId);
    if (removed) {
      saveFreeChannels();
      System.out.println("[ChannelConfig] Free-response disabled for channel " + channelId);
    }
    return removed;
  }

  /**
   * Returns an unmodifiable snapshot of all configured free-response channel IDs.
   */
  public static Set<Long> getFreeChannels() {
    return Collections.unmodifiableSet(new HashSet<>(freeChannels));
  }

  // -------------------------------------------------------
  // GUI Channel Management
  // -------------------------------------------------------

  /**
   * Returns {@code true} if the given channel is configured to display the GUI panel.
   */
  public static boolean isGuiChannel(long channelId) {
    return guiChannels.contains(channelId);
  }

  /**
   * Adds a channel to the GUI panel display list.
   *
   * @return {@code true} if the channel was newly added, {@code false} if already present.
   */
  public static boolean addGuiChannel(long channelId) {
    boolean added = guiChannels.add(channelId);
    if (added) {
      saveGuiChannels();
      System.out.println("[ChannelConfig] GUI panel enabled for channel " + channelId);
    }
    return added;
  }

  /**
   * Removes a channel from the GUI panel display list.
   *
   * @return {@code true} if removed, {@code false} if it was not in the list.
   */
  public static boolean removeGuiChannel(long channelId) {
    boolean removed = guiChannels.remove(channelId);
    if (removed) {
      saveGuiChannels();
      System.out.println("[ChannelConfig] GUI panel disabled for channel " + channelId);
    }
    return removed;
  }

  /**
   * Returns an unmodifiable snapshot of all configured GUI channel IDs.
   */
  public static Set<Long> getGuiChannels() {
    return Collections.unmodifiableSet(new HashSet<>(guiChannels));
  }

  /**
   * Loads AI scope from environment variables and panel channels. BOT_AI_CATEGORY_ID: one category
   * ID BOT_AI_CHANNEL_IDS: comma-separated text channel IDs PANEL_CHANNEL_IDS: comma-separated
   * panel/GUI channel IDs
   */
  public static synchronized void bootstrapFromEnvironment() {
    Long category = parseLong(Env.get("BOT_AI_CATEGORY_ID"));
    aiCategoryId = category;

    aiChannels.clear();
    String rawChannels = Env.get("BOT_AI_CHANNEL_IDS");
    if (rawChannels != null && !rawChannels.isBlank()) {
      String[] parts = rawChannels.split(",");
      for (String p : parts) {
        Long id = parseLong(p);
        if (id != null && id > 0) {
          aiChannels.add(id);
        }
      }
    }

    // Load panel channels from env var (PANEL_CHANNEL_IDS)
    String rawPanelChannels = Env.get("PANEL_CHANNEL_IDS");
    if (rawPanelChannels != null && !rawPanelChannels.isBlank()) {
      String[] parts = rawPanelChannels.split(",");
      for (String p : parts) {
        Long id = parseLong(p);
        if (id != null && id > 0) {
          guiChannels.add(id);
        }
      }
    }

    // Backward compatibility: allow single-channel legacy env name.
    Long legacyPanel = parseLong(Env.get("PANEL_DEFAULT_CHANNEL_ID"));
    if (legacyPanel != null && legacyPanel > 0) {
      guiChannels.add(legacyPanel);
    }

    if (aiCategoryId != null || !aiChannels.isEmpty()) {
      System.out.println(
          "[ChannelConfig] AI scope loaded. category="
              + (aiCategoryId == null ? "none" : aiCategoryId)
              + " | channels="
              + aiChannels.size());
    }

    if (!guiChannels.isEmpty()) {
      System.out.println(
          "[ChannelConfig] GUI channels loaded from environment: " + guiChannels.size());
    } else {
      System.out.println(
          "[ChannelConfig] No GUI channels from env. Set PANEL_CHANNEL_IDS or PANEL_DEFAULT_CHANNEL_ID.");
    }
  }

  /**
   * True when a channel is inside configured AI category scope or explicitly listed.
   */
  public static boolean isAiScopedChannel(Long parentCategoryId, long channelId) {
    if (aiChannels.contains(channelId)) {
      return true;
    }
    return aiCategoryId != null
        && parentCategoryId != null
        && aiCategoryId.longValue() == parentCategoryId.longValue();
  }

  public static Long getAiCategoryId() {
    return aiCategoryId;
  }

  public static Set<Long> getAiChannels() {
    return Collections.unmodifiableSet(new HashSet<>(aiChannels));
  }

  // ---------------------------------------------------------------------------
  // Persistence
  // ---------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static void loadFreeChannels() {
    File file = new File(FREE_CHANNELS_FILE);
    if (!file.exists()) {
      return;
    }
    try {
      List<Long> ids =
          mapper.readValue(
              file, mapper.getTypeFactory().constructCollectionType(List.class, Long.class));
      freeChannels.addAll(ids);
      System.out.println("[ChannelConfig] Loaded " + ids.size() + " free-response channel(s).");
    } catch (IOException e) {
      System.err.println("[ChannelConfig] Failed to load bot_channels.json: " + e.getMessage());
    }
  }

  private static void saveFreeChannels() {
    try {
      File file = new File(FREE_CHANNELS_FILE);
      if (file.getParentFile() != null) {
        file.getParentFile().mkdirs();
      }
      mapper.writeValue(file, new ArrayList<>(freeChannels));
    } catch (IOException e) {
      System.err.println("[ChannelConfig] Failed to save bot_channels.json: " + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private static void loadGuiChannels() {
    File file = new File(GUI_CHANNELS_FILE);
    if (!file.exists()) {
      return;
    }
    try {
      List<Long> ids =
          mapper.readValue(
              file, mapper.getTypeFactory().constructCollectionType(List.class, Long.class));
      guiChannels.addAll(ids);
      System.out.println("[ChannelConfig] Loaded " + ids.size() + " GUI channel(s).");
    } catch (IOException e) {
      System.err.println("[ChannelConfig] Failed to load gui_channels.json: " + e.getMessage());
    }
  }

  private static void saveGuiChannels() {
    try {
      File file = new File(GUI_CHANNELS_FILE);
      if (file.getParentFile() != null) {
        file.getParentFile().mkdirs();
      }
      mapper.writeValue(file, new ArrayList<>(guiChannels));
    } catch (IOException e) {
      System.err.println("[ChannelConfig] Failed to save gui_channels.json: " + e.getMessage());
    }
  }

  private static Long parseLong(String raw) {
    if (raw == null) {
      return null;
    }
    String v = raw.trim();
    if (v.isEmpty()) {
      return null;
    }
    try {
      return Long.parseLong(v);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
