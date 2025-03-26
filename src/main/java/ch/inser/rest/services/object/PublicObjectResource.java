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

package ch.inser.rest.services.object;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.core.AbstractResource;
import ch.inser.rest.util.Constants;
import ch.inser.rest.util.Constants.Verb;
import ch.inser.rest.util.JsonVoUtil;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Variante publique de ObjectResource
 *
 * @author INSER SA *
 */
public class PublicObjectResource extends AbstractResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(PublicObjectResource.class);

    /** Id de l'objet métier */
    private String iId;

    /**
     *
     * @param aContext
     *            Le rest servlet context
     * @param aObjectName
     *            nom de l'objet métier
     * @param aId
     *            Id de l'objet métier
     */
    public PublicObjectResource(ServletContext aContext, String aObjectName, String aId) {
        super(aContext, aObjectName);
        iId = aId;
    }

    /**
     * Get a public record as pdf (or json or other format)
     *
     * @param aIncludeChildren
     *            <code>true</code> to include children, by default true
     *
     * @param aFormat
     *            format, ex. pdf, json
     * @param aLang
     *            langue de l'enregistrement dans iso-lang-code ('fr', 'de')
     *
     * @return enregistrement de type {objectname} et avec id {id}
     */
    @ApiOperation(value = "Get business object by object name and id")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "The object id is undefined"),
            @ApiResponse(code = 400, message = "The object type, format or language is not supported"),
            @ApiResponse(code = 403, message = "No access permission"), @ApiResponse(code = 404, message = "Record not found"),
            @ApiResponse(code = 500, message = "Unexpected error while consulting record") })
    @GET
    public final Response getRecord(
            @ApiParam(value = "<code>true</code> to include children, by default true", required = false) @QueryParam("includeChildren") String aIncludeChildren,
            @ApiParam(value = "Data format of the record", required = false) @QueryParam("format") String aFormat,
            @ApiParam(value = "Language as iso-lang-code", required = false) @QueryParam("lang") String aLang) {
        try {
            logger.info("GET PUBLIC RECORD. Objectname: " + iObjectName + ", Id: " + iId + ", Format: " + aFormat);

            ILoggedUser loggedUser = SecurityUtil.getPublicUser((ServiceLocator) iContext.getAttribute("ServiceLocator"));
            RestUtil.addToNdc(loggedUser.getUsername());
            Response error = validateAccess(loggedUser, aFormat);
            if (error != null) {
                return error;
            }

            // Return record in specified format
            if ("pdf".equals(aFormat)) {
                return getPDF(iId, aLang, loggedUser);
            }

            // Get JSON object
            IValueObject rec = getRecord(iId, loggedUser).getValueObject();
            if (rec == null) {
                return Response.status(Status.NOT_FOUND).build();
            }

            // Build the response
            JsonObjectBuilder json = Json.createObjectBuilder();
            json.add(Constants.RECORD, JsonVoUtil.voToJson(rec, aIncludeChildren == null || Boolean.valueOf(aIncludeChildren), loggedUser));

            return Response.ok(json.build().toString()).build();

        } catch (Exception e) {
            logger.error("Erreur de récuperation de l'enregistrement publique", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * Valide les droits d'accès sur le service
     *
     * @param aUser
     *            utilisateur
     * @param aFormat
     *            format de données (pdf ou autre)
     * @return réponse erreur si id manque, le nom d'objet métier est non-permi, l'utilisateur n'a pas le droit sur l'objet, le format
     *         demandé est invalde
     */
    private Response validateAccess(ILoggedUser aUser, String aFormat) {
        if (iId == null || "undefined".equals(iId)) {
            logger.info("Le id n'est pas défini");
            return Response.status(Status.BAD_REQUEST).build();
        }

        if (!isPublicEntity()) {
            logger.info("Entité " + iObjectName + " n'est pas publique");
            return Response.status(Status.BAD_REQUEST).build();
        }

        if (aUser == null || !aUser.isAuthAction(iObjectName, Verb.GET.toString())) {
            logger.info("L'utilisateur publique n'a pas le droit de consulter entité: " + iObjectName);
            return Response.status(Status.FORBIDDEN).build();
        }

        if (aFormat != null && !"pdf".equalsIgnoreCase(aFormat)) {
            logger.info("Le format de retour demandé n'est pas valide: " + aFormat);
            return Response.status(Status.BAD_REQUEST).build();
        }
        return null;
    }
}
