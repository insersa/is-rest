/*
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.
 */

package ch.inser.rest.services;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.core.AbstractPropertiesResource;
import ch.inser.rest.util.JsonUtil;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Service to access to the properties of the application needed by the front-end.
 *
 * @author INSER SA */
@Path("/properties")
@Api(value = "properties")
public class PropertiesResource extends AbstractPropertiesResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(PropertiesResource.class);

    /**
     * Get the front-end properties.
     *
     * Avant réception de token, les properties limités sont fournis à l'application.
     *
     * @param aToken
     *            avec token toutes les properties, sans token liste limité de properties
     *
     * @return the front-end properties
     */
    @ApiOperation(value = "Get the front-end properties")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Unexpected error getting the properties") })
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Response getProperties(@ApiParam(value = "Security token", required = false) @HeaderParam("token") String aToken) {
        logger.debug("Get properties");

        try {

            IContextManager contextManager = ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getContextManager();

            // -- Contrôler si le client est logué
            String newToken = validateToken(aToken);

            return Response.ok(getProperties(newToken, contextManager).build().toString()).build();
        } catch (Exception e) {
            logger.error("Exception getting the front-end properties", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * Get the properties.
     *
     * @param aToken
     *            the new token to check the user and add it to the properties, <code>null</code> if the user is not authenticated
     * @param aContextManager
     *            the context manager
     * @return the properties to be returned by the service
     */
    protected JsonObjectBuilder getProperties(String aToken, IContextManager aContextManager) {
        JsonObjectBuilder properties;

        String listProperties;
        if (aToken != null) {
            // Properties for authenticated user
            listProperties = aContextManager.getProperty(FRONTEND_PROPERTIES);
            properties = JsonUtil.mapToJsonObject(aContextManager.getApplicationAboutMap());
            properties.add("token", aToken);
        } else {
            // Properties for non-authenticated user
            listProperties = aContextManager.getProperty(FRONTEND_PROPERTIES_FREE);
            properties = Json.createObjectBuilder();
        }

        if (listProperties != null) {
            String value;
            for (String key : listProperties.split(",")) {
                value = aContextManager.getProperty(key);
                if (value != null) {
                    properties.add(key, value);
                }
            }
        }

        return properties;
    }

    /**
     * Valide le token de sécurité pour déterminer s'il faut charger la totalité des propriétés
     *
     * @param aToken
     *            token de sécurité
     * @return nouveau token, ou null si l'ancien n'est pas valide
     */
    private String validateToken(String aToken) {
        if (aToken == null) {
            return null;
        }
        IContextManager ctx = RestUtil.getContextManager();
        try {
            // Contrôle de sécurité
            // Check the token
            Claims claims = RestUtil.getClaims(aToken);

            // Prendre l'information sur le nouveau token
            return SecurityUtil.getToken(claims, ctx);

        } catch (ISSecurityException e) {
            if (e.getCause() instanceof ExpiredJwtException) {
                logger.warn("Login exception", e);
            } else {
                logger.error("Login exception", e);
            }
            return null;
        }
    }
}
