package com.axway.apim.organization.adapter;

import com.axway.apim.adapter.APIManagerAdapter;
import com.axway.apim.adapter.apis.APIFilter;
import com.axway.apim.adapter.apis.APIManagerAPIAdapter;
import com.axway.apim.api.API;
import com.axway.apim.api.model.APIAccess;
import com.axway.apim.api.model.CustomProperties.Type;
import com.axway.apim.api.model.Image;
import com.axway.apim.api.model.Organization;
import com.axway.apim.lib.Result;
import com.axway.apim.lib.error.AppException;
import com.axway.apim.lib.error.ErrorCode;
import com.axway.apim.lib.utils.Utils;
import com.axway.apim.organization.lib.OrgImportParams;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class OrgConfigAdapter extends OrgAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(OrgConfigAdapter.class);

    OrgImportParams importParams;

    public OrgConfigAdapter(OrgImportParams params) {
        this.importParams = params;
        this.result = new Result(); // Not used, to be refactored
    }

    public void readConfig() throws AppException {
        ObjectMapper mapper = null;
        String config = importParams.getConfig();
        String stage = importParams.getStage();

        File configFile = Utils.locateConfigFile(config);
        if (!configFile.exists()) return;
        File stageConfig = Utils.getStageConfig(stage, importParams.getStageConfig(), configFile);
        List<Organization> baseOrgs;
        // Try to read a list of organizations
        try {
            mapper = Utils.createObjectMapper(configFile);
            baseOrgs = mapper.readValue(Utils.substituteVariables(configFile), new TypeReference<List<Organization>>() {
            });
            if (stageConfig != null) {
                throw new AppException("Stage overrides are not supported for organization lists.", ErrorCode.CANT_READ_CONFIG_FILE);
            } else {
                this.orgs = baseOrgs;
            }
            // Try to read single organization
        } catch (MismatchedInputException me) {
            try {
                Organization org = mapper.readValue(Utils.substituteVariables(configFile), Organization.class);
                org = readOrganization(mapper, org, stageConfig, stage);
                this.orgs = new ArrayList<>();
                this.orgs.add(org);
            } catch (Exception pe) {
                throw new AppException("Cannot read organization(s) from config file: " + config, ErrorCode.ACCESS_ORGANIZATION_ERR, pe);
            }
        } catch (Exception e) {
            throw new AppException("Cannot read organization(s) from config file: " + config, ErrorCode.ACCESS_ORGANIZATION_ERR, e);
        }
        try {
            addImage(orgs, configFile.getCanonicalFile().getParentFile());
        } catch (Exception e) {
            throw new AppException("Cannot read image for organization(s) from config file: " + config, ErrorCode.ACCESS_ORGANIZATION_ERR, e);
        }
        addAPIAccess(orgs, result);
        validateCustomProperties(orgs);
    }

    private void addImage(List<Organization> orgs, File parentFolder) throws AppException {
        for (Organization org : orgs) {
            String imageUrl = org.getImageUrl();
            if (imageUrl == null || imageUrl.isEmpty()) continue;
            if (imageUrl.startsWith("data:")) {
                org.setImage(Image.createImageFromBase64(imageUrl));
            } else {
                org.setImage(Image.createImageFromFile(new File(parentFolder + File.separator + imageUrl)));
            }
        }
    }

    private void addAPIAccess(List<Organization> orgs, Result result) throws AppException {
        APIManagerAPIAdapter apiAdapter = APIManagerAdapter.getInstance().getApiAdapter();
        for (Organization org : orgs) {
            if (org.getApiAccess() == null) continue;
            Iterator<APIAccess> it = org.getApiAccess().iterator();
            while (it.hasNext()) {
                APIAccess apiAccess = it.next();
                List<API> apis = apiAdapter.getAPIs(new APIFilter.Builder()
                        .hasName(apiAccess.getApiName())
                        .build()
                    , false);
                if (apis == null || apis.isEmpty()) {
                    LOG.error("API with name: {} not found. Ignoring this APIs.", apiAccess.getApiName());
                    result.setError(ErrorCode.UNKNOWN_API);
                    it.remove();
                    continue;
                }
                if (apis.size() > 1 && apiAccess.getApiVersion() == null) {
                    LOG.error("Found: {} APIs with name: {} not providing a version. Ignoring this APIs.", apis.size(), apiAccess.getApiName());
                    result.setError(ErrorCode.UNKNOWN_API);
                    it.remove();
                    continue;
                }
                API api = apis.get(0);
                apiAccess.setApiId(api.getId());
            }
        }
    }

    private void validateCustomProperties(List<Organization> orgs) throws AppException {
        for (Organization org : orgs) {
            Utils.validateCustomProperties(org.getCustomProperties(), Type.organization);
        }
    }

    public Organization readOrganization(ObjectMapper mapper, Organization org, File stageConfig, String stage){
        if (stageConfig != null) {
            try {
                ObjectReader updater = mapper.readerForUpdating(org);
                return updater.readValue(Utils.substituteVariables(stageConfig));
            } catch (IOException e) {
                LOG.warn("No config file found for stage: {}", stage);
            }
        }
        return org;
    }
}
