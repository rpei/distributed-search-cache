package com.maxent.dscache.api.rest.controller;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;

/**
 * Created by alain on 16/8/17.
 */
@Path("/cache")
public class CacheController {

    @POST
    @Path("")
    public String create(@Context final HttpServletResponse httpServletResponse,
                       final String request) {
        return "create " + request;
    }

    @POST
    @Path("{cache_id}/save")
    public String save(@Context final HttpServletResponse httpServletResponse,
                       final String request) {
        return "save " + request;
    }

    @POST
    @Path("/match")
    public String match(@Context final HttpServletResponse httpServletResponse,
                        final String request) {
        return "match " + request;
    }
}
