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
import ch.inser.dynaplus.util.Constants.Mode;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.util.Constants;
import ch.inser.rest.util.Constants.Verb;
import ch.inser.rest.util.JsonVoUtil;
import ch.inser.rest.util.RestUtil;

import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Ressource pour initialiser un nouvel objet métier ("initVO") selon page: recherche, création etc.
 *
 *
 * @author INSER SA *
 */
@Path("/initobject")
@Api(value = "initobject")
public class InitObjectResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(InitObjectResource.class);

    /**
     * Le rest servlet context
     */
    @Context
    private ServletContext iContext;

    /**
     * @param aToken
     *            le token
     * @param aObjectName
     *            nom de l'objet métier
     * @param aMode
     *            'create' or 'search'
     * @return réponse HTTP avec un objet initiale selon mode
     */
    @ApiOperation(value = "Get an initial business object")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"),
            @ApiResponse(code = 500, message = "Error querying the init value object") })
    @Path("{objectname}/{mode}")
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Response getInitObject(@ApiParam(value = "Security token", required = true) @HeaderParam("token") String aToken,
            @PathParam("objectname") String aObjectName, @PathParam("mode") String aMode) {
        try {
            logger.debug("Get init object " + aObjectName + " en mode " + aMode);

            // -- Contrôle si paramètre présent
            Claims claims = RestUtil.getClaims(aToken, aObjectName);
            if (claims == null) {
                logger.warn("Erreur d'intialisation d'objet. Objectname" + aObjectName + ". Mode: " + aMode);
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
            json.add(Constants.RECORD,
                    JsonVoUtil.voToJson(RestUtil.getBPDelegate(aObjectName).getInitVO(Mode.valueOf(aMode), loggedUser),
                            Mode.valueOf(aMode).equals(Mode.create), loggedUser))
                    .add(Constants.TOKEN, SecurityUtil.getToken(claims, RestUtil.getContextManager()));
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
