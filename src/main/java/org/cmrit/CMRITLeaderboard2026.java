package org.cmrit;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The CMRITLeaderboard2026 class is responsible for scraping and processing data from various coding platforms
 * such as Codeforces, Leetcode, GeeksforGeeks, Codechef, and Hackerrank. It loads participant details from a CSV file
 * and stores them in a SQLite database. It then scrapes the ratings of participants from each platform and calculates
 * their percentile based on the maximum rating in each platform. Finally, it builds a leaderboard by fetching the
 * ratings from the database and sorting them in descending order of percentile. The leaderboard is then saved as an
 * Excel file.
 *
 * The class provides methods for scraping ratings from each platform, loading data from CSV to the database,
 * building the leaderboard, and uploading the leaderboard to the database. It also includes helper methods for
 * splitting users into chunks for efficient scraping and parsing JSON responses from the platforms.
 *
 * The class uses external libraries such as Gson for JSON parsing and Jsoup for web scraping. It also makes use of
 * standard Java libraries for database connectivity and file handling.
 *
 * Note: This class assumes that the necessary libraries are included in the classpath and the required database
 * and file resources are available.
 */

public class CMRITLeaderboard2026 {

    private static final String CREATE_TABLE_SQL = "CREATE TABLE IF NOT EXISTS " +
            "users_data (" +
            "handle TEXT PRIMARY KEY," +
            "geeksforgeeks_handle TEXT," +
            "codeforces_handle TEXT," +
            "leetcode_handle TEXT," +
            "codechef_handle TEXT," +
            "hackerrank_handle TEXT," +
            "codeforces_url_exists INTEGER," +
            "geeksforgeeks_url_exists INTEGER," +
            "leetcode_url_exists INTEGER," +
            "codechef_url_exists INTEGER," +
            "hackerrank_url_exists INTEGER)";

    private static final String INSERT_DATA_SQL = "REPLACE INTO users_data " +
            "(handle, geeksforgeeks_handle, codeforces_handle, leetcode_handle, codechef_handle, hackerrank_handle, " +
            "geeksforgeeks_url_exists, codeforces_url_exists, leetcode_url_exists, codechef_url_exists, hackerrank_url_exists) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String CODECHEF_URL = "https://code-chef-rating-api.vercel.app/";
    private static final String CODEFORCES_URL = "https://codeforces.com/api/user.info?handles=";
    // Load Codeforces Key and Secret from env variables
    private static final String API_KEY = System.getenv("CODEFORCES_KEY");
    private static final String API_SECRET = System.getenv("CODEFORCES_SECRET");
    private static final String LEETCODE_URL = "https://leetcode.com/graphql?query=";
    private static final String GFG_URL = "https://auth.geeksforgeeks.org/user/";
    private static final String GFG_WEEKLY_CONTEST_URL = "https://practiceapi.geeksforgeeks.org/api/latest/events/recurring/gfg-weekly-coding-contest/leaderboard/?leaderboard_type=0&page=";
    private static final String GFG_PRACTICE_URL = "https://practiceapi.geeksforgeeks.org/api/v1/institute/341/students/stats?page_size=100000&page=";
    public static List<String> SEARCH_TOKENS = new ArrayList<>();

    static Map<String, User> userMap = new HashMap<>();


