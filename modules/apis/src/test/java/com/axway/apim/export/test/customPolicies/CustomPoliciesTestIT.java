package com.axway.apim.export.test.customPolicies;

import com.axway.apim.api.model.*;
import com.axway.apim.export.test.ExportTestAction;
import com.axway.apim.test.ImportTestAction;
import com.consol.citrus.annotations.CitrusResource;
import com.consol.citrus.annotations.CitrusTest;
import com.consol.citrus.context.TestContext;
import com.consol.citrus.dsl.testng.TestNGCitrusTestRunner;
import com.consol.citrus.functions.core.RandomNumberFunction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.testng.annotations.Optional;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.*;

@Test
public class CustomPoliciesTestIT extends TestNGCitrusTestRunner {

	private final ExportTestAction swaggerExport = new ExportTestAction();
	private final ImportTestAction swaggerImport = new ImportTestAction();
	ObjectMapper mapper = new ObjectMapper();
	
	@CitrusTest
	@Test @Parameters("context")
	public void run(@Optional @CitrusResource TestContext context) throws IOException {		
		description("Import an API to export it afterwards");
		createVariable("useApiAdmin", "true");
		variable("apiNumber", RandomNumberFunction.getRandomNumber(3, true));
		variable("apiPath", "/api/test/"+this.getClass().getSimpleName()+"-${apiNumber}");
		variable("apiName", this.getClass().getSimpleName()+"-${apiNumber}");
		variable("state", "published");
		echo("####### Importing the API, which should exported in the second step #######");
		createVariable(ImportTestAction.API_DEFINITION,  "/test/export/files/basic/petstore.json");
		createVariable(ImportTestAction.API_CONFIG,  "/test/export/files/customPolicies/custom-policies-issue-156.json");
		createVariable("requestPolicy", "Request policy 1");
		createVariable("responsePolicy", "Response policy 1");
		createVariable("tokenInfoPolicy", "Tokeninfo policy 1");
		createVariable("expectedReturnCode", "0");
		swaggerImport.doExecute(context);
		exportAPI(context, false);
		exportAPI(context, true);
	}
	
