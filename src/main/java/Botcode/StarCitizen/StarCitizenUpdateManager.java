package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileWriter;

/**
 * Orchestrates fetch -> normalize -> save -> reload updates for Star Citizen datasets.
 */
public class StarCitizenUpdateManager {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String DATA_FOLDER = "data/";

    /**
     * Updates a specific dataset (commodities, trade_routes, ships, etc.)
     */
    public static boolean update(String dataset) {
        try {
            // Step 1: pull raw data from the matching upstream source.
            JsonNode raw = fetch(dataset);
            if (raw == null) {
                System.out.println("[UpdateManager] Fetch failed for: " + dataset);
                return false;
            }

            // Step 2: convert upstream data into the bot's normalized schema.
            JsonNode normalized = normalize(dataset, raw);
            if (normalized == null) {
                System.out.println("[UpdateManager] Normalize failed for: " + dataset);
                return false;
            }

            // Step 3: persist to disk and refresh in-memory references.
            save(dataset, normalized);

            // Reload into memory
            StarCitizenDataService.reload(dataset);

            System.out.println("[UpdateManager] Updated dataset: " + dataset);
            return true;

        } catch (Exception e) {
            System.out.println("[UpdateManager] Error updating dataset: " + dataset);
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Fetches raw data from the correct source based on dataset name.
     */
    private static JsonNode fetch(String dataset) {
        switch (dataset) {

            // -------------------------
            // UEX DATASETS
            // -------------------------
            case "commodities":
                return StarCitizenFetcher.fetchCommodities();

            case "trade_routes":
                return StarCitizenFetcher.fetchTradeRoutes();

            // -------------------------
            // ERKUL DATASETS (placeholders)
            // -------------------------
            case "ships":
                return StarCitizenFetcher.fetchShips();

            case "weapons":
                return StarCitizenFetcher.fetchWeapons();

            case "components":
                return StarCitizenFetcher.fetchComponents();

            // -------------------------
            // FUTURE DATASETS (SC.Tools, static, etc.)
            // -------------------------
            case "mining":
            case "refinery":
            case "salvage":
            case "locations":
            case "missions":
            case "items":
            case "armor":
                System.out.println("[UpdateManager] Fetcher not implemented for: " + dataset);
                return null;

            default:
                System.out.println("[UpdateManager] Unknown dataset: " + dataset);
                return null;
        }
    }

    /**
     * Normalizes raw data into your internal JSON format.
     */
    private static JsonNode normalize(String dataset, JsonNode raw) {
        switch (dataset) {

            // -------------------------
            // UEX NORMALIZERS
            // -------------------------
            case "commodities":
                return StarCitizenNormalizer.normalizeCommodities(raw);

            case "trade_routes":
                return StarCitizenNormalizer.normalizeTradeRoutes(raw);

            // -------------------------
            // ERKUL NORMALIZERS (placeholders)
            // -------------------------
            case "ships":
                return StarCitizenNormalizer.normalizeShips(raw);

            case "weapons":
                return StarCitizenNormalizer.normalizeWeapons(raw);

            case "components":
                return StarCitizenNormalizer.normalizeComponents(raw);

            // -------------------------
            // FUTURE NORMALIZERS
            // -------------------------
            case "mining":
            case "refinery":
            case "salvage":
            case "locations":
            case "missions":
            case "items":
            case "armor":
                System.out.println("[UpdateManager] Normalizer not implemented for: " + dataset);
                return null;

            default:
                System.out.println("[UpdateManager] Unknown dataset: " + dataset);
                return null;
        }
    }

    /**
     * Saves normalized JSON to /data/<dataset>.json
     */
    private static void save(String dataset, JsonNode json) {
        try {
            // Ensure output directory exists before writing dataset snapshots.
            File folder = new File(DATA_FOLDER);
            if (!folder.exists()) folder.mkdirs();

            FileWriter writer = new FileWriter(DATA_FOLDER + dataset + ".json");
            writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
            writer.close();

        } catch (Exception e) {
            System.out.println("[UpdateManager] Failed to save dataset: " + dataset);
            e.printStackTrace();
        }
    }
}
