package Botcode.Panel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Persists the latest panel message location so startup can refresh it in-place.
 */
public class PanelStateStore {

  private static final String STATE_FILE = "data/panel_state.json";
  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  public static class PanelState {

    public long channelId;
    public long messageId;
  }

  public static PanelState load() {
    Map<Long, Long> all = loadAll();
    if (all.isEmpty()) {
      return null;
    }
    Map.Entry<Long, Long> first = all.entrySet().iterator().next();
    PanelState state = new PanelState();
    state.channelId = first.getKey();
    state.messageId = first.getValue();
    return state;
  }

  public static PanelState loadForChannel(long channelId) {
    Long messageId = loadAll().get(channelId);
    if (messageId == null || messageId <= 0) {
      return null;
    }
    PanelState state = new PanelState();
    state.channelId = channelId;
    state.messageId = messageId;
    return state;
  }

  public static void save(long channelId, long messageId) {
    File file = new File(STATE_FILE);
    try {
      if (file.getParentFile() != null) {
        file.getParentFile().mkdirs();
      }
      Map<Long, Long> all = loadAll();
      all.put(channelId, messageId);
      writeAll(all);
    } catch (IOException e) {
      System.out.println("[PanelState] Failed to save: " + e.getMessage());
    }
  }

  public static void clear(long channelId) {
    try {
      Map<Long, Long> all = loadAll();
      if (all.remove(channelId) != null) {
        writeAll(all);
      }
    } catch (IOException e) {
      System.out.println("[PanelState] Failed to clear: " + e.getMessage());
    }
  }

  private static Map<Long, Long> loadAll() {
    Map<Long, Long> out = new HashMap<>();
    File file = new File(STATE_FILE);
    if (!file.exists()) {
      return out;
    }
    try {
      JsonNode root = MAPPER.readTree(file);
      if (root == null || !root.isObject()) {
        return out;
      }

      // Legacy format support: { "channelId": <long>, "messageId": <long> }
      long channelId = root.path("channelId").asLong(0);
      long messageId = root.path("messageId").asLong(0);
      if (channelId > 0 && messageId > 0) {
        out.put(channelId, messageId);
        return out;
      }

      // New format: { "channels": { "<channelId>": <messageId>, ... } }
      JsonNode channels = root.path("channels");
      if (channels.isObject()) {
        Iterator<String> ids = channels.fieldNames();
        while (ids.hasNext()) {
          String id = ids.next();
          try {
            long parsedId = Long.parseLong(id);
            long parsedMsg = channels.path(id).asLong(0);
            if (parsedId > 0 && parsedMsg > 0) {
              out.put(parsedId, parsedMsg);
            }
          } catch (NumberFormatException ignored) {
            // Ignore invalid channel-id keys.
          }
        }
      }
    } catch (IOException e) {
      System.out.println("[PanelState] Failed to load: " + e.getMessage());
    }
    return out;
  }

  private static void writeAll(Map<Long, Long> states) throws IOException {
    File file = new File(STATE_FILE);
    if (file.getParentFile() != null) {
      file.getParentFile().mkdirs();
    }
    ObjectNode root = MAPPER.createObjectNode();
    ObjectNode channels = MAPPER.createObjectNode();
    for (Map.Entry<Long, Long> entry : states.entrySet()) {
      channels.put(Long.toString(entry.getKey()), entry.getValue());
    }
    root.set("channels", channels);
    MAPPER.writeValue(file, root);
  }
}
