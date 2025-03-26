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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jose4j.jwt.JwtClaims;

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.core.AbstractFieldsInfoResource;
import ch.inser.rest.oidc.auth.SecurityUtilOIDC;
import ch.inser.rest.util.Constants.Verb;
import ch.inser.rest.util.RestUtil;

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
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Resource that provides the infos for initializing an input component. Authorization with OIDC Bearer token
 *
 * - Required, readonly, min, max, pattern (regular expression in JS syntax)
 */
@Path("/fieldinfos")
@Api(value = "fieldinfos")
public class FieldsInfoResourceOIDC extends AbstractFieldsInfoResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(FieldsInfoResourceOIDC.class);

    /**
     * @param aAuthorization
     *            Bearer OIDC token
     * @param aObjectName
     *            nom de l'objet métier
     * @return réponse HTTP avec les infos qui concernent l'utilisateur
     */
    @ApiOperation(value = "Get the permissions for the application")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"),
            @ApiResponse(code = 500, message = "Error querying the fields configuration") })
    @Path("{objectname}")
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Response getFieldInfos(@ApiParam(value = "Security token", required = true) @HeaderParam("Authorization") String aAuthorization,
            @PathParam("objectname") String aObjectName) {
        try {
            logger.debug("Get field infos " + aObjectName);

            // -- Contrôle si paramètre présent
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null || !RestUtil.isResource(aObjectName)) {
                logger.warn("Error initializing fields infos. Objectname " + aObjectName);
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, aObjectName, Verb.GET);
            // Contrôle métier
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // -- Récuperation des infos
            JsonObjectBuilder json = Json.createObjectBuilder();
            addFieldsInfo(aObjectName, json);
            return Response.ok(json.build().toString()).build();

        } catch (ISSecurityException e) {
            logger.warn("Token invalid", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            logger.error("Erreur : ", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }
}
