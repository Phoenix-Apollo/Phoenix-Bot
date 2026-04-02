package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Converts raw API payloads into a stable internal JSON shape used by commands.
 *
 * Each normalizer ensures missing sections are initialized and adds update
 * timestamps so downstream features can track data freshness.
 */
public class StarCitizenNormalizer {

    private static final ObjectMapper mapper = new ObjectMapper();

    // ---------------------------------------------------------
    // UEX NORMALIZERS
    // ---------------------------------------------------------

    /**
     * Normalizes commodity payloads and computes best buy/sell plus profit metadata.
     */
    public static JsonNode normalizeCommodities(JsonNode raw) {
        ObjectNode root = mapper.createObjectNode();

        if (raw == null || !raw.isArray()) {
            return root;
        }

        for (JsonNode commodity : raw) {
            String name = commodity.path("name").asText();
            if (name.isEmpty()) continue;

            ObjectNode entry = mapper.createObjectNode();

            // Copy buy/sell arrays directly
            entry.set("buy", commodity.path("buy").isMissingNode() ? mapper.createArrayNode() : commodity.path("buy"));
            entry.set("sell", commodity.path("sell").isMissingNode() ? mapper.createArrayNode() : commodity.path("sell"));

            // Compute best buy
            double bestBuy = Double.MAX_VALUE;
            String bestBuyLoc = null;

            for (JsonNode b : entry.withArray("buy")) {
                double price = b.path("price").asDouble(Double.MAX_VALUE);
                if (price < bestBuy) {
                    bestBuy = price;
                    bestBuyLoc = b.path("location").asText();
                }
            }

            // Compute best sell
            double bestSell = 0;
            String bestSellLoc = null;

            for (JsonNode s : entry.withArray("sell")) {
                double price = s.path("price").asDouble(0);
                if (price > bestSell) {
                    bestSell = price;
                    bestSellLoc = s.path("location").asText();
                }
            }

            entry.put("best_buy", bestBuyLoc == null ? "" : bestBuyLoc);
            entry.put("best_sell", bestSellLoc == null ? "" : bestSellLoc);

            // Profit per SCU
            if (bestBuyLoc != null && bestSellLoc != null) {
                entry.put("profit_per_scu", bestSell - bestBuy);
            } else {
                entry.put("profit_per_scu", 0);
            }

            entry.put("last_update", System.currentTimeMillis() / 1000);

            root.set(name, entry);
        }

        return root;
    }

    /**
     * Normalizes trade route payloads into commodity-keyed route summaries.
     */
    public static JsonNode normalizeTradeRoutes(JsonNode raw) {
        ObjectNode root = mapper.createObjectNode();

        if (raw == null || !raw.isArray()) {
            return root;
        }

        for (JsonNode route : raw) {
            String commodity = route.path("commodity").asText();
            if (commodity.isEmpty()) continue;

            ObjectNode entry = mapper.createObjectNode();

            entry.put("best_buy", route.path("best_buy").asText(""));
            entry.put("best_sell", route.path("best_sell").asText(""));
            entry.put("profit_per_scu", route.path("profit_per_scu").asDouble(0));
            entry.put("distance", route.path("distance").asDouble(0));
            entry.put("risk", route.path("risk").asText("unknown"));
            entry.put("last_update", System.currentTimeMillis() / 1000);

            root.set(commodity, entry);
        }

        return root;
    }

    // ---------------------------------------------------------
    // PLACEHOLDER NORMALIZERS FOR FUTURE DATASETS
    // ---------------------------------------------------------

    /**
     * Normalizes ship payloads into info/stats/weapons/component sections.
     */
    public static JsonNode normalizeShips(JsonNode raw) {
        ObjectNode root = mapper.createObjectNode();

        if (raw == null || !raw.isArray()) {
            return root;
        }

        for (JsonNode ship : raw) {
            String name = ship.path("name").asText();
            if (name.isEmpty()) continue;

            ObjectNode entry = mapper.createObjectNode();

            // -------------------------
            // INFO (brief summary)
            // -------------------------
            ObjectNode info = mapper.createObjectNode();
            info.put("manufacturer", ship.path("manufacturer").asText(""));
            info.put("role", ship.path("role").asText(""));
            info.put("size", ship.path("size").asText(""));
            info.put("crew", ship.path("crew").asInt(0));
            info.put("cargo", ship.path("cargo").asInt(0));
            info.put("scm_speed", ship.path("scm_speed").asInt(0));
            info.put("max_speed", ship.path("max_speed").asInt(0));
            info.put("shield_hp", ship.path("shield_hp").asInt(0));
            info.put("hull_hp", ship.path("hull_hp").asInt(0));
            entry.set("info", info);

            // -------------------------
            // STATS (important only)
            // -------------------------
            ObjectNode stats = mapper.createObjectNode();
            stats.put("mass", ship.path("mass").asDouble(0));
            stats.put("pitch", ship.path("pitch").asDouble(0));
            stats.put("yaw", ship.path("yaw").asDouble(0));
            stats.put("roll", ship.path("roll").asDouble(0));
            stats.put("fuel_capacity", ship.path("fuel_capacity").asDouble(0));
            stats.put("quantum_fuel", ship.path("quantum_fuel").asDouble(0));
            stats.put("qt_speed", ship.path("qt_speed").asDouble(0));
            stats.put("qt_range", ship.path("qt_range").asDouble(0));
            entry.set("stats", stats);

            // -------------------------
            // WEAPONS
            // -------------------------
            entry.set("weapons",
                    ship.path("weapons").isArray()
                            ? ship.path("weapons")
                            : mapper.createArrayNode()
            );

            // -------------------------
            // MISSILES
            // -------------------------
            entry.set("missiles",
                    ship.path("missiles").isArray()
                            ? ship.path("missiles")
                            : mapper.createArrayNode()
            );

            // -------------------------
            // TURRETS
            // -------------------------
            entry.set("turrets",
                    ship.path("turrets").isArray()
                            ? ship.path("turrets")
                            : mapper.createArrayNode()
            );

            // -------------------------
            // COMPONENTS
            // -------------------------
            entry.set("components",
                    ship.path("components").isObject()
                            ? ship.path("components")
                            : mapper.createObjectNode()
            );

            // -------------------------
            // TIMESTAMP
            // -------------------------
            entry.put("last_update", System.currentTimeMillis() / 1000);

            root.set(name, entry);
        }

        return root;
    }

