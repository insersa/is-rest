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

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.core.AbstractUserInfoResource;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;

import io.jsonwebtoken.Claims;
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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Des attributs spéciales de l'utilisateur pour des filtres de recherches et cockpit
 *
 * Cette ressource n'est accessible qu'en lecture, et seuls les infos de l'utilisteur demandeur sont fournis. La vérification s'effectue en
 * vérifiant le nom de l'utilisateur trouvé dans le token et le nom d'utilisateur passé en paramètre.
 *
 * Service d'enquête, commune, canton, news url
 *
 * @author INSER SA *
 */
@Path("/userinfos")
@Api(value = "userinfos")
public class UserInfoResource extends AbstractUserInfoResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(UserInfoResource.class);

    /**
     * @param aUser
     *            nom d'utilisateur
     * @param aToken
     *            le token
     * @param aRefresh
     *            flag pour rafraîchir le loggeduser dans le cache avant de retourner les infos
     * @return réponse HTTP avec les infos qui concernent l'utilisateur
     */
    @ApiOperation(value = "Get the permissions for the application")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"),
            @ApiResponse(code = 403, message = "The rights are not sufficient to access data"),
            @ApiResponse(code = 500, message = "Error querying the permissions tables") })
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Response getUserInfos(@ApiParam(value = "Security token", required = true) @HeaderParam("token") String aToken,
            @QueryParam("user") String aUser, @QueryParam("refresh") String aRefresh) {
        try {
            logger.debug("Get infos " + aUser);

            // -- Contrôle si paramètre présent
            Claims claims = RestUtil.getClaims(aToken);
            if (aUser == null || claims == null) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = SecurityUtil.getUser(claims, (ServiceLocator) iContext.getAttribute("ServiceLocator"));

            if (!aUser.equals(loggedUser.getUsername())) {
                logger.warn("Not autorized to access the userinfo of: " + aUser);
                return Response.status(Status.FORBIDDEN).build();
            }

            JsonObjectBuilder infos = Json.createObjectBuilder();
            if (Boolean.TRUE.toString().equals(RestUtil.getContextManager().getProperty("security.inituser.claims"))) {
                addInfo(claims, infos);
            } else {
                addInfo(getLoggedUser(aUser, Boolean.TRUE.toString().equals(aRefresh)), infos);
            }

            infos.add("token", SecurityUtil.getToken(claims, RestUtil.getContextManager()));

            return Response.ok(infos.build().toString()).build();

        } catch (ISSecurityException e) {
            // -- Problème avec les token
            logger.warn("Erreur de login. User: " + aUser, e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            // -- Tous les autres problèmes
            logger.error("Erreur. User: " + aUser, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * Ajoute les infos du rôle
     *
     * @param aClaims
     *            Attribut de l'user qui vient du token
     * @param aJson
     *            données de permission
     * @throws ISException
     *             erreur de consultation de service d'enquête
     */
    @SuppressWarnings("unused")
    protected void addInfo(Claims aClaims, JsonObjectBuilder aJson) throws ISException {
        // Implémenter dans la classe spécialisée métier, selon besoin
    }

}
