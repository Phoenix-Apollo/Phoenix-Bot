package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight learned query aliasing (e.g., "qt" -> "quantum drives").
 */
public class QueryAliasService {

  private static final String FILE_PATH = "data/query_aliases.json";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Map<String, String> aliases = new ConcurrentHashMap<>();

  static {
    load();
    // Built-in baseline aliases.
    aliases.putIfAbsent("qt", "quantum drives");
    aliases.putIfAbsent("qtd", "quantum drives");
    aliases.putIfAbsent("qdrive", "quantum drives");
    aliases.putIfAbsent("mining hotspots", "best spots to mine");
    aliases.putIfAbsent("best spots to mine", "mining hotspots");
  }

  private QueryAliasService() {
  }

  public static boolean learnAlias(String from, String to) {
    String a = normalizeAliasKey(from);
    String b = normalizeAliasValue(to);
    if (a == null || b == null) {
      return false;
    }
    aliases.put(a, b);
    save();
    return true;
  }

  public static boolean learnAliasPair(String left, String right) {
    String a = normalizeAliasKey(left);
    String b = normalizeAliasValue(right);
    if (a == null || b == null) {
      return false;
    }
    if (a.equals(b)) {
      return false;
    }

    aliases.put(a, b);
    aliases.put(normalizeAliasKey(b), normalizeAliasValue(a));
    save();
    return true;
  }

  public static String applyAliases(String text) {
    if (text == null || text.isBlank() || aliases.isEmpty()) {
      return text;
    }

    List<String> keys = new ArrayList<>(aliases.keySet());
    keys.sort(Comparator.comparingInt(String::length).reversed());
    if (keys.isEmpty()) {
      return text;
    }

    String alternation = String.join("|", keys.stream().map(Pattern::quote).toList());
    Pattern pattern = Pattern.compile("(?i)\\b(?:" + alternation + ")\\b");
    Matcher matcher = pattern.matcher(text);
    StringBuffer out = new StringBuffer();
    while (matcher.find()) {
      String matched = matcher.group();
      String normalized = normalizeAliasKey(matched);
      String replacement = normalized == null ? matched : aliases.getOrDefault(normalized, matched);
      matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(out);
    return out.toString();
  }

  private static String normalizeAliasKey(String value) {
    if (value == null) {
      return null;
    }
    String clean =
        value
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s-]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    if (clean.length() < 2 || clean.length() > 64) {
      return null;
    }
    if (!clean.matches(".*[a-z0-9].*")) {
      return null;
    }
    return clean;
  }

  private static String normalizeAliasValue(String value) {
    if (value == null) {
      return null;
    }
    String clean =
        value
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s-]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    if (clean.length() < 3 || clean.length() > 96) {
      return null;
    }
    return clean;
  }

  private static void load() {
    try {
      File f = new File(FILE_PATH);
      if (!f.exists()) {
        return;
      }
      JsonNode root = MAPPER.readTree(f);
      if (root == null || !root.isObject()) {
        return;
      }
      Iterator<String> fields = root.fieldNames();
      while (fields.hasNext()) {
        String key = fields.next();
        String value = root.path(key).asText("");
        String a = normalizeAliasKey(key);
        String b = normalizeAliasValue(value);
        if (a != null && b != null) {
          aliases.put(a, b);
        }
      }
    } catch (Exception ignored) {
    }
  }

  private static void save() {
    try {
      File f = new File(FILE_PATH);
      if (f.getParentFile() != null) {
        f.getParentFile().mkdirs();
      }
      ObjectNode root = MAPPER.createObjectNode();
      aliases.forEach(root::put);
      try (Writer w =
          new OutputStreamWriter(Files.newOutputStream(f.toPath()), StandardCharsets.UTF_8)) {
        w.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
      }
    } catch (Exception e) {
      System.out.println("[QueryAliasService] Failed to save aliases: " + e.getMessage());
    }
  }
}
