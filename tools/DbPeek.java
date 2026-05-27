import java.sql.*;

public class DbPeek {
  public static void main(String[] args) throws Exception {
    String dbPath = args.length > 0 ? args[0] : "data/starcitizen.db";
    String url = "jdbc:sqlite:" + dbPath;
    Class.forName("org.sqlite.JDBC");

    try (Connection conn = DriverManager.getConnection(url)) {
      try (Statement st = conn.createStatement();
           ResultSet rs = st.executeQuery("SELECT dataset, refreshed_at, source, status, length(payload) AS len FROM datasets ORDER BY refreshed_at DESC")) {
        System.out.println("dataset\trefreshed_at\tsource\tstatus\tlen");
        while (rs.next()) {
          System.out.println(
              rs.getString("dataset") + "\t"
                  + rs.getLong("refreshed_at") + "\t"
                  + rs.getString("source") + "\t"
                  + rs.getString("status") + "\t"
                  + rs.getInt("len"));
        }
      }

      try (PreparedStatement ps = conn.prepareStatement("SELECT payload FROM datasets WHERE dataset='ships'")) {
        try (ResultSet rs = ps.executeQuery()) {
          if (rs.next()) {
            String payload = rs.getString(1);
            if (payload == null) {
              System.out.println("ships payload: <null>");
            } else {
              int weaponsEmpty = count(payload, "\"weapons\":[]");
              int turretsEmpty = count(payload, "\"turrets\":[]");
              int missilesEmpty = count(payload, "\"missiles\":[]");
              int pilotDpsZero = count(payload, "\"pilot_dps\":0.0");
              int hasPanther = payload.contains("CF-337 Panther Repeater") ? 1 : 0;
              int has300i = payload.contains("\"300i\"") ? 1 : 0;
              System.out.println("ships diagnostics:");
              System.out.println("  weapons empty entries: " + weaponsEmpty);
              System.out.println("  turrets empty entries: " + turretsEmpty);
              System.out.println("  missiles empty entries: " + missilesEmpty);
              System.out.println("  pilot_dps zero entries: " + pilotDpsZero);
              System.out.println("  contains 300i: " + has300i);
              System.out.println("  contains CF-337 Panther Repeater string: " + hasPanther);
            }
          }
        }
      }
    }
  }

  private static int count(String s, String needle) {
    int c = 0;
    int i = 0;
    while ((i = s.indexOf(needle, i)) >= 0) {
      c++;
      i += needle.length();
    }
    return c;
  }
}

