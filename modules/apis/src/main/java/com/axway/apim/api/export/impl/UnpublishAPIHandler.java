package com.axway.apim.api.export.impl;

import java.util.List;

import com.axway.apim.adapter.APIStatusManager;
import com.axway.apim.adapter.apis.APIFilter;
import com.axway.apim.api.API;
import com.axway.apim.api.export.lib.params.APIExportParams;
import com.axway.apim.lib.errorHandling.ActionResult;
import com.axway.apim.lib.errorHandling.AppException;
import com.axway.apim.lib.utils.Utils;

public class UnpublishAPIHandler extends APIResultHandler {

	public UnpublishAPIHandler(APIExportParams params) {
		super(params);
	}

	@Override
	public ActionResult execute(List<API> apis) throws AppException {
		ActionResult result = new ActionResult();
		APIStatusManager statusManager = new APIStatusManager();
		System.out.println(apis.size() + " selected to unpublish.");
		if(Utils.askYesNo("Do you wish to proceed? (Y/N)")) {
			System.out.println("Okay, going to unpublish: " + apis.size() + " API(s)");
			for(API api : apis) {
				try {
					statusManager.update(api, API.STATE_UNPUBLISHED, true);
				} catch(Exception e) {
					LOG.error("Error unpublishing API: " + api.getName());
				}
			}
			System.out.println("Done!");
		}
		return result;
	}

	@Override
	public APIFilter getFilter() {
		return getBaseAPIFilterBuilder().build();
	}

}
