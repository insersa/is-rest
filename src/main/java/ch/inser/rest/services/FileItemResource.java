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
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.core.AbstractFileItemResource;
import ch.inser.rest.core.IBPDelegate;
import ch.inser.rest.util.Constants.Verb;
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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Gestion de l'item d'un fichier pour l'upload et le download
 *
 * @author INSER SA *
 */

@Api(value = "{id}")
public class FileItemResource extends AbstractFileItemResource {

    /** Logger */
    private static final Log logger = LogFactory.getLog(FileItemResource.class);

    /**
     * Constructeur appelé par FileResource
     *
     * @param aContext
     *            context du service
     * @param aId
     *            id de la resource à traiter
     */
    public FileItemResource(ServletContext aContext, String aId) {
        super(aContext, aId);
    }

    /**
     * Télécharge un fichier
     *
     * @param aHeaderToken
     *            token de sécurité avec userId, userName etc. fourni comme paramètre de header
     *
     * @param aToken
     *            token de sécurité fourni comme paramètre de url.
     * @param aObjName
     *            name of business object, used for example if the file is a blob in the objects table instead of in the table of documents
     * @param aFieldName
     *            name of blob field, if the document is in the business object table
     * @return le document
     */
    @ApiOperation(value = "Download a document")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"), @ApiResponse(code = 404, message = "Not found record"),
            @ApiResponse(code = 500, message = "Error querying ") })
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadFile(
            @ApiParam(value = "Security token in the header", required = false) @HeaderParam("token") String aHeaderToken,
            @ApiParam(value = "Security token", required = false) @QueryParam("token") String aToken,
            @ApiParam(value = "Object name", required = false) @QueryParam("objectname") String aObjName,
            @ApiParam(value = "Field name", required = false) @QueryParam("fieldname") String aFieldName) {

        try {
            logger.debug("GET FILE : " + iId + (aObjName != null ? ", ObjectName: " + aObjName : ""));
            Claims claims = RestUtil.getClaims(aToken != null ? aToken : aHeaderToken, Entity.DOCUMENT.toString());

            // -- Contrôle si paramètre présent
            if (claims == null || iId == null) {
                logger.error("Erreur download file. Id: " + iId + (aObjName != null ? ", ObjectName: " + aObjName : ""));
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, Entity.DOCUMENT.toString(), Verb.GET);
            if (loggedUser == null || aObjName != null && !loggedUser.isAuthAction(aObjName, Verb.GET.toString())) {
                return Response.status(Status.FORBIDDEN).build();
            }
            return downloadFile(loggedUser, aObjName, aFieldName);

        } catch (ISSecurityException e) {
            // -- Problème avec les token
            logger.warn("Erreur de login", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            // -- Tous les autres problèmes
            logger.error("Erreur", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }

    }

    /**
     *
     * Supppression d'un fichier uniquement si fichier pas lié à un objet métier
     *
     * @param aToken
     *            sécurité pour accès au service
     *
     * @return 200 - OK
     */
    @ApiOperation(value = "Remove the file")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"), @ApiResponse(code = 404, message = "Not found record"),
            @ApiResponse(code = 500, message = "Error querying ") })
    @DELETE
    public Response removeFile(@ApiParam(value = "Security token", required = true) @HeaderParam("token") String aToken) {

        try {
            logger.debug("Delete File : " + iId);

            // -- Contrôle si paramètre présent
            Claims claims = RestUtil.getClaims(aToken, Entity.DOCUMENT.toString());
            if (claims == null || iId == null) {
                logger.error("Erreur de suppression de fichier. Id: " + iId);
                return Response.status(Status.BAD_REQUEST).build();
            }

            // Si id pas un nombre, un internal server error est retourné
            Long id = Long.valueOf(iId);

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, Entity.DOCUMENT.toString(), Verb.DELETE);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // -- Supprimer le fichier (uniquement si objet pas lié à un objet
            // métier
            IBPDelegate bp = RestUtil.getBPDelegate(ch.inser.dynaplus.util.Constants.Entity.DOCUMENT.toString());
            IValueObject rec = bp.getRecord(id, loggedUser).getValueObject();

            // Si le fichier n'existe pas ou si il est dans les fichier temp de
            // l'upload, on retourne not found
            if (rec == null || !"UPLOAD".equals(rec.getProperty("doc_obj_name"))) {
                return Response.status(Status.NOT_FOUND).build();
            }

            int retDel = (Integer) bp.executeMethode("restFileDeleteTemp", rec, loggedUser);
            if (retDel == 1) {
                JsonObjectBuilder json = Json.createObjectBuilder().add("token",
                        SecurityUtil.getToken(claims, RestUtil.getContextManager()));

                return Response.ok().entity(json.build().toString()).build();
            }

            return Response.status(Status.BAD_REQUEST).build();

        } catch (ISSecurityException e) {
            // -- Problème avec les token
            logger.warn("Erreur de login", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            // -- Tous les autres problèmes
            logger.error("Erreur de suppression de fichier. Id: " + iId, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

}
