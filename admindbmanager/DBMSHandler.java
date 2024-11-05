package admindbmanager;

import com.google.gson.JsonObject;

public interface DBMSHandler {
    JsonObject registerCompany(JsonObject data);
    JsonObject registerProduct(JsonObject data);
    JsonObject getProduct(JsonObject data);
    JsonObject getProducts(JsonObject data);
    JsonObject getCompany(JsonObject data);
    JsonObject getCompanies(JsonObject data);
}
