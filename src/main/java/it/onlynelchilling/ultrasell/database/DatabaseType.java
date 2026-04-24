package it.onlynelchilling.ultrasell.database;

enum DatabaseType {
    SQLITE("org.sqlite.JDBC"),
    H2("org.h2.Driver"),
    MYSQL("com.mysql.cj.jdbc.Driver"),
    MARIADB("org.mariadb.jdbc.Driver");

    private final String driver;

    DatabaseType(String driver) { this.driver = driver; }

    String driver() { return driver; }

    static DatabaseType from(String s) {
        if (s == null) return SQLITE;
        try { return valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return SQLITE; }
    }
}

