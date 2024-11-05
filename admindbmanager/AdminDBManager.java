package admindbmanager;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;

public class AdminDBManager {
    private final Map<String, DBMSHandler> dbmsMap = new HashMap<>();

    public AdminDBManager() {
        initDBMSHandlers();
    }

    private void initDBMSHandlers() {
        dbmsMap.put("mysql", new MySQLHandler());
    }

    public JsonObject registerCompany(JsonObject data) {
        DBMSHandler dbmsHandler = extractHandlerFromJSON(data);
        return dbmsHandler.registerCompany(data);
    }

    public JsonObject getCompany(JsonObject data) {
        DBMSHandler dbmsHandler = extractHandlerFromJSON(data);
        return dbmsHandler.getCompany(data);
    }

    public JsonObject getCompanies(JsonObject data) {
        DBMSHandler dbmsHandler = extractHandlerFromJSON(data);
        return dbmsHandler.getCompanies(data);
    }

    //TODO: implement more administrative DB commands
    /*
    public JsonObject registerProduct(JsonObject data) {
        return dbHandler.registerProduct(data);
    }

    public JsonObject getProduct(JsonObject data) {
        return dbHandler.getProduct(data);
    }

    public JsonObject getProducts(JsonObject data) {
        return dbHandler.getProducts(data);
    }




    */

    private DBMSHandler extractHandlerFromJSON(JsonObject data) {
        String dbms = data.get("dbms").getAsString();
        DBMSHandler dbmsHandler = dbmsMap.get(dbms);

        return dbmsHandler;
    }

}
