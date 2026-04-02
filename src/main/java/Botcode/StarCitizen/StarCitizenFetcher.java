package Botcode.StarCitizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Fetches raw Star Citizen datasets from external APIs before normalization.
 *
 * Source-specific helpers map dataset types to endpoints, while a single shared
 * HTTP method performs the request and JSON parsing.
 */
public class StarCitizenFetcher {

    private static final ObjectMapper mapper = new ObjectMapper();

    // UEX Fetcher

    /**
     * Fetches commodity market data from UEX.
     */
    public static JsonNode fetchCommodities() {
        return fetch("https://uexcorp.space/api/commodities");
    }

    /**
     * Fetches trade route data from UEX.
     */
    public static JsonNode fetchTradeRoutes() {
        return fetch("https://uexcorp.space/api/trade-routes");
    }

    // ERKUL Fetcher

    /**
     * Fetches ship data from the Erkul API.
     */
    public static JsonNode fetchShips() {
        return fetch("https://api.erkul.games/ships");
    }

    /**
     * Fetches weapon data from the Erkul API.
     */
    public static JsonNode fetchWeapons()
    {
        return fetch("https://api.erkul.games/weapons");
    }

    /**
     * Fetches component data from the Erkul API.
     */
    public static JsonNode fetchComponents()
    {
        return fetch("https://api.erkul.games/components");
    }

    // Shared fetch logic for all external endpoints.
    /**
     * Performs an HTTP GET and parses the response body as JSON.
     */
    private static JsonNode fetch(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            // Minimal headers to avoid provider-side request rejection.
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream())
            );

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();
            return mapper.readTree(response.toString());

        } catch (Exception e) {
            System.out.println("[UEXFetcher] Failed to fetch: " + urlString);
            e.printStackTrace();
            return null;
        }
    }
}
