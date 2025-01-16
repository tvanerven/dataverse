package edu.harvard.iq.dataverse.api;

import edu.harvard.iq.dataverse.settings.SettingsServiceBean;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static jakarta.ws.rs.core.Response.Status.*;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SendFeedbackApiIT {

    @BeforeAll
    public static void setUpClass() {
        RestAssured.baseURI = UtilIT.getRestAssuredBaseUri();
    }

    @AfterEach
    public void reset() {
        UtilIT.deleteSetting(SettingsServiceBean.Key.RateLimitingCapacityByTierAndAction);
    }

    @Test
    public void testSupportRequest() {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("fromEmail", "from@mailinator.com");
        job.add("subject", "Help!");
        job.add("body", "I need help.");

        Response response = UtilIT.sendFeedback(job, null);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data[0].fromEmail", CoreMatchers.equalTo("from@mailinator.com"));
    }

    @Test
    public void testSubmitFeedbackOnRootDataverse() {
        JsonObjectBuilder job = Json.createObjectBuilder();
        long rootDataverseId = 1;
        job.add("targetId", rootDataverseId);
        job.add("fromEmail", "from@mailinator.com");
        job.add("toEmail", "to@mailinator.com");
        job.add("subject", "collaboration");
        job.add("body", "Are you interested writing a grant based on this research?");

        Response response = UtilIT.submitFeedback(job);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(OK.getStatusCode());
    }

    @Test
    public void testSendFeedbackOnDataset() {
        Response createUser = UtilIT.createRandomUser();
        createUser.prettyPrint();
        createUser.then().assertThat()
                .statusCode(OK.getStatusCode());
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);
        String fromEmail = UtilIT.getEmailFromResponse(createUser);

        Response createDataverseResponse = UtilIT.createRandomDataverse(apiToken);
        createDataverseResponse.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        String dataverseAlias = UtilIT.getAliasFromResponse(createDataverseResponse);

        String pathToJsonFile = "scripts/api/data/dataset-create-new-all-default-fields.json";
        Response createDataset = UtilIT.createDatasetViaNativeApi(dataverseAlias, pathToJsonFile, apiToken);
        createDataset.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        long datasetId = JsonPath.from(createDataset.body().asString()).getLong("data.id");
        Response response;

        // Test with body text length to long
        UtilIT.setSetting(SettingsServiceBean.Key.ContactFeedbackMessageSizeLimit, "10");
        response = UtilIT.sendFeedback(buildJsonEmail(datasetId, null), apiToken);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("message", CoreMatchers.equalTo("body exceeds feedback length"));
        // reset to unlimited
        UtilIT.setSetting(SettingsServiceBean.Key.ContactFeedbackMessageSizeLimit, "0");

        // Test with no body/body length =0
        response = UtilIT.sendFeedback(Json.createObjectBuilder().add("targetId", datasetId).add("subject", "collaboration").add("body", ""), apiToken);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("message", CoreMatchers.equalTo("body can not be empty"));

        // Don't send fromEmail. Let it get it from the requesting user
        response = UtilIT.sendFeedback(buildJsonEmail(datasetId, null), apiToken);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data[0].fromEmail", CoreMatchers.equalTo(fromEmail));

        // Test guest calling with no token
        fromEmail = "testEmail@example.com";
        response = UtilIT.sendFeedback(buildJsonEmail(datasetId, fromEmail), null);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data[0].fromEmail", CoreMatchers.equalTo(fromEmail));
        validateEmail(response.body().asString());

        // Test guest calling with no token and missing email
        response = UtilIT.sendFeedback(buildJsonEmail(datasetId, null), null);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("message", CoreMatchers.equalTo("Missing 'fromEmail'"));

        // Test with invalid email - also tests that fromEmail trumps the users email if it is included in the Json
        response = UtilIT.sendFeedback(buildJsonEmail(datasetId, "BADEmail"), apiToken);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(BAD_REQUEST.getStatusCode())
                .body("message", CoreMatchers.equalTo("Invalid 'fromEmail'"));
    }

    private JsonObjectBuilder buildJsonEmail(long datasetId, String fromEmail) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        job.add("targetId", datasetId);
        job.add("subject", "collaboration");
        job.add("body", "Are you interested writing a grant based on this research? {\"<script src=\\\"http://malicious.url.com\\\"/>\", \"\"}");
        if (fromEmail != null) {
            job.add("fromEmail", fromEmail);
        }
        return job;
    }
    private void validateEmail(String body) {
        assertTrue(!body.contains("malicious.url.com"));
    }

    @Test
    public void testSendRateLimited() {
        Response createUser = UtilIT.createRandomUser();
        createUser.prettyPrint();
        createUser.then().assertThat()
                .statusCode(OK.getStatusCode());
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);
        String fromEmail = UtilIT.getEmailFromResponse(createUser);

        Response createDataverseResponse = UtilIT.createRandomDataverse(apiToken);
        createDataverseResponse.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        String dataverseAlias = UtilIT.getAliasFromResponse(createDataverseResponse);

        String pathToJsonFile = "scripts/api/data/dataset-create-new-all-default-fields.json";
        Response createDataset = UtilIT.createDatasetViaNativeApi(dataverseAlias, pathToJsonFile, apiToken);
        createDataset.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        long datasetId = JsonPath.from(createDataset.body().asString()).getLong("data.id");
        Response response;

        // Test with rate limiting on
        UtilIT.setSetting(SettingsServiceBean.Key.RateLimitingCapacityByTierAndAction, "[{\"tier\": 0, \"limitPerHour\": 1, \"actions\": [\"CheckRateLimitForDatasetFeedbackCommand\"]}]");
        // This call gets allowed because the setting change OKs it when resetting rate limiting
        response = UtilIT.sendFeedback(buildJsonEmail(datasetId, "testEmail@example.com"), null);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data[0].fromEmail", CoreMatchers.equalTo("testEmail@example.com"));

        // Call 1 of the 1 per hour limit
        response = UtilIT.sendFeedback(buildJsonEmail(datasetId, "testEmail2@example.com"), null);
        response.prettyPrint();
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(OK.getStatusCode());
        // Call 2 - over the limit
        response = UtilIT.sendFeedback(buildJsonEmail(datasetId, "testEmail2@example.com"), null);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(TOO_MANY_REQUESTS.getStatusCode());

        response = UtilIT.sendFeedback(buildJsonEmail(datasetId, null), apiToken);
        response.prettyPrint();
        response.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data[0].fromEmail", CoreMatchers.equalTo(fromEmail));
    }
}
