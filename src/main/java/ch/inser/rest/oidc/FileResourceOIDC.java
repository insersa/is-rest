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
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.jose4j.jwt.JwtClaims;

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.rest.auth.ISSecurityException;
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
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * File upload and download with OIDC token
 */
@Path("/file")
@Api(value = "file")
public class FileResourceOIDC {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(FileResourceOIDC.class);

    /**
     * Le rest servlet context
     */
    @Context
    private ServletContext iContext;

    /**
     * Lien avec l'objet qui va traiter l'item
     *
     *
     * @param aId
     *            id de l'objet
     * @return ressource pour accéder à un objet métier donné
     */
    @Path("{id}")
    public FileItemResourceOIDC getFileItemResource(@PathParam("id") String aId) {
        return new FileItemResourceOIDC(iContext, aId);
    }

    /**
     * Upload a file
     *
     * @param aAuthorization
     *            Authorization Bearer token
     * @param aFiles
     *            liste de fichiers uploadé
     * @return 201 - succès
     */
    @ApiOperation(value = "Upload a new document")
    @ApiResponses(value = { @ApiResponse(code = 201, message = "OK"), @ApiResponse(code = 204, message = "No Content"),
            @ApiResponse(code = 400, message = "Error input parameters"), @ApiResponse(code = 401, message = "Error authenfication"),
            @ApiResponse(code = 500, message = "Error querying ") })
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadFile(@ApiParam(value = "Security token", required = true) @HeaderParam("Authorization") String aAuthorization,
            MultipartFormDataInput aFiles) {

        try {
            logger.debug("Upload new file");
            // -- Validate token
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null) {
                return Response.status(Status.BAD_REQUEST).build();
            }
            // Check the user authorizations
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, Entity.DOCUMENT.toString(), Verb.POST);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // Upload the file
            Long id = (Long) RestUtil.getBPDelegate(Entity.DOCUMENT.toString()).executeMethode("restFileUpload", aFiles, loggedUser);

            if (id == null || id < 0) {
                // Mauvais nom de fichier ou fichier trop grand
                if (id != null && id < -1) {
                    return Response.status(Status.BAD_REQUEST).build();
                }
                // Erreur générique
                return Response.status(Status.NO_CONTENT).build();
            }

            JsonObjectBuilder json = Json.createObjectBuilder().add("doc_id", id);

            return Response.status(Status.CREATED).entity(json.build().toString()).build();

        } catch (ISSecurityException e) {
            // -- Problème avec les token
            logger.warn("Erreur de login", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (Exception e) {
            // -- Tous les autres problèmes
            logger.error("Erreur", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * Lectures des informations pour les documents
     *
     * PAS ENCORE IMPLEMENTEE
     *
     * @param aToken
     *            sécurité pour accès à la fonction
     *
     * @return la liste des documents selon une query
     */
    @ApiOperation(value = "Get the permissions for the application")
    @ApiResponses(value = { @ApiResponse(code = 400, message = "Error input parameters") })
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Response getFilesInfo(@ApiParam(value = "Security token", required = true) @HeaderParam("token") String aToken) {
        if (aToken == null) {
            return Response.status(Status.BAD_REQUEST).build();
        }

        // PAS ENCORE IMPLEMENTEE
        return Response.status(Status.BAD_REQUEST).build();
    }

}
