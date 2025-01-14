package com.axway.apim.test.apimethods;

import com.axway.apim.export.test.ExportTestAction;
import com.axway.apim.lib.error.AppException;
import com.axway.apim.test.ImportTestAction;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.dsl.testng.TestNGCitrusTestRunner;
import com.consol.citrus.functions.core.RandomNumberFunction;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.testng.Assert.assertEquals;

public class ImportApiMethodsWithTagsIT extends TestNGCitrusTestRunner {

    @CitrusTest
    @Test
    @Parameters("context")
    public void run(@Optional @CitrusResource TestContext context) throws IOException, AppException {
        ImportTestAction importTestAction = new ImportTestAction();
        ExportTestAction exportTestAction = new ExportTestAction();
        variable("apiNumber", RandomNumberFunction.getRandomNumber(4, true));
        variable("apiPath", "/basic-api-method-level-api-${apiNumber}");
        variable("apiName", "API Method-Export-${apiNumber}");
        variable("exportLocation", "citrus:systemProperty('java.io.tmpdir')");
        variable(ExportTestAction.EXPORT_API, "${apiPath}");

        // These are the folder and filenames generated by the export tool
        variable("exportFolder", "api-test-${apiName}");
        variable("exportAPIName", "${apiName}.json");

        echo("####### Try to replicate an API having Method-Level settings declared #######");
        createVariable(ImportTestAction.API_DEFINITION, "/com/axway/apim/test/files/basic/petstore.json");
        createVariable(ImportTestAction.API_CONFIG, "/com/axway/apim/test/files/apimethods/api_methods_with_tags.json");
        createVariable("state", "published");
        createVariable("expectedReturnCode", "0");
        createVariable("securityProfileName", "APIKeyBased${apiNumber}");
        importTestAction.doExecute(context);

        echo("####### Export the API including applications from the API-Manager #######");
        createVariable("exportMethods", "true");
        createVariable("expectedReturnCode", "0");
        exportTestAction.doExecute(context);

        String exportedAPIConfigFile = context.getVariable("exportLocation") + "/" + context.getVariable("apiPath") + "/api-config.json";
        echo("Exported config file location " + exportedAPIConfigFile);
        echo("####### Reading exported API-Config file: '" + exportedAPIConfigFile + "' #######");
        DocumentContext documentContext = JsonPath.parse(new File(exportedAPIConfigFile));
        assertEquals(documentContext.read("$.version", String.class), "1.0.7");
        assertEquals(documentContext.read("organization", String.class), "API Development " + context.getVariable("orgNumber"));
        assertEquals(documentContext.read("state", String.class), "published");
        assertEquals(documentContext.read("path", String.class), context.getVariable("apiPath"));
        assertEquals(documentContext.read("name", String.class), context.getVariable("apiName"));
        assertEquals(documentContext.read("apiMethods", ArrayList.class).size(), 20);
        assertEquals(documentContext.read("$.apiMethods[?(@.name=='createUser')].tags.stage", ArrayList.class).get(0), Arrays.asList("dev"));
        assertEquals(documentContext.read("$.apiMethods[?(@.name=='logoutUser')].tags.stage", ArrayList.class).get(0), Arrays.asList("dev"));
    }


}
