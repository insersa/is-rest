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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.util.RestUtil;

import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;

/**
 * Fournir l'accès à des fichiers externes de l'application, uniquement la clé est fournit au service, le path et le nom du fichier se
 * trouvent dans le fichier properties.
 *
 * Attention ne pas utiliser le nom du fichier dans le path pour le nom fichier retourné au client
 *
 * La clé dans le fichier de properties est <code>document.external.files</code>
 *
 *
 * @author INSER SA *
 */
@Path("/documents")
@Api(value = "documents")
public class DocumentExternalResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(DocumentExternalResource.class);

    /**
     * Le rest servlet context
     */
    @Context
    private ServletContext iContext;

    /**
     *
     * Demande d'un fichier extérieur par l'utilisateur
     *
     * ATTENTION pas sécurisé au niveau de la sécurité db (menu_item) a priori, tout utilisateur authentifié a le droit d'accéder à ces
     * fichiers.
     *
     * @param aToken
     *            Sécurité pour l'accès à cette fonction
     * @param aKey
     *            la clé pour l'accès au fichier, uniquement le fichier properties connait le PATH du fichier
     *
     * @return fichier
     */
    @ApiOperation(value = "Get external file from a key")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"), @ApiResponse(code = 404, message = "Not found record"),
            @ApiResponse(code = 500, message = "Unexpected error getting the properties") })
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getDocument(@ApiParam(value = "Security token", required = true) @HeaderParam("token") String aToken,
            @QueryParam("key") String aKey) {

        try {
            logger.debug("Get document " + aKey);

            // -- Contrôle si paramètre présent
            if (aToken == null) {
                logger.warn("Token missing");
                return Response.status(Status.BAD_REQUEST).build();
            }

            // Check the token
            Claims claims = RestUtil.getClaims(aToken);

            if (aKey == null) {
                logger.warn("Document key missing");
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Rechercher path et nom du fichier
            return getExternalDocument(aKey, claims);

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

    /**
     *
     * @param aKey
     *            clé du document externe
     * @param aClaims
     *            les infos de l'utilisateur authentifié reçu par token
     * @return réponse HTTP avec le document comme un input stream
     * @throws ISException
     *             erreur de lecture du document
     */
    private Response getExternalDocument(String aKey, Claims aClaims) throws ISException {
        String[] files = RestUtil.getContextManager().getProperty("document.external.files").split(",");

        String filepath = null;
        String filename = null;
        String contentType = null;

        for (int i = 0; i < files.length; i = i + 4) {
            if (files[i].equals(aKey)) {
                filepath = files[i + 1];
                filename = files[i + 2];
                contentType = files[i + 3];
                break;
            }
        }

        // -- Envoyer le fichier au client
        if (filepath != null) {
            File file = new File(filepath);
            if (file.exists()) {
                try {
                    ResponseBuilder response = Response.ok(new FileInputStream(file));
                    response.header("Content-type", contentType);
                    response.header("Content-Disposition", "attachment;filename=\"" + filename + "\"");
                    response.header("token", SecurityUtil.getToken(aClaims, RestUtil.getContextManager()));
                    return response.build();
                } catch (IOException e) {
                    throw new ISException("Problème de lecture de documente: " + filename, e);
                }

            }
        }
        return Response.status(Status.NOT_FOUND).build();
    }

}
