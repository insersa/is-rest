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

package ch.inser.rest.oidc;

import javax.cache.Cache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jose4j.jwt.JwtClaims;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.core.AbstractPropertiesResource;
import ch.inser.rest.oidc.auth.SecurityUtilOIDC;
import ch.inser.rest.util.JsonUtil;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Service to access the properties of the application needed by the front-end. Authentication with OIDC
 *
 * @author INSER SA */
@Path("/properties")
@Api(value = "properties")
public class PropertiesResourceOIDC extends AbstractPropertiesResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(PropertiesResourceOIDC.class);

    /**
     * Property name of flag that allows for resetting LoggedUser cache when calling this service (generally once at the beginning of a
     * session). Useful when user profiles are managed by an administrator and a profile possibly has been deleted. The cache then contains
     * that users obsolete user ID.
     */
    private static final String RESET_USER_CACHE_PROPERTY = "security.reset.user.cache";

    /**
     * Get the front-end properties.
     *
     * If there is no authorization token, only the public properties are provided
     *
     * @param aAuthorization
     *            with OIDC token all the tokens are provided, without token only a limited set
     *
     * @return the front-end properties
     */
    @ApiOperation(value = "Get the front-end properties")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "Unexpected error getting the properties") })
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Response getProperties(
            @ApiParam(value = "Security token", required = false) @HeaderParam("Authorization") String aAuthorization) {
        logger.debug("Get properties");

        try {
            IContextManager contextManager = ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getContextManager();

            // -- Contrôler si le client est logué
            boolean isFullData = validateToken(aAuthorization);
            return Response.ok(getProperties(isFullData, contextManager).build().toString()).build();
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
     * @param aIsFullData
     *            true if a valid token is provided
     * @param aContextManager
     *            the context manager
     * @return the properties to be returned by the service
     */
    protected JsonObjectBuilder getProperties(boolean aIsFullData, IContextManager aContextManager) {
        JsonObjectBuilder properties;

        String listProperties;
        if (aIsFullData) {
            // Properties for authenticated user
            listProperties = aContextManager.getProperty(FRONTEND_PROPERTIES);
            properties = JsonUtil.mapToJsonObject(aContextManager.getApplicationAboutMap());
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
     * @param aAuthorization
     *            Bearer token dOIDC
     * @return true si le token est valide
     * @throws ISSecurityException
     *             token invalide
     */
    private boolean validateToken(String aAuthorization) throws ISSecurityException {
        if (aAuthorization == null) {
            return false;
        }
        // Contrôle de sécurité
        JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization), RestUtil.getContextManager());
        RestUtil.addToNdc(claims);

        if (Boolean.TRUE.toString().equals(RestUtil.getContextManager().getProperty(RESET_USER_CACHE_PROPERTY))) {
            Cache<String, ILoggedUser> cache = RestUtil.getContextManager().getCacheManager().getCache("userCache", String.class,
                    ILoggedUser.class);
            JsonObject claimsJSON = JsonUtil.stringToJsonObject(claims.getRawJson());
            String username = !claimsJSON.isEmpty() ? claimsJSON.getString("preferred_username") : null;
            if (cache.containsKey(username)) {
                cache.remove(username);
            }
        }

        // Information si toutes les données sont à retourner
        return true;
    }
}