	private void exportAPI(TestContext context, boolean ignoreAdminAccount) throws IOException {
		variable("exportLocation", "citrus:systemProperty('java.io.tmpdir')");
		variable(ExportTestAction.EXPORT_API,  "${apiPath}");
		// These are the folder and filenames generated by the export tool 
		variable("exportFolder", "api-test-${apiName}");
		variable("exportAPIName", "${apiName}.json");
		
		echo("####### Export the API from the API-Manager #######");
		createVariable("expectedReturnCode", "0");
		
		if(ignoreAdminAccount) {
			echo("####### Exporting the API with Org-Admin permissions only #######");
			createVariable("exportLocation", "${exportLocation}/orgAdmin");
			createVariable("useApiAdmin", "false"); // This is an org-admin user
		} else {
			createVariable("exportLocation", "${exportLocation}/ignoreAdminAccount");
			echo("####### Exporting the API with Admin permissions #######");
		}
		
		swaggerExport.doExecute(context);
		
		String exportedAPIConfigFile = context.getVariable("exportLocation")+"/"+context.getVariable("exportFolder")+"/api-config.json";
		
		echo("####### Reading exported API-Config file: '"+exportedAPIConfigFile+"' #######");
		JsonNode exportedAPIConfig = mapper.readTree(Files.newInputStream(Paths.get(exportedAPIConfigFile)));
		String tmp = context.replaceDynamicContentInString(IOUtils.toString(this.getClass().getResourceAsStream("/test/export/files/customPolicies/custom-policies-issue-156.json"), StandardCharsets.UTF_8));
		JsonNode importedAPIConfig = mapper.readTree(tmp);

		assertEquals(exportedAPIConfig.get("path").asText(), 				context.getVariable("apiPath"));
		assertEquals(exportedAPIConfig.get("name").asText(), 				context.getVariable("apiName"));
		assertEquals(exportedAPIConfig.get("state").asText(), 				context.getVariable("state"));
		assertEquals(exportedAPIConfig.get("version").asText(), 			"v1");
		assertEquals(exportedAPIConfig.get("organization").asText(),		"API Development "+context.getVariable("orgNumber"));
		
		List<SecurityProfile> importedSecurityProfiles = mapper.convertValue(importedAPIConfig.get("securityProfiles"), new TypeReference<List<SecurityProfile>>(){});
		List<SecurityProfile> exportedSecurityProfiles = mapper.convertValue(exportedAPIConfig.get("securityProfiles"), new TypeReference<List<SecurityProfile>>(){});
		assertEquals(importedSecurityProfiles, exportedSecurityProfiles, "SecurityProfiles are not equal.");
		
		Map<String, OutboundProfile> importedOutboundProfiles = mapper.convertValue(importedAPIConfig.get("outboundProfiles"), new TypeReference<Map<String, OutboundProfile>>(){});
		Map<String, OutboundProfile> exportedOutboundProfiles = mapper.convertValue(exportedAPIConfig.get("outboundProfiles"), new TypeReference<Map<String, OutboundProfile>>(){});
		assertEquals(importedOutboundProfiles, exportedOutboundProfiles, "OutboundProfiles are not equal.");
		assertFalse(exportedAPIConfig.get("outboundProfiles").get("_default").get("requestPolicy").asText().startsWith("<key"), "Request policy should not start with <key");
		assertFalse(exportedAPIConfig.get("outboundProfiles").get("_default").get("responsePolicy").asText().startsWith("<key"), "Request policy should not start with <key");

		TagMap importedTags = mapper.convertValue(importedAPIConfig.get("tags"), new TypeReference<TagMap>(){});
		TagMap exportedTags = mapper.convertValue(exportedAPIConfig.get("tags"), new TypeReference<TagMap>(){});
		assertEquals(importedTags.equals(exportedTags), "Tags are not equal.");
		
		List<CorsProfile> importedCorsProfiles = mapper.convertValue(importedAPIConfig.get("corsProfiles"), new TypeReference<List<CorsProfile>>(){});
		List<CorsProfile> exportedCorsProfiles = mapper.convertValue(exportedAPIConfig.get("corsProfiles"), new TypeReference<List<CorsProfile>>(){});
		assertEquals(importedCorsProfiles, exportedCorsProfiles, "CorsProfiles are not equal.");
		
		APIQuota importedAppQuota = mapper.convertValue(importedAPIConfig.get("applicationQuota"), new TypeReference<APIQuota>(){});
		APIQuota exportedAppQuota = mapper.convertValue(exportedAPIConfig.get("applicationQuota"), new TypeReference<APIQuota>(){});
		assertEquals(importedAppQuota, exportedAppQuota, "applicationQuota are not equal.");
		
		APIQuota importedSystemQuota = mapper.convertValue(importedAPIConfig.get("systemQuota"), new TypeReference<APIQuota>(){});
		APIQuota exportedSystemQuota = mapper.convertValue(exportedAPIConfig.get("systemQuota"), new TypeReference<APIQuota>(){});
		assertEquals(importedSystemQuota, exportedSystemQuota, "systemQuota are not equal.");
		

		assertEquals(exportedAPIConfig.get("caCerts").size(), 				3);
		
		assertEquals(exportedAPIConfig.get("caCerts").get(0).get("certFile").asText(), 				"sample-certificate.crt");
		assertFalse(exportedAPIConfig.get("caCerts").get(0).get("inbound").asBoolean());
		assertTrue(exportedAPIConfig.get("caCerts").get(0).get("outbound").asBoolean());

		assertEquals(exportedAPIConfig.get("caCerts").get(1).get("certFile").asText(), 				"SampleEncryptAuthority.crt");
		assertFalse(exportedAPIConfig.get("caCerts").get(1).get("inbound").asBoolean());
		assertTrue(exportedAPIConfig.get("caCerts").get(1).get("outbound").asBoolean());

		assertEquals(exportedAPIConfig.get("caCerts").get(2).get("certFile").asText(), 				"SampleRootCA.crt");
		assertFalse(exportedAPIConfig.get("caCerts").get(2).get("inbound").asBoolean());
		assertTrue(exportedAPIConfig.get("caCerts").get(2).get("outbound").asBoolean());
		
		assertTrue(new File(context.getVariable("exportLocation")+"/"+context.getVariable("exportFolder")+"/sample-certificate.crt").exists(), "Certificate sample-certificate.crt is missing");
		assertTrue(new File(context.getVariable("exportLocation")+"/"+context.getVariable("exportFolder")+"/SampleEncryptAuthority.crt").exists(), "Certificate SampleEncryptAuthority.crt is missing");
		assertTrue(new File(context.getVariable("exportLocation")+"/"+context.getVariable("exportFolder")+"/SampleRootCA.crt").exists(), "Certificate SampleRootCA.crt is missing");
		
		assertTrue(new File(context.getVariable("exportLocation")+"/"+context.getVariable("exportFolder")+"/"+context.getVariable("exportAPIName")).exists(), "Exported Swagger-File is missing");
	}
}
