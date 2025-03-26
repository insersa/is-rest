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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jose4j.jwt.JwtClaims;

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.core.AbstractCodeResource;
import ch.inser.rest.oidc.auth.SecurityUtilOIDC;
import ch.inser.rest.util.Constants.Verb;
import ch.inser.rest.util.JsonUtil;
import ch.inser.rest.util.RestUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Code resource with OIDC token authorization
 */
@Path("/codes")
@Api(value = "codes")
public class CodeResourceOIDC extends AbstractCodeResource {
    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(CodeResourceOIDC.class);

    /**
     * Get the codes.
     *
     * @param aAuthorization
     *            the Authorization "Bearer <token>"
     * @return the codes as JSON
     */
    @ApiOperation(value = "Get the codes of the application")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"),
            @ApiResponse(code = 500, message = "Error querying the codes tables") })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCodes(@HeaderParam("Authorization") String aAuthorization) {
        try {
            logger.debug("GET codes");

            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // Check the user authorizations
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, getEntity(), Verb.GET);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // Get the codes from the database
            Map<String, List<String>> codes = new HashMap<>();
            addCodes(codes, loggedUser);

            // Convert to JSON and return
            return Response.ok(JsonUtil.mapToJsonObject(codes).build().toString()).build();
        } catch (ISSecurityException e) {
            logger.warn("Security error", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            logger.error("Error", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

}
