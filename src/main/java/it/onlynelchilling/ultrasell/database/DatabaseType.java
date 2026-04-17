package it.onlynelchilling.ultrasell.database;

public enum DatabaseType {
    SQLITE("org.sqlite.JDBC"),
    H2("org.h2.Driver"),
    MYSQL("com.mysql.cj.jdbc.Driver"),
    MARIADB("org.mariadb.jdbc.Driver");

    private final String driver;

    DatabaseType(String driver) { this.driver = driver; }

    public String driver() { return driver; }

    public static DatabaseType from(String s) {
        if (s == null) return SQLITE;
        try { return valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return SQLITE; }
    }
}