    /**
     * The main function that loads data from CSV, fetches user details from the database, and performs scraping based on the specified method name.
     *
     * @param  args    the command-line arguments
     * @throws CustomScrapingException    if a custom scraping exception occurs
     */
    public static void main(String[] args) throws CustomScrapingException {

        // Load data from csv

        loadCSVtoSQL("src//main//resources//participant_details.csv");

        // Load hackerrank urls
        try (BufferedReader br = new BufferedReader(new FileReader("src//main//resources//hackerrank_urls.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Trim the line to remove leading and trailing whitespace characters
                line = line.trim();
                // Remove the last character if it's a '/'
                if (line.endsWith("/")) {
                    line = line.substring(0, line.length() - 1);
                }
                String[] parts = line.split("/");
                String lastPart = parts[parts.length - 1];
                SEARCH_TOKENS.add(lastPart);
            }
            System.out.println("Hackerrank urls loaded successfully.");
            // Print all assigned search tokens
            for (String token : SEARCH_TOKENS) {
                System.out.println(token);
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }

        String methodName = args[0];

        String dbName = "cmrit";
        Connection conn = null;
        Statement statement = null;
        ResultSet resultSet = null;
        ArrayList <User> trueCodechef = new ArrayList<>();
        ArrayList <User> trueCodeforces = new ArrayList<>();
        ArrayList <User> trueLeetcode = new ArrayList<>();
        ArrayList <User> trueGeeksforgeeks = new ArrayList<>();
        ArrayList <User> trueHackerrank = new ArrayList<>();

        Map<String, User> gfgHandleToUserMap;
        Map<String, User> hackerrankHandleToUserMap;

        switch (methodName) {
            case "codechef":
                // Fetch all true codechef handles from the database
                try {
                    conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
                    statement = conn.createStatement();

                    String sql = "SELECT handle, codechef_handle FROM users_data WHERE codechef_url_exists = 1";
                    resultSet = statement.executeQuery(sql);

                    assert resultSet != null;

                    while (resultSet.next()) {
                        String handle = resultSet.getString("handle");
                        String codechefHandle = resultSet.getString("codechef_handle");
                        if (codechefHandle != null) {
                            trueCodechef.add(new User(handle, "codechef", codechefHandle));
                        }
                    }

                } catch (SQLException e) {
                    System.err.println("Error fetching true Codechef handles: " + e.getMessage());
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                        if (conn != null) conn.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing resultSet, statement, or connection: " + e.getMessage());
                    }
                }
                scrapeCodechef(trueCodechef);
                break;
            case "codeforces":
                // Fetch all true codeforces handles from the database
                try {
                    conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
                    statement = conn.createStatement();

                    String sql = "SELECT handle, codeforces_handle FROM users_data WHERE codeforces_url_exists = 1";
                    resultSet = statement.executeQuery(sql);

                    assert resultSet != null;

                    while (resultSet.next()) {
                        String handle = resultSet.getString("handle");
                        String codeforcesHandle = resultSet.getString("codeforces_handle");
                        if (codeforcesHandle != null) {
                            trueCodeforces.add(new User(handle, "codeforces", codeforcesHandle));
                        }
                    }

                } catch (SQLException e) {
                    System.err.println("Error fetching true Codeforces handles: " + e.getMessage());
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                        if (conn != null) conn.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing resultSet, statement, or connection: " + e.getMessage());
                    }
                }
                scrapeCodeforces(trueCodeforces);
                break;
            case "leetcode":
                // Fetch all true leetcode handles from the database
                try{
                    conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
                    statement = conn.createStatement();

                    String sql = "SELECT handle, leetcode_handle FROM users_data WHERE leetcode_url_exists = 1";
                    resultSet = statement.executeQuery(sql);

                    assert resultSet != null;

                    while (resultSet.next()) {
                        String handle = resultSet.getString("handle");
                        String leetcodeHandle = resultSet.getString("leetcode_handle");
                        if (leetcodeHandle != null) {
                            trueLeetcode.add(new User(handle, "leetcode", leetcodeHandle));
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error fetching true Leetcode handles: " + e.getMessage());
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                        if (conn != null) conn.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing resultSet, statement, or connection: " + e.getMessage());
                    }
                }

                scrapeLeetcode(trueLeetcode);
                break;
            case "gfg":
                // Fetch all true gfg handles from the database
                try{
                    conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
                    statement = conn.createStatement();

                    String sql = "SELECT handle, geeksforgeeks_handle FROM users_data WHERE geeksforgeeks_url_exists = 1";
                    resultSet = statement.executeQuery(sql);

                    assert resultSet != null;

                    while (resultSet.next()) {
                        String handle = resultSet.getString("handle");
                        String geeksforgeeksHandle = resultSet.getString("geeksforgeeks_handle");
                        if (geeksforgeeksHandle != null) {
                            trueGeeksforgeeks.add(new User(handle, "geeksforgeeks", geeksforgeeksHandle));
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error fetching true GeeksforGeeks handles: " + e.getMessage());
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                        if (conn != null) conn.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing resultSet, statement, or connection: " + e.getMessage());
                    }
                }

                // create a gfgHandle to user map
                gfgHandleToUserMap = new HashMap<>();
                for (User user : trueGeeksforgeeks) {
                    gfgHandleToUserMap.put(user.getGeeksforgeeksHandle().toLowerCase(), user);
                }

                scrapeGfg(trueGeeksforgeeks, gfgHandleToUserMap);
                break;
            case "gfg_practice":
                // Fetch all true gfg handles from the database
                try{
                    conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
                    statement = conn.createStatement();

                    String sql = "SELECT handle, geeksforgeeks_handle FROM users_data WHERE geeksforgeeks_url_exists = 1";
                    resultSet = statement.executeQuery(sql);

                    assert resultSet != null;

                    while (resultSet.next()) {
                        String handle = resultSet.getString("handle");
                        String geeksforgeeksHandle = resultSet.getString("geeksforgeeks_handle");
                        if (geeksforgeeksHandle != null) {
                            trueGeeksforgeeks.add(new User(handle, "geeksforgeeks", geeksforgeeksHandle));
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error fetching true GeeksforGeeks handles: " + e.getMessage());
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                        if (conn != null) conn.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing resultSet, statement, or connection: " + e.getMessage());
                    }
                }

                // create a gfgHandle to user map
                gfgHandleToUserMap = new HashMap<>();
                for (User user : trueGeeksforgeeks) {
                    gfgHandleToUserMap.put(user.getGeeksforgeeksHandle().toLowerCase(), user);
                }

                scrapeGfgPractice(trueGeeksforgeeks, gfgHandleToUserMap);
                break;
            case "hackerrank":
                // Fetch all true hackerrank handles from the database
                try{
                    conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
                    statement = conn.createStatement();

                    String sql = "SELECT handle, hackerrank_handle FROM users_data WHERE hackerrank_url_exists = 1";
                    resultSet = statement.executeQuery(sql);

                    assert resultSet != null;

                    while (resultSet.next()) {
                        String handle = resultSet.getString("handle");
                        String hackerrankHandle = resultSet.getString("hackerrank_handle");
                        if (hackerrankHandle != null) {
                            trueHackerrank.add(new User(handle, "hackerrank", hackerrankHandle));
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("Error fetching true Hackerrank handles: " + e.getMessage());
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                        if (conn != null) conn.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing resultSet, statement, or connection: " + e.getMessage());
                    }
                }

                // create a hackerrankHandle to user map
                hackerrankHandleToUserMap = new HashMap<>();
                for (User user : trueHackerrank) {
                    hackerrankHandleToUserMap.put(user.getHackerrankHandle().toLowerCase(), user);
                }

                scrapeHackerrank(trueHackerrank, hackerrankHandleToUserMap);
                break;
            case "all":
                // Fetch all true handles from the database
                try {
                    conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
                    statement = conn.createStatement();

                    String sql = "SELECT handle, " +
                            "CASE WHEN codeforces_url_exists = 1 THEN codeforces_handle ELSE NULL END AS codeforces_handle, " +
                            "CASE WHEN leetcode_url_exists = 1 THEN leetcode_handle ELSE NULL END AS leetcode_handle, " +
                            "CASE WHEN geeksforgeeks_url_exists = 1 THEN geeksforgeeks_handle ELSE NULL END AS geeksforgeeks_handle, " +
                            "CASE WHEN codechef_url_exists = 1 THEN codechef_handle ELSE NULL END AS codechef_handle, " +
                            "CASE WHEN hackerrank_url_exists = 1 THEN hackerrank_handle ELSE NULL END AS hackerrank_handle " +
                            "FROM users_data";
                    resultSet = statement.executeQuery(sql);

                    assert resultSet != null;

                    while (resultSet.next()) {
                        String handle = resultSet.getString("handle");
                        String codeforcesHandle = resultSet.getString("codeforces_handle");
                        String leetcodeHandle = resultSet.getString("leetcode_handle");
                        String geeksforgeeksHandle = resultSet.getString("geeksforgeeks_handle");
                        String codechefHandle = resultSet.getString("codechef_handle");
                        String hackerrankHandle = resultSet.getString("hackerrank_handle");

                        if (codeforcesHandle != null) {
                            trueCodeforces.add(new User(handle, "codeforces", codeforcesHandle));
                        }
                        if (leetcodeHandle != null) {
                            trueLeetcode.add(new User(handle, "leetcode", leetcodeHandle));
                        }
                        if (geeksforgeeksHandle != null) {
                            trueGeeksforgeeks.add(new User(handle, "geeksforgeeks", geeksforgeeksHandle));
                        }
                        if ((codechefHandle != null)) {
                            trueCodechef.add(new User(handle, "codechef", codechefHandle));
                        }
                        if (hackerrankHandle != null) {
                            trueHackerrank.add(new User(handle, "hackerrank", hackerrankHandle));
                        }
                    }

                } catch (SQLException e) {
                    System.err.println("Error fetching true handles: " + e.getMessage());
                } finally {
                    try {
                        if (resultSet != null) resultSet.close();
                        if (statement != null) statement.close();
                        if (conn != null) conn.close();
                    } catch (SQLException e) {
                        System.err.println("Error closing resultSet, statement, or connection: " + e.getMessage());
                    }
                }

                // create a gfgHandle to user map
                gfgHandleToUserMap = new HashMap<>();
                for (User user : trueGeeksforgeeks) {
                    gfgHandleToUserMap.put(user.getGeeksforgeeksHandle().toLowerCase(), user);
                }

                // create a hackerrankHandle to user map
                hackerrankHandleToUserMap = new HashMap<>();
                for (User user : trueHackerrank) {
                    hackerrankHandleToUserMap.put(user.getHackerrankHandle().toLowerCase(), user);
                }

                scrapeCodechef(trueCodechef);
                scrapeCodeforces(trueCodeforces);
                scrapeLeetcode(trueLeetcode);
                scrapeGfg(trueGeeksforgeeks, gfgHandleToUserMap);
                scrapeHackerrank(trueHackerrank, hackerrankHandleToUserMap);
                break;
            case "build_leaderboard":
                // Add all usernames to the userMap which can be fetched from the database
                try{
                    conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
                    statement = conn.createStatement();

                    String sql = "SELECT * FROM users_data";
                    resultSet = statement.executeQuery(sql);

                    assert resultSet != null;

                    while (resultSet.next()) {
                        String handle = resultSet.getString("handle");
                        String codeforcesHandle = resultSet.getString("codeforces_handle");
                        String geeksforgeeksHandle = resultSet.getString("geeksforgeeks_handle");
                        String leetcodeHandle = resultSet.getString("leetcode_handle");
                        String codechefHandle = resultSet.getString("codechef_handle");
                        String hackerrankHandle = resultSet.getString("hackerrank_handle");
                        User user = new User(handle, codeforcesHandle, geeksforgeeksHandle, leetcodeHandle, codechefHandle, hackerrankHandle);
                        // Add the user to the userMap
                        userMap.put(handle, user);
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
                buildLeaderboard();
                break;
            case "load_data":
                System.out.println("Completed loading data from CSV to database.");
                System.exit(0);
            default:
                System.err.println("Invalid method specified.");
                System.exit(1);
        }
    }

    /**
     * Use all generated rating files to build the leaderboard
     * Read all the rating files and store the ratings in a map
     */
    private static void buildLeaderboard(){
        // Use all generated rating files to build the leaderboard
        // Read all the rating files and store the ratings in a map

        try {
            BufferedReader reader = new BufferedReader(new FileReader("codeforces_ratings.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                String handle = data[0];
                String codeforcesHandle = data[1];
                int rating = Integer.parseInt(data[2]);
                // if the user is not in the map, add the user
                if (!userMap.containsKey(handle)) {
                    userMap.put(handle, new User(handle, "codeforces", codeforcesHandle));
                }
                User user = userMap.get(handle);
                user.setCodeforcesRating(rating);
            }
        } catch (IOException e) {
            System.err.println("Error reading Codeforces ratings file: " + e.getMessage());
        }
        System.out.println("Codeforces ratings read successfully.");
        try {
            BufferedReader reader = new BufferedReader(new FileReader("gfg_ratings.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                String handle = data[0];
                String gfgHandle = data[1];
                int rating = Integer.parseInt(data[2]);
                // if the user is not in the map, add the user
                if (!userMap.containsKey(handle)) {
                    userMap.put(handle, new User(handle, "geeksforgeeks", gfgHandle));
                }
                User user = userMap.get(handle);
                user.setGeeksforgeeksRating(rating);
            }
        } catch (IOException e) {
            System.err.println("Error reading GFG ratings file: " + e.getMessage());
        }
        System.out.println("GFG ratings read successfully.");
        try {
            BufferedReader reader = new BufferedReader(new FileReader("gfg_practice_ratings.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                String handle = data[0];
                String gfgHandle = data[1];
                int rating = Integer.parseInt(data[2]);
                // if the user is not in the map, add the
                if (!userMap.containsKey(handle)) {
                    userMap.put(handle, new User(handle, "geeksforgeeks", gfgHandle));
                }
                User user = userMap.get(handle);
                user.setgeeksforgeeksPracticeRating(rating);
            }
        } catch (IOException e) {
            System.err.println("Error reading GFG Practice ratings file: " + e.getMessage());
        }
        System.out.println("GFG Practice ratings read successfully.");
        try {
            BufferedReader reader = new BufferedReader(new FileReader("leetcode_ratings.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                String handle = data[0];
                String leetcodeHandle = data[1];
                int rating = Integer.parseInt(data[2]);
                // if the user is not in the map, add the user
                if (!userMap.containsKey(handle)) {
                    userMap.put(handle, new User(handle, "leetcode", leetcodeHandle));
                }
                User user = userMap.get(handle);
                user.setLeetcodeRating(rating);
            }
        } catch (IOException e) {
            System.err.println("Error reading Leetcode ratings file: " + e.getMessage());
        }
        System.out.println("Leetcode ratings read successfully.");
        try {
            BufferedReader reader = new BufferedReader(new FileReader("codechef_ratings.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                String handle = data[0];
                String codechefHandle = data[1];
                int rating = Integer.parseInt(data[2]);
                // if the user is not in the map, add the user
                if (!userMap.containsKey(handle)) {
                    userMap.put(handle, new User(handle, "codechef", codechefHandle));
                }
                User user = userMap.get(handle);
                user.setCodechefRating(rating);
            }
        } catch (IOException e) {
            System.err.println("Error reading Codechef ratings file: " + e.getMessage());
        }
        System.out.println("Codechef ratings read successfully.");
        try {
            BufferedReader reader = new BufferedReader(new FileReader("hackerrank_ratings.txt"));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] data = line.split(",");
                String handle = data[0];
                String hackerrankHandle = data[1];
                int rating = Integer.parseInt(data[2]);
                // if the user is not in the map, add the user
                if (!userMap.containsKey(handle)) {
                    userMap.put(handle, new User(handle, "hackerrank", hackerrankHandle));
                }
                User user = userMap.get(handle);
                user.setHackerrankRating(rating);
            }
        } catch (IOException e) {
            System.err.println("Error reading Hackerrank ratings file: " + e.getMessage());
        }
        System.out.println("Hackerrank ratings read successfully.");

        // Upload userMap as a leaderboard to the database
        uploadLeaderboardToDatabase(userMap);

        // use sql to find max of each rating
        // use sql to find percentile of each user

        String dbName = "leaderboard";
        Connection conn = null;
        Statement statement = null;
        ResultSet resultSet = null;

        int maxCodeforcesRating = 0;
        int maxGeeksforgeeksRating = 0;
        int maxGeeksforgeeksPracticeRating = 0;
        int maxLeetcodeRating = 0;
        int maxCodechefRating = 0;
        int maxHackerrankRating = 0;

        String sql = "SELECT MAX(codeforces_rating) AS max_codeforces_rating, MAX(geeksforgeeks_rating) AS max_geeksforgeeks_rating, MAX(geeksforgeeks_practice_rating) AS max_geeksforgeeks_practice_rating, MAX(leetcode_rating) AS max_leetcode_rating, MAX(codechef_rating) AS max_codechef_rating, MAX(hackerrank_rating) AS max_hackerrank_rating FROM leaderboard";
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
            statement = conn.createStatement();
            resultSet = statement.executeQuery(sql);
            assert resultSet != null;
            maxCodeforcesRating = resultSet.getInt("max_codeforces_rating");
            maxGeeksforgeeksRating = resultSet.getInt("max_geeksforgeeks_rating");
            maxGeeksforgeeksPracticeRating = resultSet.getInt("max_geeksforgeeks_practice_rating");
            maxLeetcodeRating = resultSet.getInt("max_leetcode_rating");
            maxCodechefRating = resultSet.getInt("max_codechef_rating");
            maxHackerrankRating = resultSet.getInt("max_hackerrank_rating");
        } catch (SQLException e) {
            System.err.println("Error fetching max ratings: " + e.getMessage());
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (statement != null) statement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                System.err.println("Error closing resultSet, statement, or connection: " + e.getMessage());
            }
        }

        // update the userMap with percentile
        for (User user : userMap.values()) {
            double cf = (double) user.getCodeforcesRating() / maxCodeforcesRating * 100;
            double gfgs = (double) user.getGeeksforgeeksRating() / maxGeeksforgeeksRating * 100;
            if (user.getgeeksforgeeksPracticeRating() == null) {
                user.setgeeksforgeeksPracticeRating(0);
            }
            double gfgp = (double) user.getgeeksforgeeksPracticeRating() / maxGeeksforgeeksPracticeRating * 100;
            double lc = (double) user.getLeetcodeRating() / maxLeetcodeRating * 100;
            double cc = (double) user.getCodechefRating() / maxCodechefRating * 100;
            double hr = (double) user.getHackerrankRating() / maxHackerrankRating * 100;
            double percentile = ( cf * 0.3 + gfgs*0.3  + gfgp*0.1 + lc*0.1 + cc*0.1 + hr*0.1 );

            user.setPercentile(percentile);
        }

        // Push the updated userMap to the database
        uploadLeaderboardToDatabase(userMap);

        // Fetch the leaderboard from the database with decreasing percentile
        sql = "SELECT * FROM leaderboard ORDER BY percentile DESC";
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
            statement = conn.createStatement();
            resultSet = statement.executeQuery(sql);
            assert resultSet != null;

            // Create a new workbook
            XSSFSheet sheet;
            XSSFWorkbook workbook = new XSSFWorkbook();
            sheet = workbook.createSheet("Leaderboard");
            // Create a header row with the column names
            Row headerRow = sheet.createRow(0);

            // Create bold font with size 18 for column headers
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldFont.setFontHeightInPoints((short) 20);

            Font boldFont2 = workbook.createFont();
            boldFont2.setBold(true);
            boldFont2.setFontHeightInPoints((short) 14);

            // Create bold centered cell style with 14 font size for normal cells
            CellStyle boldCenteredCellStyle = workbook.createCellStyle();
            boldCenteredCellStyle.setAlignment(HorizontalAlignment.CENTER);
            boldCenteredCellStyle.setFont(boldFont);
            boldCenteredCellStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE1.getIndex());
            boldCenteredCellStyle.setBorderBottom(BorderStyle.THICK);
            boldCenteredCellStyle.setBorderTop(BorderStyle.THICK);
            boldCenteredCellStyle.setBorderLeft(BorderStyle.THICK);
            boldCenteredCellStyle.setBorderRight(BorderStyle.THICK);
            boldCenteredCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            // Create bold cell style with 14 font size for normal cells
            CellStyle boldCellStyle = workbook.createCellStyle();
            boldCellStyle.setAlignment(HorizontalAlignment.CENTER);
            boldCellStyle.setFont(boldFont2);
            boldCellStyle.setFillForegroundColor(IndexedColors.TURQUOISE.getIndex());
            boldCellStyle.setBorderBottom(BorderStyle.THICK);
            boldCellStyle.setBorderTop(BorderStyle.THICK);
            boldCellStyle.setBorderLeft(BorderStyle.THICK);
            boldCellStyle.setBorderRight(BorderStyle.THICK);
            boldCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            String[] columns = {"Rank", "Handle", "Codeforces_Handle", "Codeforces_Rating", "Geeksforgeeks_Handle", "Geeksforgeeks_Rating", "Geeksforgeeks_Practice_Rating", "Leetcode_Handle", "Leetcode_Rating", "Codechef_Handle", "Codechef_Rating", "Hackerrank_Handle", "Hackerrank_Rating", "Percentile"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellStyle(boldCenteredCellStyle);
                cell.setCellValue(columns[i]);
            }

            int rank = 1;
            while (resultSet.next()) {
                String handle = resultSet.getString("handle");
                int codeforcesRating = resultSet.getInt("codeforces_rating");
                int codechefRating = resultSet.getInt("codechef_rating");
                int leetcodeRating = resultSet.getInt("leetcode_rating");
                int geeksforgeeksRating = resultSet.getInt("geeksforgeeks_rating");
                int geeksforgeeksPracticeRating = resultSet.getInt("geeksforgeeks_practice_rating");
                int hackerrankRating = resultSet.getInt("hackerrank_rating");
                DecimalFormat df = new DecimalFormat("#.##");
                double percentileD = Double.parseDouble(df.format(resultSet.getDouble("percentile")));
                // convert to string and add % at the end
                String percentile = String.valueOf(percentileD);
                String codeforcesHandle = userMap.get(handle).getCodeforcesHandle();
                String geeksforgeeksHandle = userMap.get(handle).getGeeksforgeeksHandle();
                String leetcodeHandle = userMap.get(handle).getLeetcodeHandle();
                String codechefHandle = userMap.get(handle).getCodechefHandle();
                String hackerrankHandle = userMap.get(handle).getHackerrankHandle();
                System.out.println(rank + ". " + handle + " - " + codeforcesRating + " - " + geeksforgeeksRating + " - " + geeksforgeeksPracticeRating + " - " + leetcodeRating + " - " + codechefRating + " - " + hackerrankRating + " - " + percentile);

                // Append the data to the sheet
                Row row = sheet.createRow(rank);
                // add all the data to the row at once
                Object[] data = {rank, handle, codeforcesHandle, codeforcesRating, geeksforgeeksHandle, geeksforgeeksRating, geeksforgeeksPracticeRating, leetcodeHandle, leetcodeRating, codechefHandle, codechefRating, hackerrankHandle, hackerrankRating, percentile};
                for (int i = 0; i < data.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellStyle(boldCellStyle);
                    if (data[i] instanceof String) {
                        cell.setCellValue((String) data[i]);
                    } else if (data[i] instanceof Integer) {
                        cell.setCellValue((Integer) data[i]);
                    } else if (data[i] instanceof Double) {
                        cell.setCellValue((Double) data[i]);
                    }
                }
                rank++;
            }

            // Set all cells to auto-size
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }


            // Check if the directory exists, if not create it
            File directory = new File("Leaderboards");
            if (!directory.exists()) {
                boolean dirCreated = directory.mkdir();
                if (!dirCreated) {
                    System.err.println("Error creating directory: Leaderboards");
                }
            }

            // Write the workbook to a file
            try (FileOutputStream fileOut = new FileOutputStream("Leaderboards//CurrentCMRITLeaderboard2026.xlsx")) {
                workbook.write(fileOut);
            } catch (IOException e) {
                System.err.println("Error writing leaderboard to file: " + e.getMessage());
            }
        } catch (SQLException e) {
            System.err.println("Error fetching leaderboard: " + e.getMessage());
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (statement != null) statement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                System.err.println("Error closing resultSet, statement, or connection: " + e.getMessage());
            }
        }
    }

    /**
     * Uploads the leaderboard to the database.
     *
     * @param  userMap   a map of user handles to user objects
     */
    private static void uploadLeaderboardToDatabase(Map<String, User> userMap) {
        String dbName = "leaderboard";
        Connection conn = null;
        PreparedStatement preparedStatement = null;

        try {
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);

            // if the table exists, drop it
            String sql = "DROP TABLE IF EXISTS leaderboard";
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.executeUpdate();

            sql = "CREATE TABLE IF NOT EXISTS leaderboard (" +
                    "handle TEXT PRIMARY KEY," +
                    "codeforces_rating INTEGER," +
                    "codechef_rating INTEGER," +
                    "leetcode_rating INTEGER," +
                    "geeksforgeeks_rating INTEGER," +
                    "geeksforgeeks_practice_rating INTEGER," +
                    "hackerrank_rating INTEGER, " +
                    "percentile REAL)";

            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.executeUpdate();

            sql = "REPLACE INTO leaderboard " +
                    "(handle, codeforces_rating, codechef_rating, leetcode_rating, geeksforgeeks_rating, geeksforgeeks_practice_rating, hackerrank_rating, percentile) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

            preparedStatement = conn.prepareStatement(sql);

            for (User user : userMap.values()) {
                preparedStatement.setString(1, user.getHandle());
                preparedStatement.setInt(2, user.getCodeforcesRating());
                preparedStatement.setInt(3, user.getCodechefRating());
                preparedStatement.setInt(4, user.getLeetcodeRating());
                preparedStatement.setInt(5, user.getGeeksforgeeksRating());
                if (user.getgeeksforgeeksPracticeRating() == null) {
                    preparedStatement.setInt(6, 0);
                } else {
                    preparedStatement.setInt(6, user.getgeeksforgeeksPracticeRating());
                }
                preparedStatement.setInt(7, user.getHackerrankRating());
                if (user.getPercentile() == null) {
                    preparedStatement.setNull(8, Types.REAL);
                } else {
                    preparedStatement.setDouble(8, user.getPercentile());
                }
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error uploading leaderboard to database: " + e.getMessage());
        } finally {
            try {
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                System.err.println("Error closing preparedStatement or connection: " + e.getMessage());
            }
        }
    }

        private static void scrapeCodechef(ArrayList <User> resultSet) {
        // Scraper logic for Codechef

        System.out.println("Codechef scraping in progress...");

        String url;
        URI websiteUrl;
        URLConnection connection;
        HttpURLConnection o;
        InputStream inputStream;

        // create or clear the file for writing
        File file = new File("codechef_ratings.txt");
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(""); // Clearing the file
            writer.close();
        } catch (IOException e) {
            System.err.println("Error clearing file: " + e.getMessage());
        }

        int size = resultSet.size();
        int i = 1;

        for (User user : resultSet) {
            String handle = user.getHandle();
            String codechefHandle = user.getCodechefHandle();

            // remove any spaces from the handle
            codechefHandle = codechefHandle.replace(" ", "");

            System.out.println("(" + i + "/" + size + ") Scraping Codechef for " + handle + " (Codechef Handle: " + codechefHandle + ")");
            i++;

            try {
                url = CODECHEF_URL + codechefHandle;
                websiteUrl = new URI(url);
                connection = websiteUrl.toURL().openConnection();
                o = (HttpURLConnection) connection;
                o.setRequestMethod("GET");
                if(o.getResponseCode() == 500 ) { Thread.sleep(30000); continue; }
                else if (o.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND || o.getResponseCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
                    throw new RuntimeException();
                }
                inputStream = o.getInputStream();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                    StringBuilder jsonContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        jsonContent.append(line);
                    }
                    JSONObject jsonObject = new JSONObject(jsonContent.toString());
                    int codechefRating = 0;
                    try {
                        Thread.sleep(30000);
                        try {
                        codechefRating = jsonObject.getInt("currentRating"); 
                        } catch(Exception e) {
                            //Thread.sleep(30000);
                            codechefRating = jsonObject.getInt("currentRating"); 
                        }

                        // update the user object with the codechef rating
                        user.setCodechefRating(codechefRating);

                        System.out.println("Codechef rating for " + codechefHandle + " is: " + codechefRating);
                        // Write to a text file
                        FileWriter writer = new FileWriter("codechef_ratings.txt", true);
                        writer.write(handle + "," + codechefHandle + "," + codechefRating + "\n");
                        writer.close();
                    } catch (JSONException e) {
                        System.err.println("Error fetching codechef rating for " + codechefHandle + ": " + e.getMessage());
                    }
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

        System.out.println("Codechef scraping completed.");
        System.out.println("========================================");

    }


    private static final int MAX_HANDLES_PER_REQUEST = 380;

    /**
     * Scrapes Codeforces ratings for a list of users and writes the results to a text file.
     *
     * @param  resultSet  list of User objects to scrape ratings for
     * @throws CustomScrapingException  if unable to fetch Codeforces rating after 10 retries
     */
    private static void scrapeCodeforces(ArrayList<User> resultSet) throws CustomScrapingException {
        System.out.println("Codeforces scraping in progress...");

        File file = new File("codeforces_ratings.txt");
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(""); // Clearing the file
            writer.close();
        } catch (IOException e) {
            System.err.println("Error clearing file: " + e.getMessage());
        }

        List<List<User>> userChunks = splitUsersIntoChunks(resultSet);

        int counter = 1;
        int totalUsers = resultSet.size();

        for (List<User> users : userChunks) {
            int retryCount = 0;
            while (retryCount < 10) {
                try {
                    String codeforcesHandles = users.stream()
                            .map(User::getCodeforcesHandle)
                            .map(handle -> handle.replaceAll(" ", ""))
                            .collect(Collectors.joining(";"));

                    String url = CODEFORCES_URL + codeforcesHandles;
                    url = url.replaceAll("\t", "");
                    System.out.println("Codeforces URL: " + url);

                    long currentTime = System.currentTimeMillis() / 1000;
                    String rand = generateRandomString(6);
                    String apiSig = generateApiSig(rand, "user.info", codeforcesHandles, currentTime, API_SECRET);

                    url += "&apiKey=" + API_KEY + "&time=" + currentTime + "&apiSig=" + rand + apiSig;

                    Document doc = Jsoup.connect(url).ignoreContentType(true).method(org.jsoup.Connection.Method.GET).execute().parse();
                    String jsonContent = doc.body().text();

                    JSONObject jsonObject = new JSONObject(jsonContent);
                    JSONArray array = jsonObject.getJSONArray("result");

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = array.getJSONObject(i);
                        String handle = obj.getString("handle");
                        int rating = obj.optInt("rating", 0);
                        System.out.println("(" + counter + "/" + totalUsers + ") " + "Codeforces rating for " + handle + " is: " + rating);

                        User user = users.stream()
                                .filter(u -> u.getCodeforcesHandle().replace(" ", "").equalsIgnoreCase(handle.replace(" ", "")))
                                .findFirst()
                                .orElse(null);
                        if (user != null) {
                            user.setCodeforcesRating(rating);
                            FileWriter writer = new FileWriter("codeforces_ratings.txt", true);
                            writer.write(user.getHandle() + "," + handle + "," + rating + "\n");
                            writer.close();
                        }

                        counter++;
                    }
                    break;
                } catch (IOException e) {
                    retryCount++;
                    System.err.println("Error fetching Codeforces rating. Retrying attempt " + retryCount + ": " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Error processing request: " + e.getMessage());
                }
            }
            if (retryCount == 10) {
                throw new CustomScrapingException("Failed to fetch Codeforces rating after 10 retries.");
            }
        }
        System.out.println("Codeforces scraping completed.");
        System.out.println("========================================");
    }

    private static String generateRandomString(int length) {
        String chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder sb = new StringBuilder(length);
        Random random = new Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String generateApiSig(String rand, String methodName, String handles, long time, String secret) throws NoSuchAlgorithmException {
        String parameters = "apiKey=" + API_KEY + "&handles=" + handles + "&time=" + time;
        String toHash = rand + "/" + methodName + "?" + parameters + "#" + secret;

        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[] hashBytes = digest.digest(toHash.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    // Custom exception for scraping
    private static class CustomScrapingException extends Exception {
        public CustomScrapingException(String message) {
            super(message);
        }
    }


    /**
     * Splits the list of users into chunks of a specified size.
     *
     * @param  resultSet  the list of users to be split
     * @return            a list of chunks, each containing a sublist of users
     */
    private static List<List<User>> splitUsersIntoChunks(ArrayList<User> resultSet) {
        // Initialize an empty list to store the chunks
        List<List<User>> chunks = new ArrayList<>();

        // Get the total number of users
        int size = resultSet.size();

        // Iterate through the list of users and split them into chunks of size MAX_HANDLES_PER_REQUEST
        for (int i = 0; i < size; i += MAX_HANDLES_PER_REQUEST) {
            // Calculate the end index of the chunk
            int end = Math.min(size, i + MAX_HANDLES_PER_REQUEST);

            // Create a sublist of users and add it to the chunks list
            chunks.add(new ArrayList<>(resultSet.subList(i, end)));
        }

        // Return the list of chunks
        return chunks;
    }

    private static final int MAX_REQUESTS_PER_SECOND = 2;
    private static final long REQUEST_INTERVAL_MILLIS = 1000 / MAX_REQUESTS_PER_SECOND;

    private static long lastRequestTime = 0;

    /**
     * Scrapes Leetcode ratings for a list of users and updates their user objects with the ratings.
     *
     * @param  resultSet  ArrayList of User objects to scrape ratings for
     */
    private static void scrapeLeetcode(ArrayList<User> resultSet) {
        // Scraper logic for Leetcode
        System.out.println("Leetcode scraping in progress...");

        // Create or clear the file for writing
        File file = new File("leetcode_ratings.txt");
        try {
            // Clearing the file
            FileWriter writer = new FileWriter(file);
            writer.write("");
            writer.close();
        } catch (IOException e) {
            // Error handling
            System.err.println("Error clearing file: " + e.getMessage());
        }

        int counter = 1;
        int size = resultSet.size();

        for (User user : resultSet) {

            // Rate limiting
            long currentTime = System.currentTimeMillis();
            long timeElapsedSinceLastRequest = currentTime - lastRequestTime;
            if (timeElapsedSinceLastRequest < REQUEST_INTERVAL_MILLIS) {
                try {
                    // Sleep for rate limiting
                    Thread.sleep(REQUEST_INTERVAL_MILLIS - timeElapsedSinceLastRequest);
                } catch (InterruptedException e) {
                    // Interrupted exception handling
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while sleeping for rate limiting", e);
                }
            }
            lastRequestTime = System.currentTimeMillis();

            // Construct URL for API request
            String handle = user.getHandle();
            String leetcodeHandle = user.getLeetcodeHandle();
            String encodedLeetcodeHandle = URLEncoder.encode(leetcodeHandle, StandardCharsets.UTF_8);

            String url = LEETCODE_URL + URLEncoder.encode("query{userContestRanking(username:\"" + encodedLeetcodeHandle + "\"){rating}}", StandardCharsets.UTF_8);

            try {
                System.out.println("URL: " + url + "\n====================================");
                URI websiteUrl = new URI(url);
                URLConnection connection = websiteUrl.toURL().openConnection();
                HttpURLConnection o = (HttpURLConnection) connection;

                // Set request method
                o.setRequestMethod("GET");

                // Add provided headers
                o.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
                o.setRequestProperty("Accept-Language", "en-US,en;q=0.9,en-IN;q=0.8,en-GB;q=0.7");
                o.setRequestProperty("Cache-Control", "max-age=0");
                o.setRequestProperty("Cookie", "gr_user_id=e0bc031e-2749-493a-b6e8-33f33fe2e6d0; __stripe_mid=f4e2affa-c7a2-4359-b07d-9ed75a0d2ff74f1623; 87b5a3c3f1a55520_gr_last_sent_cs1=21r01a67e6; csrftoken=wNsw5l1EIsHhzc4TQX0Q6DDw3C1gkGN83o9229f3ULBBccvahHyRldbeLWNSqvNu; 87b5a3c3f1a55520_gr_cs1=21r01a67e6; _ga_CDRWKZTDEX=GS1.1.1706187230.19.1.1706187296.60.0.0; _ga=GA1.1.1231149882.1693316228; cf_clearance=wfRNb1td_mDRegZDij_p0TPFyRXf6hvQctV6X5xzEK4-1708415178-1.0-AQG+8uiVC3mUEYY8sC4cibGLgiW/1S94eORpvLmbnddQ1Pl4pcDHQfbFjwxeTOVKicAOKjPO6d2TfTsIA3EO9uY=; LEETCODE_SESSION=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJfYXV0aF91c2VyX2lkIjoiNzk3NzczMiIsIl9hdXRoX3VzZXJfYmFja2VuZCI6ImFsbGF1dGguYWNjb3VudC5hdXRoX2JhY2tlbmRzLkF1dGhlbnRpY2F0aW9uQmFja2VuZCIsIl9hdXRoX3VzZXJfaGFzaCI6IjM3ZGEyNzM3OTEzMWZkNGVkNzNiMjEzOTc5MmRiYWU3YmEzNzU0MjVmYTE1ZDQ0NmFhNGQ4MDdhYjQ4ZmZlZWYiLCJpZCI6Nzk3NzczMiwiZW1haWwiOiIyMXIwMWE2N2U2QGNtcml0b25saW5lLmFjLmluIiwidXNlcm5hbWUiOiIyMXIwMWE2N2U2IiwidXNlcl9zbHVnIjoiMjFyMDFhNjdlNiIsImF2YXRhciI6Imh0dHBzOi8vYXNzZXRzLmxlZXRjb2RlLmNvbS91c2Vycy9hdmF0YXJzL2F2YXRhcl8xNjY4MTg2MzEwLnBuZyIsInJlZnJlc2hlZF9hdCI6MTcwOTgyNTA2NCwiaXAiOiIxODMuODMuMjI1LjI0MyIsImlkZW50aXR5IjoiY2U1OGE1MjYwYmE0ZmE5Y2RlZGY0ODg2ZTRjZjQ1YzYiLCJzZXNzaW9uX2lkIjo1NDI3OTE4NH0.6zLWAStoqoYNd_1nM67_UohxjqkvSZkNIAmIPupV-Vs; INGRESSCOOKIE=25e33f9f5bb4b70334074b2a6707350c|8e0876c7c1464cc0ac96bc2edceabd27; __cf_bm=ca1lJgDedKfTUr8uvXaHThqTO7xIm2D6NFB71xAYeIs-1709827901-1.0.1.1-l8Kl3r1Eq6d3zxmKkKSOM9_lWIwYugvVncpeWebvf43gyY5aJiN6bkML7cOW_LbY7sx0LB2BAsaSQlpx4HirBw");
                o.setRequestProperty("Sec-Ch-Ua", "\"Chromium\";v=\"122\", \"Not(A:Brand\";v=\"24\", \"Microsoft Edge\";v=\"122\"");
                o.setRequestProperty("Sec-Ch-Ua-Arch", "\"x86\"");
                o.setRequestProperty("Sec-Ch-Ua-Bitness", "\"64\"");
                o.setRequestProperty("Sec-Ch-Ua-Full-Version", "\"122.0.2365.66\"");
                o.setRequestProperty("Sec-Ch-Ua-Full-Version-List", "\"Chromium\";v=\"122.0.6261.95\", \"Not(A:Brand\";v=\"24.0.0.0\", \"Microsoft Edge\";v=\"122.0.2365.66\"");
                o.setRequestProperty("Sec-Ch-Ua-Mobile", "?0");
                o.setRequestProperty("Sec-Ch-Ua-Model", "\"\"");
                o.setRequestProperty("Sec-Ch-Ua-Platform", "\"Windows\"");
                o.setRequestProperty("Sec-Ch-Ua-Platform-Version", "\"15.0.0\"");
                o.setRequestProperty("Sec-Fetch-Dest", "document");
                o.setRequestProperty("Sec-Fetch-Mode", "navigate");
                o.setRequestProperty("Sec-Fetch-Site", "none");
                o.setRequestProperty("Sec-Fetch-User", "?1");
                o.setRequestProperty("Upgrade-Insecure-Requests", "1");
                o.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0");

                if (o.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND || o.getResponseCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
                    // Handle specific response codes
                    if (o.getResponseCode() == 524) {
                        // Wait and retry for response code 524
                        Thread.sleep(30000);
                        o.setRequestMethod("GET");
                        continue;
                    }
                    throw new RuntimeException();
                }

                // Read response
                try (InputStream inputStream = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                    System.out.println("input stream: \n" + inputStream + "\n==================================");
                    StringBuilder jsonContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        jsonContent.append(line);
                    }

                    System.out.println("Json Content: \n" + jsonContent + "\n==================================");

                    // Parse JSON response
                    int rating = getRating(jsonContent);

                    // Update the user object with the leetcode rating
                    user.setLeetcodeRating(rating);

                    // Print the rating information
                    System.out.println("(" + counter + "/" + size + ") " + "Leetcode rating for " + handle + " with leetcode handle " + leetcodeHandle + " is: " + rating);

                    // Write to a text file
                    FileWriter writer = new FileWriter("leetcode_ratings.txt", true);
                    writer.write(user.getHandle() + "," + leetcodeHandle + "," + rating + "\n");
                    writer.close();

                    counter++;
                } catch (JSONException e) {
                    // Error handling for JSON parsing
                    System.err.println("Error fetching leetcode rating for " + handle + " with leetcode handle " + leetcodeHandle + ": " + e.getMessage());
                } catch (IOException e) {
                    // Error handling for IO operations
                    throw new RuntimeException(e);
                }
            } catch (URISyntaxException | IOException | InterruptedException e) {
                // General exception handling
                throw new RuntimeException(e);
            }
        }

        System.out.println("Leetcode scraping completed.");
        System.out.println("========================================");
    }

    private static int getRating(StringBuilder jsonContent) throws JSONException {
        JSONObject jsonObject = new JSONObject(jsonContent.toString());
        JSONObject data = jsonObject.optJSONObject("data");
        JSONObject userContestRanking = data.optJSONObject("userContestRanking");

        int rating = 0; // Default rating is 0

        if (userContestRanking != null) {
            double rawRating = userContestRanking.optDouble("rating", Double.NaN);
            if (!Double.isNaN(rawRating)) {
                // Convert rating to int if it's not NaN
                rating = (int) rawRating;
            }
        }
        return rating;
    }

    /**
     * Scraper logic for GeeksforGeeks
     *
     * @param  trueGfg          list of true GFG users
     * @param  gfgHandleToUserMap  map of GFG handle to User object
     */
    private static void scrapeGfg(ArrayList<User> trueGfg, Map<String, User> gfgHandleToUserMap){
        // Scraper logic for GeeksforGeeks

        System.out.println("GeeksforGeeks scraping in progress...");

        // Essential variables
        String url;
        int counter = 1;

        // Create or clear the file for writing
        File file = new File("gfg_ratings.txt");
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(""); // Clearing the file
            writer.close();
        } catch (IOException e) {
            System.err.println("Error clearing file: " + e.getMessage());
        }

        // Overall weekly leaderboard scraping

        for(int j=1;j<=10000;j++) {
            try {
                url = GFG_WEEKLY_CONTEST_URL + j;

                System.out.println("Page: " + j);

                // Fetch JSON data from a URL (or you can read from a file)
                Document doc = Jsoup.connect(url).ignoreContentType(true).get();
                String json = doc.body().text();

                // Parse JSON using Gson
                Gson gson = new Gson();
                DataModel gfguserData = gson.fromJson(json, DataModel.class);

                // Access parsed data

                boolean foundZero = false;

                for (Result gfgUser : gfguserData.results) {
                    String gfgHandle = gfgUser.user_handle;
                    User user = gfgHandleToUserMap.get(gfgHandle.toLowerCase());
                    if (gfgUser.user_score == 0) {
                        foundZero = true;
                        break;
                    }
                    if (user != null) {
                        user.setGeeksforgeeksRating((int)gfgUser.user_score);
                        System.out.println("(" + counter + "/" + trueGfg.size() + ") " + "GFG weekly contest rating for " + user.getHandle() + " with GFG handle " + gfgHandle + " is: " + (int)gfgUser.user_score);
                        // Write to a text file
                        FileWriter writer = new FileWriter("gfg_ratings.txt", true);
                        writer.write(user.getHandle() + "," + gfgHandle + "," + (int)gfgUser.user_score + "\n");
                        writer.close();
                        counter++;
                    }
                }

                if (foundZero) {
                    break;
                }
            } catch (IOException e) {
                System.err.println("Error fetching GFG Practice rating: " + e.getMessage());
            }
        }

        // set all unset ratings to 0
        for (User user : trueGfg) {
            if (user.getGeeksforgeeksRating() == null) {
                user.setGeeksforgeeksRating(0);
                System.out.println("(" + counter + "/" + trueGfg.size() + ") " + "GFG overall rating for " + user.getHandle() + " with GFG handle " + user.getGeeksforgeeksHandle() + " is: " + 0);

                try{
                    FileWriter writer = new FileWriter("gfg_ratings.txt", true);
                    writer.write(user.getHandle() + "," + user.getGeeksforgeeksHandle() + "," + 0 + "\n");
                    writer.close();
                    counter++;
                } catch (IOException e) {
                    System.err.println("Error fetching GFG contest rating: " + e.getMessage());
                }
            }
        }

        System.out.println("GFG overall scraping completed.");
        System.out.println("========================================");

    }

    /**
     * Scrapes GeeksforGeeks practice ratings for users and updates their ratings in the provided data structures
     *
     * @param  trueGfg           ArrayList of User objects to update practice ratings for
     * @param  gfgHandleToUserMap   Map of String to User for quick access to user data
     */
    private static void scrapeGfgPractice(ArrayList<User> trueGfg, Map<String, User> gfgHandleToUserMap){
        // Display progress message
        System.out.println("GeeksforGeeks scraping in progress...");

        // Clear the file for writing
        File file = new File("gfg_practice_ratings.txt");
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(""); // Clearing the file
            writer.close();
        } catch (IOException e) {
            System.err.println("Error clearing file: " + e.getMessage());
        }

        System.out.println("GFG practice scraping in progress...");

        int counter = 1;

        // Overall Practice score scraping
        for(int j=1;j<=1;j++) {
            try {
                // Construct URL for practice scores
                String url = GFG_PRACTICE_URL + j ;

                System.out.println("Page: " + j);

                // Check if URL exists else break
                URI uri = new URI(url);
                HttpURLConnection exists = (HttpURLConnection) uri.toURL().openConnection();
                exists.setRequestMethod("GET");
                if (exists.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND || exists.getResponseCode() == HttpURLConnection.HTTP_BAD_REQUEST) {
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                // Fetch JSON data from the URL
                Document doc = Jsoup.connect(url).ignoreContentType(true).get();
                String json = doc.body().text();

                // Parse JSON using Gson
                Gson gson = new Gson();
                GeeksforgeeksStatsResponse gfguserData = gson.fromJson(json, GeeksforgeeksStatsResponse.class);

                // Access parsed data and update user ratings
                for (GeeksforgeeksStatsResult gfgUser : gfguserData.getResults()) {
                    String gfgHandle = gfgUser.getHandle();
                    User user = gfgHandleToUserMap.get(gfgHandle.toLowerCase());
                    if (user != null) {
                        user.setgeeksforgeeksPracticeRating(gfgUser.getCodingScore());
                        System.out.println("(" + counter + "/" + trueGfg.size() + ") " + "GFG practice contest rating for " + user.getHandle() + " with GFG handle " + gfgHandle + " is: " + gfgUser.getCodingScore());
                        // Write to a text file
                        FileWriter writer = new FileWriter("gfg_practice_ratings.txt", true);
                        writer.write(user.getHandle() + "," + gfgHandle + "," + gfgUser.getCodingScore() + "\n");
                        writer.close();
                        counter++;
                    }
                }
            } catch (IOException e) {
                System.err.println("Error fetching GFG Practice rating: " + e.getMessage());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("GFG practice scraping from institute page completed.");

        // Set up Firefox Options for headless scraping
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("-headless");

        // Initialize Firefox driver and navigate to the website
        WebDriver driver = new FirefoxDriver(options);
        driver.get("https://auth.geeksforgeeks.org/");

        try {
            // Find username and password fields and enter credentials
            WebElement username = driver.findElement(By.id("luser"));
            WebElement password = driver.findElement(By.id("password"));
            String gfgUsername = System.getenv("GFG_USERNAME");
            String gfgPassword = System.getenv("GFG_PASSWORD");
            username.sendKeys(gfgUsername);
            password.sendKeys(gfgPassword);

            // Click on the sign-in button
            WebElement signInButton = driver.findElement(By.className("signin-button"));
            signInButton.click();

            // Add a delay for demonstration purposes
            Thread.sleep(5000);

            // Iterate over users to fetch practice ratings if not already available
            for (User user : trueGfg) {
                if (user.getgeeksforgeeksPracticeRating() == null) {
                    // Open user profile and get the rating
                    String gfgHandle = user.getGeeksforgeeksHandle();
                    System.out.println("Practice rating not found for " + user.getHandle() + " with GFG handle " + gfgHandle + ". Fetching from profile...");
                    driver.get(GFG_URL + gfgHandle);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    // Fetch and parse practice rating
                    try {
                        WebElement scoreCardValue = driver.findElement(By.xpath("//span[contains(text(), 'Overall Coding Score')]/following-sibling::br/following-sibling::span"));
                        int gfgRating = 0;
                        try {
                            gfgRating = Integer.parseInt(scoreCardValue.getText());
                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing GFG practice contest rating for " + gfgHandle + ": " + e.getMessage());
                        }
                        user.setgeeksforgeeksPracticeRating(gfgRating);

                        System.out.println("(" + counter + "/" + trueGfg.size() + ") " + "GFG practice contest rating for " + user.getHandle() + " with GFG handle " + user.getGeeksforgeeksHandle() + " is: " + gfgRating);
                        FileWriter writer = new FileWriter("gfg_practice_ratings.txt", true);
                        writer.write(user.getHandle() + "," + gfgHandle + "," + gfgRating + "\n");
                        writer.close();
                        counter++;
                    } catch (NoSuchElementException | IOException e) {
                        System.err.println("Error fetching GFG practice contest rating for " + gfgHandle + ": " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        } finally {
            // Close the browser
            driver.quit();
        }
    }

    static class DataModel {
        int count;
        Result[] results;
        boolean consider_for_geek_bits;
    }

    static class Result {
        int user_id;
        String user_handle;
        double user_score;
        int user_rank;
    }

    static class GeeksforgeeksStatsResponse {

        @SerializedName("page_size")
        private int pageSize;
        private int count;
        private String next;
        private String previous;
        private List<GeeksforgeeksStatsResult> results;

        // Getters and setters
        public List<GeeksforgeeksStatsResult> getResults() {return results;}
    }

    static class GeeksforgeeksStatsResult {

        @SerializedName("user_id")
        private int userId;
        private String handle;
        @SerializedName("coding_score")
        private int codingScore;
        @SerializedName("total_problems_solved")
        private int totalProblemsSolved;
        @SerializedName("potd_longest_streak")
        private Integer potdLongestStreak;

        // Getters and setters
        public String getHandle() {return handle;}
        public int getCodingScore() {return codingScore;}
    }

    /**
     * Scrapes and updates the Hackerrank ratings for the given users
     *
     * @param trueHackerrank          List of User objects with Hackerrank accounts
     * @param hackerrankHandleToUserMap   Map of Hackerrank handles to User objects
     */
    private static void scrapeHackerrank(ArrayList<User> trueHackerrank, Map<String, User> hackerrankHandleToUserMap) {
        // Scraper logic for Hackerrank
        System.out.println("Hackerrank scraping in progress...");

        // create or clear the file for writing
        File file = new File("hackerrank_ratings.txt");
        try {
            FileWriter writer = new FileWriter(file);
            writer.write(""); // Clearing the file
            writer.close();
        } catch (IOException e) {
            System.err.println("Error clearing file: " + e.getMessage());
        }

        try {
            // Iterate over search tokens
            for (String trackerName : SEARCH_TOKENS) {
                if (trackerName.equals("null")) break;
                System.out.println(trackerName);
                // Iterate over leaderboard page offsets
                for (int j = 0; j < 10000; j += 100) {
                    try {
                        // Construct the leaderboard URL
                        String url = "https://www.hackerrank.com/rest/contests/" + trackerName + "/leaderboard?offset=" + j + "&limit=100";
                        Document doc = Jsoup.connect(url).ignoreContentType(true).get();
                        Element body = doc.body();
                        if (body.text().contains("INVALID URL")) {
                            throw new ArithmeticException("INVALID URL : " + trackerName);
                        }
                        String jsonContent = body.text();
                        Leaderboard leaderboard = new Gson().fromJson(jsonContent, Leaderboard.class);
                        List<LeaderboardModel> models = leaderboard.models;
                        if (models.isEmpty()) break;
                        // Iterate over leaderboard models
                        for (LeaderboardModel model : models) {
                            String userHandle = model.hacker.toLowerCase();

                            // Find user handle in the map
                            User user = hackerrankHandleToUserMap.get(userHandle);
                            if (user != null) {
                                // Update user's Hackerrank rating
                                if (user.getHackerrankRating() == null) {
                                    user.setHackerrankRating((int) model.score);
                                } else {
                                    user.setHackerrankRating(user.getHackerrankRating() + (int) model.score);
                                }
                                System.out.println("Hackerrank rating for " + userHandle + " is: " + (int) model.score);
                            } else {
                                System.out.println("User not found: " + userHandle);
                            }
                        }
                    } catch (IOException | ArithmeticException e) {
                        System.err.println("Error fetching Hackerrank rating for " + trackerName + ": " + e.getMessage());
                    }
                }
            }
            // Write Hackerrank ratings to file
            for (User user : trueHackerrank) {
                if (user.getHackerrankRating() != null) {
                    FileWriter writer = new FileWriter("hackerrank_ratings.txt", true);
                    writer.write(user.getHandle() + "," + user.getHackerrankHandle() + "," + user.getHackerrankRating() + "\n");
                    writer.close();
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching Hackerrank rating: " + e.getMessage());
        }
    }

    static class Leaderboard {
        List<LeaderboardModel> models;
    }

    static class LeaderboardModel {
        String hacker;
        double score;
    }

    /**
     * A method to load data from a CSV file to an SQL database.
     *
     * @param  path   the file path of the CSV to be loaded
     */
    public static void loadCSVtoSQL(String path) {
        // Database name
        String dbName = "cmrit";

        // Initialize database connection and reader
        Connection conn = null;
        BufferedReader reader = null;

        try {
            // Establish database connection
            conn = DriverManager.getConnection("jdbc:sqlite:" + dbName);
            Statement statement = conn.createStatement();

            // Create table if not exists
            statement.execute(CREATE_TABLE_SQL);

            // Prepare statement for inserting data
            PreparedStatement preparedStatement = conn.prepareStatement(INSERT_DATA_SQL);

            // Read data from CSV file
            reader = new BufferedReader(new FileReader(path));
            String line;
            while ((line = reader.readLine()) != null) {
                // skip header and last 2 rows
                if (line.equals("Handle,GeeksForGeeks Handle,Codeforces Handle,LeetCode Handle,CodeChef Handle,HackerRank Handle," + "GeeksForGeeks URL Exists,Codeforces URL Exists,LeetCode URL Exists,CodeChef URL Exists,HackerRank URL Exists"))
                    continue;
                if (line.startsWith("None") || line.startsWith("TOTAL")) continue;
                String[] data = line.split(",");
                for (int i = 0; i < data.length; i++) {
                    if (i == 6 || i == 7 || i == 8 || i == 9 || i == 10) {
                        preparedStatement.setBoolean(i + 1, Boolean.parseBoolean(data[i])); // URL Exists (true or false)
                    } else {
                        preparedStatement.setString(i + 1, data[i]);
                    }
                }
                preparedStatement.addBatch();
            }

            // Execute batch insert
            preparedStatement.executeBatch();

        } catch (SQLException | IOException e) {
            // Handle errors
            System.err.println("Error loading CSV data to SQL database: " + e.getMessage());
            throw new RuntimeException(e);
        } finally {
            // Close reader and connection
            try {
                if (reader != null) reader.close();
                if (conn != null) conn.close();
            } catch (IOException | SQLException e) {
                System.err.println("Error closing reader or connection: " + e.getMessage());
            }
        }
    }
}

/**
 * Represents a user with various handles and ratings on different platforms.
 * The User class provides methods to get and set the user's handles and ratings on platforms such as Codeforces, GeeksforGeeks, LeetCode, Codechef, and Hackerrank.
 * 
 * The User class has the following attributes:
 * - handle: The user's handle or username.
 * - codeforcesHandle: The user's handle on Codeforces platform.
 * - geeksforgeeksHandle: The user's handle on GeeksforGeeks platform.
 * - leetcodeHandle: The user's handle on LeetCode platform.
 * - codechefHandle: The user's handle on Codechef platform.
 * - hackerrankHandle: The user's handle on Hackerrank platform.
 * - codeforcesRating: The user's rating on Codeforces platform.
 * - geeksforgeeksRating: The user's rating on GeeksforGeeks platform.
 * - geeksforgeeksPracticeRating: The user's practice rating on GeeksforGeeks platform.
 * - leetcodeRating: The user's rating on LeetCode platform.
 * - codechefRating: The user's rating on Codechef platform.
 * - hackerrankRating: The user's rating on Hackerrank platform.
 * - percentile: The user's percentile.
 * 
 * The User class provides the following methods:
 * - getHandle(): Returns the user's handle.
 * - getCodeforcesHandle(): Returns the user's handle on Codeforces platform.
 * - getGeeksforgeeksHandle(): Returns the user's handle on GeeksforGeeks platform.
 * - getLeetcodeHandle(): Returns the user's handle on LeetCode platform.
 * - getCodechefHandle(): Returns the user's handle on Codechef platform.
 * - getHackerrankHandle(): Returns the user's handle on Hackerrank platform.
 * - getCodeforcesRating(): Returns the user's rating on Codeforces platform.
 * - getGeeksforgeeksRating(): Returns the user's rating on GeeksforGeeks platform.
 * - getgeeksforgeeksPracticeRating(): Returns the user's practice rating on GeeksforGeeks platform.
 * - getLeetcodeRating(): Returns the user's rating on LeetCode platform.
 * - getCodechefRating(): Returns the user's rating on Codechef platform.
 * - getHackerrankRating(): Returns the user's rating on Hackerrank platform.
 * - getPercentile(): Returns the user's percentile.
 * - setCodeforcesRating(Integer codeforcesRating): Sets the user's rating on Codeforces platform.
 * - setGeeksforgeeksRating(Integer geeksforgeeksRating): Sets the user's rating on GeeksforGeeks platform.
 * - setgeeksforgeeksPracticeRating(Integer geeksforgeeksPracticeRating): Sets the user's practice rating on GeeksforGeeks platform.
 * - setLeetcodeRating(Integer leetcodeRating): Sets the user's rating on LeetCode platform.
 * - setCodechefRating(Integer codechefRating): Sets the user's rating on Codechef platform.
 * - setHackerrankRating(Integer hackerrankRating): Sets the user's rating on Hackerrank platform.
 * - setPercentile(Double percentile): Sets the user's percentile.
 */

class User {
    private String handle;
    private String codeforcesHandle;
    private String geeksforgeeksHandle;
    private String leetcodeHandle;
    private String codechefHandle;
    private String hackerrankHandle;

    private Integer codeforcesRating;
    private Integer geeksforgeeksRating;
    private Integer geeksforgeeksPracticeRating;
    private Integer leetcodeRating;
    private Integer codechefRating;
    private Integer hackerrankRating;
    private Double percentile;

    public User(String handle, String platform, String username) {
        this.handle = handle;
        switch (platform) {
            case "codeforces" -> this.codeforcesHandle = username;
            case "geeksforgeeks" -> this.geeksforgeeksHandle = username;
            case "leetcode" -> this.leetcodeHandle = username;
            case "codechef" -> this.codechefHandle = username;
            case "hackerrank" -> this.hackerrankHandle = username;
        }
        // set all ratings to 0
        this.codeforcesRating = 0;
        this.geeksforgeeksRating = 0;
        this.geeksforgeeksPracticeRating = null;
        this.leetcodeRating = 0;
        this.codechefRating = 0;
        this.hackerrankRating = 0;
    }

    public User(String handle, String codeforcesHandle, String geeksforgeeksHandle, String leetcodeHandle, String codechefHandle, String hackerrankHandle) {
        this.handle = handle;
        this.codeforcesHandle = codeforcesHandle;
        this.geeksforgeeksHandle = geeksforgeeksHandle;
        this.leetcodeHandle = leetcodeHandle;
        this.codechefHandle = codechefHandle;
        this.hackerrankHandle = hackerrankHandle;
        // set all ratings to 0
        this.codeforcesRating = 0;
        this.geeksforgeeksRating = 0;
        this.geeksforgeeksPracticeRating = null;
        this.leetcodeRating = 0;
        this.codechefRating = 0;
        this.hackerrankRating = 0;
    }

    // getters

    public String getHandle() {
        return handle;
    }

    public String getCodeforcesHandle() {
        return codeforcesHandle;
    }

    public String getGeeksforgeeksHandle() {
        return geeksforgeeksHandle;
    }

    public String getLeetcodeHandle() {
        return leetcodeHandle;
    }

    public String getCodechefHandle() {
        return codechefHandle;
    }

    public String getHackerrankHandle() {
        return hackerrankHandle;
    }

    public Integer getCodeforcesRating() {
        return codeforcesRating;
    }

    public Integer getGeeksforgeeksRating() {
        return geeksforgeeksRating;
    }

    public Integer getgeeksforgeeksPracticeRating() {
        return geeksforgeeksPracticeRating;
    }

    public Integer getLeetcodeRating() {
        return leetcodeRating;
    }

    public Integer getCodechefRating() {
        return codechefRating;
    }

    public Integer getHackerrankRating() {
        return hackerrankRating;
    }

    public Double getPercentile() {
        return percentile;
    }

    // setters

    public void setCodeforcesRating(Integer codeforcesRating) {
        this.codeforcesRating = codeforcesRating;
    }

    public void setGeeksforgeeksRating(Integer geeksforgeeksRating) {
        this.geeksforgeeksRating = geeksforgeeksRating;
    }

    public void setgeeksforgeeksPracticeRating(Integer geeksforgeeksPracticeRating) {
        this.geeksforgeeksPracticeRating = geeksforgeeksPracticeRating;
    }

    public void setLeetcodeRating(Integer leetcodeRating) {
        this.leetcodeRating = leetcodeRating;
    }

    public void setCodechefRating(Integer codechefRating) {
        this.codechefRating = codechefRating;
    }

    public void setHackerrankRating(Integer hackerrankRating) {
        this.hackerrankRating = hackerrankRating;
    }

    public void setPercentile(Double percentile) {
        this.percentile = percentile;
    }
}
