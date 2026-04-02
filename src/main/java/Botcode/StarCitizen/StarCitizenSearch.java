package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides field-based lookup helpers over normalized ship, weapon, and component data.
 *
 * Search methods perform exact case-insensitive matches against common sections,
 * then formatter helpers return either names, full objects, or keyed maps.
 */
public class StarCitizenSearch {

    // ---------------------------------------------------------
    // SHIP SEARCH
    // ---------------------------------------------------------
    /**
     * Finds ships where the requested field matches the provided value.
     */
    public static List<String> searchShips(String field, String value) {
        List<String> results = new ArrayList<>();
        JsonNode ships = StarCitizenDataService.get("ships");

        if (ships == null) return results;

        ships.fieldNames().forEachRemaining(name -> {
            JsonNode ship = ships.get(name);
            JsonNode info = ship.path("info");
            JsonNode stats = ship.path("stats");

            if (info.has(field) && info.get(field).asText().equalsIgnoreCase(value)) {
                results.add(name);
                return;
            }

            if (stats.has(field) && stats.get(field).asText().equalsIgnoreCase(value)) {
                results.add(name);
            }
        });

        return results;
    }

    // ---------------------------------------------------------
    // WEAPON SEARCH
    // ---------------------------------------------------------
    /**
     * Finds weapons where the requested field matches the provided value.
     */
    public static List<String> searchWeapons(String field, String value) {
        List<String> results = new ArrayList<>();
        JsonNode weapons = StarCitizenDataService.get("weapons");

        if (weapons == null) return results;

        weapons.fieldNames().forEachRemaining(name -> {
            JsonNode weapon = weapons.get(name);
            JsonNode info = weapon.path("info");
            JsonNode stats = weapon.path("stats");

            if (info.has(field) && info.get(field).asText().equalsIgnoreCase(value)) {
                results.add(name);
                return;
            }

            if (stats.has(field) && stats.get(field).asText().equalsIgnoreCase(value)) {
                results.add(name);
            }
        });

        return results;
    }

    // ---------------------------------------------------------
    // COMPONENT SEARCH
    // ---------------------------------------------------------
    /**
     * Finds components where info/stats/attributes fields match the provided value.
     */
    public static List<String> searchComponents(String field, String value) {
        List<String> results = new ArrayList<>();
        JsonNode components = StarCitizenDataService.get("components");

        if (components == null) return results;

        components.fieldNames().forEachRemaining(name -> {
            JsonNode comp = components.get(name);
            JsonNode info = comp.path("info");
            JsonNode stats = comp.path("stats");
            JsonNode attrs = comp.path("attributes");

            if (info.has(field) && info.get(field).asText().equalsIgnoreCase(value)) {
                results.add(name);
                return;
            }

            if (stats.has(field) && stats.get(field).asText().equalsIgnoreCase(value)) {
                results.add(name);
                return;
            }

            if (attrs.has(field) && attrs.get(field).asText().equalsIgnoreCase(value)) {
                results.add(name);
            }
        });

        return results;
    }

    // ---------------------------------------------------------
    // FORMATTING OPTIONS
    // ---------------------------------------------------------
    // Formatting utilities used by command handlers for different response shapes.
    /**
     * Returns plain entity names without extra formatting.
     */
    public static List<String> formatNamesOnly(List<String> names) {
        return names;
    }

    /**
     * Resolves entity names into full normalized objects.
     */
    public static List<JsonNode> formatFullObjects(List<String> names, String type) {
        List<JsonNode> list = new ArrayList<>();

        for (String n : names) {
            switch (type) {
                case "ship" -> list.add(StarCitizenDataService.getShipFull(n));
                case "weapon" -> list.add(StarCitizenDataService.getWeaponFull(n));
                case "component" -> list.add(StarCitizenDataService.getComponentFull(n));
            }
        }

        return list;
    }

    /**
     * Resolves entity names into a name-to-object map preserving iteration order.
     */
    public static Map<String, JsonNode> formatAllSections(List<String> names, String type) {
        Map<String, JsonNode> map = new LinkedHashMap<>();

        for (String n : names) {
            switch (type) {
                case "ship" -> map.put(n, StarCitizenDataService.getShipFull(n));
                case "weapon" -> map.put(n, StarCitizenDataService.getWeaponFull(n));
                case "component" -> map.put(n, StarCitizenDataService.getComponentFull(n));
            }
        }

        return map;
    }
}
