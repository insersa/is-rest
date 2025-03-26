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
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.core.AbstractFileItemResource;
import ch.inser.rest.core.IBPDelegate;
import ch.inser.rest.oidc.auth.SecurityUtilOIDC;
import ch.inser.rest.util.Constants.Verb;
import ch.inser.rest.util.RestUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
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
 * Download and deletion of a file with OIDC authorization
 */
@Api(value = "{id}")
public class FileItemResourceOIDC extends AbstractFileItemResource {

    /** Logger */
    private static final Log logger = LogFactory.getLog(FileItemResourceOIDC.class);

    /**
     * Constructor called by FileResource
     *
     * @param aContext
     *            rest servlet context
     * @param aId
     *            id document id
     */
    public FileItemResourceOIDC(ServletContext aContext, String aId) {
        super(aContext, aId);
    }

    /**
     * Download a file
     *
     * @param aAuthorization
     *            Authorization bearer token OIDC
     *
     * @param aObjName
     *            name of business object, used for example if the file is a blob in the objects table instead of in the table of documents
     * @param aFieldName
     *            name of blob field, if the document is in the business object table
     * @return the document
     */
    @ApiOperation(value = "Download a document")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"), @ApiResponse(code = 404, message = "Not found record"),
            @ApiResponse(code = 500, message = "Error querying ") })
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadFile(
            @ApiParam(value = "Security token in the header", required = false) @HeaderParam("Authorization") String aAuthorization,
            @ApiParam(value = "Object name", required = false) @QueryParam("objectname") String aObjName,
            @ApiParam(value = "Field name", required = false) @QueryParam("fieldname") String aFieldName) {

        try {
            logger.debug("GET FILE : " + iId + (aObjName != null ? ", ObjectName: " + aObjName : ""));

            // -- Validate token
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null || iId == null) {
                logger.error("Erreur download file. Id: " + iId + (aObjName != null ? ", ObjectName: " + aObjName : ""));
                return Response.status(Status.BAD_REQUEST).build();
            }
            // -- Validate access rights
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
     * Delete a file that hasn't yet been attached to a business object
     *
     * @param aAuthorization
     *            sAuthorization bearer token OIDC
     *
     * @return 200 - OK
     */
    @ApiOperation(value = "Remove the file")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"), @ApiResponse(code = 404, message = "Not found record"),
            @ApiResponse(code = 500, message = "Error querying ") })
    @DELETE
    public Response removeFile(@ApiParam(value = "Security token", required = true) @HeaderParam("Authorization") String aAuthorization) {

        try {
            logger.debug("Delete File : " + iId);
            // -- Validate token
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null || iId == null) {
                logger.error("Erreur de suppression de fichier. Id: " + iId);
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, Entity.DOCUMENT.toString(), Verb.DELETE);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // Si id pas un nombre, un internal server error est retourné
            Long id = Long.valueOf(iId);

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
                return Response.ok().build();
            }
            return Response.status(Status.BAD_REQUEST).build();

        } catch (ISSecurityException e) {
            // -- Problème avec les token
            logger.warn("Authorization error", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            // -- Tous les autres problèmes
            logger.error("Error deleting file. Id: " + iId, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

}
