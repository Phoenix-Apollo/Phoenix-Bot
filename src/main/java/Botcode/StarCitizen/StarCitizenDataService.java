package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * Central in-memory store for normalized Star Citizen datasets.
 *
 * Loads JSON resources at startup, exposes typed access helpers, and provides
 * lightweight fuzzy ship-name resolution for user-facing commands.
 */
public class StarCitizenDataService {

    private static final ObjectMapper mapper = new ObjectMapper();

    // Cache holds all loaded JSON files
    private static final Map<String, JsonNode> cache = new HashMap<>();

    // Direct references for fast access
    private static JsonNode ships;
    private static JsonNode weapons;
    private static JsonNode components;

    // ---------------------------------------------------------
    // INITIAL LOAD
    // ---------------------------------------------------------
    static {
        loadAll();
    }

    /**
     * Loads all supported datasets into the in-memory cache.
     */
    private static void loadAll() {

        // Load all JSON files into cache
        load("ships");
        load("weapons");
        load("components");
        load("commodities");
        load("trade_routes");
        load("mining");
        load("refinery");
        load("salvage");
        load("locations");
        load("missions");
        load("items");
        load("armor");

        // ---------------------------------------------------------
        // ASSIGN DIRECT REFERENCES
        // ---------------------------------------------------------
        // (This was missing — without this, ships/weapons/components were always null)
        ships = cache.get("ships");
        weapons = cache.get("weapons");
        components = cache.get("components");
    }

    // ---------------------------------------------------------
    // RELOAD A SINGLE DATASET
    // ---------------------------------------------------------
    /**
     * Reloads one dataset from resources and refreshes direct references if needed.
     */
    public static void reload(String name) {
        load(name);

        // Keep direct dataset handles in sync after a targeted reload.
        // Update direct references when reloading
        if (name.equals("ships")) ships = cache.get("ships");
        if (name.equals("weapons")) weapons = cache.get("weapons");
        if (name.equals("components")) components = cache.get("components");
    }

