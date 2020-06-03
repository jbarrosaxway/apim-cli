package com.axway.apim.appimport.it.basic;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.axway.apim.appimport.ApplicationImportTestAction;
import com.axway.apim.lib.errorHandling.AppException;
import com.axway.apim.test.ImportTestAction;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.dsl.testng.TestNGCitrusTestRunner;
import com.consol.citrus.functions.core.RandomNumberFunction;
import com.consol.citrus.message.MessageType;

@Test
public class ImportApplicationWithAPIAccessTestIT extends TestNGCitrusTestRunner {

	private ApplicationImportTestAction appImport = new ApplicationImportTestAction();
	
	private ImportTestAction apiImport = new ImportTestAction();
	
	private static String PACKAGE = "/com/axway/apim/appimport/apps/basic/";
	
	@CitrusTest
	@Test @Parameters("context")
	public void importApplicationBasicTest(@Optional @CitrusResource TestContext context) throws IOException, AppException {
		description("Import application that has a subscription to an API");
		
		variable("apiNumber", RandomNumberFunction.getRandomNumber(4, true));
		

		variable("apiPath", "/test-app-api1-${apiNumber}");
		variable("apiName", "Test-App-API1-${apiNumber}");
		variable("apiName1", "${apiName}");

		echo("####### Importing Test API 1 : '${apiName}' on path: '${apiPath}' #######");
		createVariable(ImportTestAction.API_DEFINITION,  PACKAGE + "petstore.json");
		createVariable(ImportTestAction.API_CONFIG,  PACKAGE + "test-api-config.json");
		createVariable("expectedReturnCode", "0");
		apiImport.doExecute(context);
		
		echo("####### Extract ID of imported API 1: '${apiName}' on path: '${apiPath}' #######");
		http(builder -> builder.client("apiManager").send().get("/proxies").header("Content-Type", "application/json"));

		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.path=='${apiPath}')].name", "${apiName}")
			.extractFromPayload("$.[?(@.path=='${apiPath}')].id", "apiId1"));
		

		variable("apiPath", "/test-app-api2-${apiNumber}");
		variable("apiName", "Test-App-API2-${apiNumber}");
		variable("apiName2", "${apiName}");
		
		echo("####### Importing Test API 2 : '${apiName}' on path: '${apiPath}' #######");
		createVariable(ImportTestAction.API_DEFINITION,  PACKAGE + "petstore.json");
		createVariable(ImportTestAction.API_CONFIG,  PACKAGE + "test-api-config.json");
		createVariable("expectedReturnCode", "0");
		apiImport.doExecute(context);
		
		echo("####### Extract ID of imported API 2: '${apiName}' on path: '${apiPath}' #######");
		http(builder -> builder.client("apiManager").send().get("/proxies").header("Content-Type", "application/json"));

		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.path=='${apiPath}')].name", "${apiName}")
			.extractFromPayload("$.[?(@.path=='${apiPath}')].id", "apiId2"));
		
		echo("###### API 1: '${apiName1}' (${apiId1})");
		echo("###### API 2: '${apiName2}' (${apiId2})");
		
		
		variable("appNumber", RandomNumberFunction.getRandomNumber(4, true));
		variable("appName", "Complete-App-${appNumber}");

		echo("####### Import application: '${appName}' with access to ONE API #######");
		createVariable(ApplicationImportTestAction.CONFIG,  PACKAGE + "AppWithAPIAccess.json");
		createVariable("expectedReturnCode", "0");
		appImport.doExecute(context);
		
		echo("####### Validate application: '${appName}' has been imported #######");
		http(builder -> builder.client("apiManager").send().get("/applications?field=name&op=eq&value=${appName}").header("Content-Type", "application/json"));

		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.name=='${appName}')].name", "@assertThat(hasSize(1))@")
			.extractFromPayload("$.[?(@.name=='${appName}')].id", "appId"));
		
		echo("####### Validate application: '${appName}' has access to the imported API 1 #######");
		http(builder -> builder.client("apiManager").send().get("/applications/${appId}/apis").header("Content-Type", "application/json"));
		
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.apiId=='${apiId1}')].state", "approved"));
		
		echo("####### Import application: '${appName}' with access to TWO APIs #######");
		createVariable(ApplicationImportTestAction.CONFIG,  PACKAGE + "AppWithAPITwoAccesses.json");
		createVariable("expectedReturnCode", "0");
		appImport.doExecute(context);
		
		echo("####### Validate application: '${appName}' has been imported #######");
		http(builder -> builder.client("apiManager").send().get("/applications?field=name&op=eq&value=${appName}").header("Content-Type", "application/json"));
		
		// Make sure, the application id is unchanged
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.id=='${appId}')].name", "${appName}"));
		
		echo("####### Validate application: '${appName}' has access to the imported API 1 and 2 #######");
		http(builder -> builder.client("apiManager").send().get("/applications/${appId}/apis").header("Content-Type", "application/json"));
		
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.apiId=='${apiId1}')].state", "approved")
			.validate("$.[?(@.apiId=='${apiId2}')].state", "approved"));
	}
}