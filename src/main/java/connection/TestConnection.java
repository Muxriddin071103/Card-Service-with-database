package connection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class TestConnection {
    private static final String DATABASE_URL = "jdbc:postgresql://127.0.0.1:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASSWORD = "root123";

    private static TestConnection instance;

    private TestConnection() {
        // private constructor to enforce singleton pattern
    }

    public static TestConnection getInstance() {
        if (instance == null) {
            instance = new TestConnection();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", USER);
        props.setProperty("password", PASSWORD);
        return DriverManager.getConnection(DATABASE_URL, props);
    }
}
