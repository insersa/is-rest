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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.core.AbstractTranslateResource;
import ch.inser.rest.util.JsonUtil;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;

import io.jsonwebtoken.Claims;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
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
 * Resource for multi-language texts from T_HELP_KEY, T_HELP_TEXT and T_CODE and T_CODETEXT.
 *
 * @author INSER SA */
@Path("/translate")
@Api(value = "translate")
public class TranslateResource extends AbstractTranslateResource {

    /**
     * The logger.
     */
    private static final Log logger = LogFactory.getLog(TranslateResource.class);

    /**
     * Get the labels for a given language. If the language is <code>"empty"</code> an empty response is returned.
     *
     * Avec token, toutes les traductions sont fournies, sans token uniquement les labels (pas les codes)
     *
     * Voir si on a besoin d'aller plus loin dans le futur
     *
     * @param aLang
     *            the language
     * @param aToken
     *            token de l'utilisateur
     * @return HTTP response containing the labels in a JSON tree
     */
    @ApiOperation(value = "Get the labels of the application")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 400, message = "Error querying the multilingual tables") })
    @GET
    @Path("{lang}")
    @Produces(MediaType.APPLICATION_JSON + "; charset=UTF-8")
    public Response getLabels(@ApiParam(value = "Security token", required = false) @HeaderParam("token") String aToken,
            @PathParam("lang") String aLang) {

        logger.debug("Get labels " + aLang);

        boolean fullData = false;
        String newToken = null;

        // -- Contrôler si le client est logger
        if (aToken != null) {
            try {
                // Contrôle de sécurité
                IContextManager contextManager = ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getContextManager();
                // Check the token
                Claims claims = RestUtil.getClaims(aToken);

                // Information si toutes les données sont à retourner
                fullData = true;

                // Prendre l'information sur le nouveau token
                newToken = SecurityUtil.getToken(claims, contextManager);

            } catch (ISSecurityException e) {
                logger.debug("Translate limited", e);
            }
        }

        // -- Rechercher les traductions
        try {
            Map<String, Object> labels = new HashMap<>();
            JsonObjectBuilder json;
            // If the language is different from "empty"
            if (!"empty".equals(aLang)) {
                ILoggedUser superUser = ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getSuperUser();

                // Les textes sont fournis à tous les clients
                addHelpTexts(aLang, labels, superUser);

                // Les codes sont fournis uniquement si token disponible
                if (fullData) {
                    addCodes(aLang, labels, superUser);
                }
            }
            json = JsonUtil.mapToJsonObject(labels);
            if (fullData) {
                json = json.add("token", newToken);
            }
            return Response.ok(json.build().toString()).build();
        } catch (ISException e) {
            logger.error("Erreur de recherche des libellés.", e);
            return Response.status(Status.BAD_REQUEST).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }
}
