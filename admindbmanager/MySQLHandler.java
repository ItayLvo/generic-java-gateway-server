package admindbmanager;

import com.google.gson.JsonObject;

import java.sql.*;

public class MySQLHandler implements DBMSHandler {

    // Method to create a reusable database connection
    private Connection getDatabaseConnection() throws SQLException {
        return DriverManager.getConnection("jdbc:mysql://localhost:3306/Company", "itay", "Aa12345678!");
    }


    @Override
    public JsonObject registerCompany(JsonObject data) {
        String companyName = data.get("Name").getAsString();
        String companyAddress = data.get("Address").getAsString();
        int numProducts = data.get("Products").getAsInt();

        String insertSQL = "INSERT INTO Company (Name, Address, Number_of_products) VALUES (?, ?, ?)";
        JsonObject responseJson = new JsonObject();

        // try with resources: create connection to MySQL DB, and then a prepared statement
        try (Connection connection = getDatabaseConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {

            // set parameters for the query
            preparedStatement.setString(1, companyName);
            preparedStatement.setString(2, companyAddress);
            preparedStatement.setInt(3, numProducts);

            // execute the insert and get affected row count
            int affectedRows = preparedStatement.executeUpdate();
            if (affectedRows == 0) {
                throw new SQLException("Inserting company failed");
            }

            // update the response JSON:
            // get generated key (companyID)
            int companyId = -1;
            ResultSet generatedKeys = preparedStatement.getGeneratedKeys();
            if (generatedKeys.next()) {
                companyId = generatedKeys.getInt(1);
            }
            responseJson.addProperty("Status", "200");
            responseJson.addProperty("Info", "Registered company ID: " + companyId + ". Registered company name: " + companyName + ".");
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println(e.getMessage()); //TODO remove test prints
            responseJson.addProperty("Status", "400");
            responseJson.addProperty("Info", "Failed creating new Company");
        }
        return responseJson;
    }

    @Override
    public JsonObject registerProduct(JsonObject data) {
        return null;
    }

    @Override
    public JsonObject getProduct(JsonObject data) {
        return null;
    }

    @Override
    public JsonObject getProducts(JsonObject data) {
        return null;
    }

    @Override
    public JsonObject getCompany(JsonObject data) {
        String companyName = data.get("Name").getAsString();

        String insertSQL = "SELECT * FROM Company WHERE Company.Name = ?";
        JsonObject responseJson = new JsonObject();

        // try with resources: create connection to MySQL DB, and then a prepared statement
        try (Connection connection = getDatabaseConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {

            // set parameters for the query
            preparedStatement.setString(1, companyName);

            // execute the insert and get affected row count
            ResultSet queryResult = preparedStatement.executeQuery();

            // update the response JSON:
            while (queryResult.next()) {
                responseJson.addProperty("Status", "200");
                responseJson.addProperty("Info", "company ID: " + queryResult.getString("CompanyID") +
                        ", company name: " + queryResult.getString("Name") +
                        ", address: " + queryResult.getString("Address") +
                        ", Number of products: " + queryResult.getInt("Number_of_products"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println(e.getMessage()); //TODO remove test prints
            responseJson.addProperty("Status", "400");
            responseJson.addProperty("Info", "Failed fetching Company data");
        }
        return responseJson;
    }

    @Override
    public JsonObject getCompanies(JsonObject data) {
        String insertSQL = "SELECT * FROM Company";
        
        JsonObject responseJson = new JsonObject();

        // try with resources: create connection to MySQL DB, and then a prepared statement
        try (Connection connection = getDatabaseConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(insertSQL)) {

            // execute the insert and get affected row count
            ResultSet queryResult = preparedStatement.executeQuery();

            // update the response JSON:
            responseJson.addProperty("Status", "200");
            int i = 0;
            while (queryResult.next()) {
                JsonObject row = new JsonObject();
                row.addProperty("company ID", queryResult.getInt("CompanyID"));
                row.addProperty("company name", queryResult.getString("Name"));
                row.addProperty("company address", queryResult.getString("Address"));
                row.addProperty("Number of products", queryResult.getInt("Number_of_products"));
                responseJson.add("Row " + (i++), row);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println(e.getMessage()); //TODO remove test prints
            responseJson.addProperty("Status", "400");
            responseJson.addProperty("Info", "Failed fetching Company data");
        }
        return responseJson;
    }
}
