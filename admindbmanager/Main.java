package admindbmanager;

import com.google.gson.JsonObject;

//TODO this is a test use case for the Admin DB manager - to be ignored, and removed later
public class Main {
    public static void main(String[] args) {
        // create an instance of AdminDBManager.MySQLHandler
        AdminDBManager dbManager = new AdminDBManager();
/*
        JsonObject response = createCompanyTest(dbManager);
        System.out.println(response);

        JsonObject response2 = getCompanyTest(dbManager);
        System.out.println(response2);
*/
        JsonObject response3 = getCompaniesTest(dbManager);
        System.out.println(response3);
    }

    private static JsonObject getCompaniesTest(AdminDBManager dbManager) {
        JsonObject data = new JsonObject();
        data.addProperty("dbms", "mysql");
        JsonObject response = dbManager.getCompanies(data);
        String longString = "Hello my name is";


        return response;
    }

    private static JsonObject getCompanyTest(AdminDBManager dbManager) {
        JsonObject data = new JsonObject();
        data.addProperty("dbms", "mysql");
        data.addProperty("Name", "Amazon");
        JsonObject response = dbManager.getCompany(data);

        return response;
    }

    private static JsonObject createCompanyTest(AdminDBManager dbManager) {
        // create a company JSON object
        JsonObject companyData = new JsonObject();
        companyData.addProperty("dbms", "mysql");
        companyData.addProperty("Name", "Amazon");
        companyData.addProperty("Address", "D-Mall, Ramat Gan");
        companyData.addProperty("Products", 1000);

        // register the company and get the response
        JsonObject response = dbManager.registerCompany(companyData);
        return response;
    }
}