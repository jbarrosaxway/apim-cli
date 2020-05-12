package com.axway.apim.appimport.it.basic;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import com.axway.apim.appimport.ApplicationImportTestAction;
import com.axway.apim.lib.errorHandling.AppException;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.dsl.testng.TestNGCitrusTestRunner;
import com.consol.citrus.functions.core.RandomNumberFunction;
import com.consol.citrus.message.MessageType;

@Test
public class ImportCompleteApplicationTestIT extends TestNGCitrusTestRunner {

	private ApplicationImportTestAction appImport = new ApplicationImportTestAction();
	
	private static String PACKAGE = "/com/axway/apim/appimport/apps/basic/";
	
	@CitrusTest
	@Test @Parameters("context")
	public void importApplicationBasicTest(@Optional @CitrusResource TestContext context) throws IOException, AppException {
		description("Import application into API-Manager");
		
		variable("appNumber", RandomNumberFunction.getRandomNumber(3, true));
		variable("appName", "Complete-App-${appNumber}");
		variable("phone", "123456789-${appNumber}");
		variable("description", "My App-Description ${appNumber}");
		variable("email", "email-${appNumber}@customer.com");
		variable("state", "approved");
		
		

		echo("####### Import application: '${appName}' #######");		
		createVariable(ApplicationImportTestAction.CONFIG,  PACKAGE + "CompleteApplication.json");
		createVariable("expectedReturnCode", "0");
		appImport.doExecute(context);
		
		echo("####### Validate application: '${appName}' has been imported #######");
		http(builder -> builder.client("apiManager").send().get("/applications?field=name&op=eq&value=${appName}").header("Content-Type", "application/json"));

		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
			.validate("$.[?(@.name=='${appName}')].name", "@assertThat(hasSize(1))@")
			.validate("$.[?(@.name=='${appName}')].phone", "${phone}")
			.validate("$.[?(@.name=='${appName}')].description", "${description}")
			.validate("$.[?(@.name=='${appName}')].email", "${email}")
			.validate("$.[?(@.name=='${appName}')].state", "${state}")
			.validate("$.[?(@.name=='${appName}')].image", "@assertThat(containsString(/api/portal/v))@")
			.extractFromPayload("$.[?(@.name=='${appName}')].id", "appId"));
		
		echo("####### Validate application: '${appName}' with id: ${appId} quota has been imported #######");
		http(builder -> builder.client("apiManager").send().get("/applications/${appId}/quota").header("Content-Type", "application/json"));
		
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
				.validate("$.type", "APPLICATION")
				.validate("$.restrictions[*].api", "@assertThat(hasSize(1))@")
				.validate("$.restrictions[0].api", "*")
				.validate("$.restrictions[0].method", "*")
				.validate("$.restrictions[0].type", "throttle")
				.validate("$.restrictions[0].config.messages", "9999")
				.validate("$.restrictions[0].config.period", "week")
				.validate("$.restrictions[0].config.per", "1"));
		
		echo("####### Validate application: '${appName}' with id: ${appId} OAuth has been imported #######");
		http(builder -> builder.client("apiManager").send().get("/applications/${appId}/oauth").header("Content-Type", "application/json"));
		
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
				.validate("$[0].id", "ClientConfidentialApp-${appNumber}")
				.validate("$[0].secret", "9cb76d80-1bc2-48d3-8d31-edeec0fddf6c"));
		
		echo("####### Validate application: '${appName}' with id: ${appId} API-Key has been imported #######");
		http(builder -> builder.client("apiManager").send().get("/applications/${appId}/apikeys").header("Content-Type", "application/json"));
		
		http(builder -> builder.client("apiManager").receive().response(HttpStatus.OK).messageType(MessageType.JSON)
				.validate("$[0].id", "6cd55c27-675a-444a-9bc7-ae9a7869184d-${appNumber}")
				.validate("$[0].secret", "34f2b2d6-0334-4dcc-8442-e0e7009b8950"));
		
		echo("####### Re-Import same application - Should be a No-Change #######");
		createVariable("expectedReturnCode", "10");
		appImport.doExecute(context);		
	}
}
