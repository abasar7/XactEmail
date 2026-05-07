package com.xactsolutions.email.handler;

import com.xactsolutions.email.filter.AuthFilter;
import com.xactsolutions.email.maddy.MaddyServiceHelper;
import lombok.NonNull;

public class HealthHandler extends BaseRequestHandler {

    public HealthHandler(String endpoint, AuthFilter authFilter) {
        super(endpoint, authFilter);
    }

    @Override
    protected @NonNull Response list() {
        boolean status = MaddyServiceHelper.getServiceStatus();
        return new JsonResponse(status ? 200 : 500, null);
    }

}
