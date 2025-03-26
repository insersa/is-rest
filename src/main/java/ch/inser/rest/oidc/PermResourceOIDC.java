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
import ch.inser.rest.core.AbstractPermResource;
import ch.inser.rest.oidc.auth.SecurityUtilOIDC;
import ch.inser.rest.util.Constants;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Permission resource with OIDC authorization
 */
@Path("/permissions")
@Api(value = "permissions")
public class PermResourceOIDC extends AbstractPermResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(PermResourceOIDC.class);

    /**
     * @param aUser
     *            nom d'utilisateur
     * @param aAuthorization
     *            Bearer <token>
     * @return réponse HTTP avec les authorisations dans une arborecence JSON
     */
    @ApiOperation(value = "Get the permissions for the application")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"),
            @ApiResponse(code = 403, message = "The rights are not sufficient to access data"),
            @ApiResponse(code = 500, message = "Error querying the permissions tables") })
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Response getPermissions(@ApiParam(value = "Security token", required = true) @HeaderParam("Authorization") String aAuthorization,
            @QueryParam("user") String aUser) {
        try {
            logger.debug("Get permissions :" + aUser);

            if (aUser == null || aAuthorization == null) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurite
            // Contrôle de sécurité
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            ILoggedUser loggedUser = SecurityUtilOIDC.getUser(claims, (ServiceLocator) iContext.getAttribute(Constants.SERVICE_LOCATOR));

            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // -- L'information des permission est uniquement accessible par
            // l'utilisateur courant
            if (aUser.equals(loggedUser.getUsername())) {

                JsonObject permissions;
                if (loggedUser.getPermissions() == null) {
                    // Création de l'objet json vierge
                    JsonObjectBuilder json = Json.createObjectBuilder();
                    // Ajout des droits selon usergroup
                    // Droit sur menus
                    addMenus(loggedUser, json);
                    // Droit sur actions
                    addActions(loggedUser, json);
                    // Droit sur les champs
                    addFields(loggedUser, json);
                    permissions = json.build();
                    // Set des permissions
                    loggedUser.setPermissions(permissions);
                } else {
                    // -- Permission déjà existant prendre les informations de
                    // l'utilisateur
                    permissions = loggedUser.getPermissions();
                }

                return Response.ok(JsonUtil.jsonObjectToBuilder(permissions).build().toString()).build();
            }

            // -- Pas le droit à l'accès aux données
            return Response.status(Status.FORBIDDEN).build();

        } catch (ISSecurityException e) {
            // -- Problème avec les token
            logger.warn("Erreur de login", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            // -- Tous les autres problèmes
            logger.error("Erreur", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }
}