    // ---------------------------------------------------------
    // LOAD A SINGLE JSON FILE
    // ---------------------------------------------------------
    /**
     * Reads a single JSON file from resources and stores it in the cache map.
     */
    private static void load(String name) {
        try {
            InputStream stream = StarCitizenDataService.class.getResourceAsStream(
                    "/starcitizen/" + name + ".json"
            );

            if (stream != null) {
                JsonNode json = mapper.readTree(stream);
                cache.put(name, json);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------------------------------------------------------
    // RAW ACCESSOR (rarely used)
    // ---------------------------------------------------------
    /**
     * Returns a cached dataset by file key.
     */
    public static JsonNode get(String name) {
        return cache.get(name);
    }

    // ---------------------------------------------------------
    // SHIP QUERY FUNCTIONS
    // ---------------------------------------------------------

    /**
     * Returns the full normalized ship object for a given ship name.
     */
    public static JsonNode getShip(String name) {
        return ships != null ? ships.path(name) : null;
    }

    /**
     * Returns the ship info subsection.
     */
    public static JsonNode getShipInfo(String name) {
        JsonNode ship = getShip(name);
        return ship != null ? ship.path("info") : null;
    }

    /**
     * Returns the ship stats subsection.
     */
    public static JsonNode getShipStats(String name) {
        JsonNode ship = getShip(name);
        return ship != null ? ship.path("stats") : null;
    }

    /**
     * Returns mounted weapon entries for the ship.
     */
    public static JsonNode getShipWeapons(String name) {
        JsonNode ship = getShip(name);
        return ship != null ? ship.path("weapons") : null;
    }

    /**
     * Returns missile configuration entries for the ship.
     */
    public static JsonNode getShipMissiles(String name) {
        JsonNode ship = getShip(name);
        return ship != null ? ship.path("missiles") : null;
    }

    /**
     * Returns turret configuration entries for the ship.
     */
    public static JsonNode getShipTurrets(String name) {
        JsonNode ship = getShip(name);
        return ship != null ? ship.path("turrets") : null;
    }

    /**
     * Returns component slots/assignments for the ship.
     */
    public static JsonNode getShipComponents(String name) {
        JsonNode ship = getShip(name);
        return ship != null ? ship.path("components") : null;
    }

    /**
     * Convenience alias for returning the complete ship object.
     */
    public static JsonNode getShipFull(String name) {
        return getShip(name);
    }

    // ---------------------------------------------------------
    // WEAPON QUERY FUNCTIONS
    // ---------------------------------------------------------

    /**
     * Returns the full normalized weapon object for a given weapon name.
     */
    public static JsonNode getWeapon(String name) {
        return weapons != null ? weapons.path(name) : null;
    }

    /**
     * Returns the weapon info subsection.
     */
    public static JsonNode getWeaponInfo(String name) {
        JsonNode weapon = getWeapon(name);
        return weapon != null ? weapon.path("info") : null;
    }

    /**
     * Returns the weapon stats subsection.
     */
    public static JsonNode getWeaponStats(String name) {
        JsonNode weapon = getWeapon(name);
        return weapon != null ? weapon.path("stats") : null;
    }

    /**
     * Returns the weapon ammo subsection.
     */
    public static JsonNode getWeaponAmmo(String name) {
        JsonNode weapon = getWeapon(name);
        return weapon != null ? weapon.path("ammo") : null;
    }

    /**
     * Returns supported fire modes for the weapon.
     */
    public static JsonNode getWeaponFireModes(String name) {
        JsonNode weapon = getWeapon(name);
        return weapon != null ? weapon.path("fire_modes") : null;
    }

    /**
     * Returns attachment compatibility information.
     */
    public static JsonNode getWeaponAttachments(String name) {
        JsonNode weapon = getWeapon(name);
        return weapon != null ? weapon.path("attachments") : null;
    }

    /**
     * Convenience alias for returning the complete weapon object.
     */
    public static JsonNode getWeaponFull(String name) {
        return getWeapon(name);
    }

    // ---------------------------------------------------------
    // COMPONENT QUERY FUNCTIONS
    // ---------------------------------------------------------

    /**
     * Returns the full normalized component object for a given component name.
     */
    public static JsonNode getComponent(String name) {
        return components != null ? components.path(name) : null;
    }

    /**
     * Returns the component info subsection.
     */
    public static JsonNode getComponentInfo(String name) {
        JsonNode comp = getComponent(name);
        return comp != null ? comp.path("info") : null;
    }

    /**
     * Returns the component stats subsection.
     */
    public static JsonNode getComponentStats(String name) {
        JsonNode comp = getComponent(name);
        return comp != null ? comp.path("stats") : null;
    }

    /**
     * Returns additional raw component attributes.
     */
    public static JsonNode getComponentAttributes(String name) {
        JsonNode comp = getComponent(name);
        return comp != null ? comp.path("attributes") : null;
    }

    /**
     * Convenience alias for returning the complete component object.
     */
    public static JsonNode getComponentFull(String name) {
        return getComponent(name);
    }
    // ---------------------------------------------------------
    // NAME RESOLVER (Balanced fuzzy matching + aliases)
    // ---------------------------------------------------------

    private static final Map<String, String> SHIP_ALIASES = Map.ofEntries(
            Map.entry("cutty", "Cutlass Black"),
            Map.entry("cutlass", "Cutlass Black"),
            Map.entry("glad", "Gladius"),
            Map.entry("hawk", "Anvil Hawk"),
            Map.entry("redee", "Redeemer"),
            Map.entry("carr", "Carrack"),
            Map.entry("cat", "Caterpillar"),
            Map.entry("valk", "Valkyrie"),
            Map.entry("valky", "Valkyrie"),
            Map.entry("arrow", "Aegis Arrow")
    );

    /**
     * Resolves user-entered ship names using aliases plus fuzzy scoring.
     *
     * Returns null when confidence is too low or top results are ambiguous.
     */
    public static String resolveShipName(String input) {

        if (input == null || input.isBlank()) return null;
        input = input.toLowerCase().trim();

        JsonNode shipData = ships;
        if (shipData == null) return null;

        // 1. Alias match
        if (SHIP_ALIASES.containsKey(input)) {
            return SHIP_ALIASES.get(input);
        }

        // 2. Build list of ship names
        List<String> names = new ArrayList<>();
        shipData.fieldNames().forEachRemaining(names::add);

        // Score candidates so exact/prefix matches win before fuzzy distance ties.
        // 3. Score matches
        Map<String, Integer> scored = new HashMap<>();
        for (String name : names) {
            int score = scoreMatch(input, name.toLowerCase());
            if (score > 0) scored.put(name, score);
        }

        if (scored.isEmpty()) return null;

        // 4. Sort by score
        List<Map.Entry<String, Integer>> sorted = scored.entrySet()
                .stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .toList();

        String best = sorted.get(0).getKey();
        int bestScore = sorted.get(0).getValue();

        // 5. Ambiguity check
        if (sorted.size() > 1) {
            int second = sorted.get(1).getValue();
            if (Math.abs(bestScore - second) < 10) {
                return null; // too close → ask user to clarify
            }
        }

        // 6. Minimum confidence
        if (bestScore < 50) return null;

        return best;
    }

    /**
     * Scores one candidate ship name against user input.
     */
    private static int scoreMatch(String input, String target) {

        if (input.equals(target)) return 100;
        if (target.startsWith(input)) return 90;
        if (target.contains(input)) return 75;

        // Fall back to edit-distance similarity when no direct substring hit exists.
        int dist = levenshtein(input, target);
        int max = Math.max(input.length(), target.length());
        int similarity = (int) ((1.0 - (double) dist / max) * 70);

        return Math.max(similarity, 0);
    }

    // Standard dynamic-programming Levenshtein implementation.
    /**
     * Computes edit distance used by fuzzy score fallback.
     */
    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;

                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[a.length()][b.length()];
    }
}