    /**
     * Normalizes weapon payloads into info/stats/ammo/fire mode sections.
     */
    public static JsonNode normalizeWeapons(JsonNode raw) {
        ObjectNode root = mapper.createObjectNode();

        if (raw == null || !raw.isArray()) {
            return root;
        }

        for (JsonNode weapon : raw) {
            String name = weapon.path("name").asText();
            if (name.isEmpty()) continue;

            ObjectNode entry = mapper.createObjectNode();

            // -------------------------
            // INFO (brief summary)
            // -------------------------
            ObjectNode info = mapper.createObjectNode();
            info.put("manufacturer", weapon.path("manufacturer").asText(""));
            info.put("type", weapon.path("type").asText(""));
            info.put("size", weapon.path("size").asInt(0));
            info.put("class", weapon.path("class").asText(""));
            info.put("damage_type", weapon.path("damage_type").asText(""));
            entry.set("info", info);

            // -------------------------
            // STATS (important only)
            // -------------------------
            ObjectNode stats = mapper.createObjectNode();
            stats.put("dps", weapon.path("dps").asDouble(0));
            stats.put("alpha_damage", weapon.path("alpha_damage").asDouble(0));
            stats.put("rpm", weapon.path("rpm").asInt(0));
            stats.put("range", weapon.path("range").asInt(0));
            stats.put("projectile_speed", weapon.path("projectile_speed").asInt(0));
            entry.set("stats", stats);

            // -------------------------
            // AMMO (if applicable)
            // -------------------------
            entry.set("ammo",
                    weapon.path("ammo").isObject()
                            ? weapon.path("ammo")
                            : mapper.createObjectNode()
            );

            // -------------------------
            // FIRE MODES
            // -------------------------
            entry.set("fire_modes",
                    weapon.path("fire_modes").isArray()
                            ? weapon.path("fire_modes")
                            : mapper.createArrayNode()
            );

            // -------------------------
            // ATTACHMENTS
            // -------------------------
            entry.set("attachments",
                    weapon.path("attachments").isArray()
                            ? weapon.path("attachments")
                            : mapper.createArrayNode()
            );

            // -------------------------
            // TIMESTAMP
            // -------------------------
            entry.put("last_update", System.currentTimeMillis() / 1000);

            root.set(name, entry);
        }

        return root;
    }

    /**
     * Normalizes component payloads into info/stats/attributes sections.
     */
    public static JsonNode normalizeComponents(JsonNode raw) {
        ObjectNode root = mapper.createObjectNode();

        if (raw == null || !raw.isArray()) {
            return root;
        }

        for (JsonNode comp : raw) {
            String name = comp.path("name").asText();
            if (name.isEmpty()) continue;

            ObjectNode entry = mapper.createObjectNode();

            // -------------------------
            // INFO (brief summary)
            // -------------------------
            ObjectNode info = mapper.createObjectNode();
            info.put("manufacturer", comp.path("manufacturer").asText(""));
            info.put("type", comp.path("type").asText(""));
            info.put("size", comp.path("size").asInt(0));
            info.put("grade", comp.path("grade").asText(""));
            info.put("class", comp.path("class").asText(""));
            entry.set("info", info);

            // -------------------------
            // STATS (important only)
            // -------------------------
            ObjectNode stats = mapper.createObjectNode();
            stats.put("power_output", comp.path("power_output").asDouble(0));
            stats.put("cooling_rate", comp.path("cooling_rate").asDouble(0));
            stats.put("shield_hp", comp.path("shield_hp").asDouble(0));
            stats.put("quantum_speed", comp.path("quantum_speed").asDouble(0));
            stats.put("quantum_range", comp.path("quantum_range").asDouble(0));
            entry.set("stats", stats);

            // -------------------------
            // ATTRIBUTES (raw extra data)
            // -------------------------
            entry.set("attributes",
                    comp.path("attributes").isObject()
                            ? comp.path("attributes")
                            : mapper.createObjectNode()
            );

            // -------------------------
            // TIMESTAMP
            // -------------------------
            entry.put("last_update", System.currentTimeMillis() / 1000);

            root.set(name, entry);
        }

        return root;
    }
}
