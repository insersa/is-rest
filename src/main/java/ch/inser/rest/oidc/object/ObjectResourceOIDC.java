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

package ch.inser.rest.oidc.object;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jose4j.jwt.JwtClaims;

import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.annotation.PATCH;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.core.AbstractObjectResource;
import ch.inser.rest.oidc.auth.SecurityUtilOIDC;
import ch.inser.rest.util.Constants;
import ch.inser.rest.util.Constants.Verb;
import ch.inser.rest.util.RestUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Ressource pour accéder à un enregistrement d'un objet métier avec authentification OIDC
 *
 * Les actions:
 *
 * 1. Consultation de l'enregistrement (GET)
 *
 * 2. Modification en donnant l'objet complèt (PUT)
 *
 * 3. Modification en donnant les champs à modifier (PATCH)
 *
 * 4. Suppression (DELETE)
 *
 * @author INSER SA *
 */
@Api(value = "{id}")
public class ObjectResourceOIDC extends AbstractObjectResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(ObjectResourceOIDC.class);

    /**
     *
     * @param aContext
     *            Le rest servlet context
     * @param aObjectName
     *            nom de l'objet métier
     * @param aId
     *            Id de l'objet métier
     */
    public ObjectResourceOIDC(ServletContext aContext, String aObjectName, String aId) {
        super(aContext, aObjectName, aId);
    }

    /**
     * Get a record
     *
     * @param aAuthorization
     *            token de sécurité OIDC: Authorization: "Bearer <token>"
     * @param aIncludeChildren
     *            Flag include children. By default true.
     * @param aFormat
     *            format, ex. pdf. by default json
     * @param aLang
     *            Language if format pdf, by default german
     * @return enregistrement de type {objectname} et avec id {id}
     */
    @ApiOperation(value = "Get business object by object name and id")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 400, message = "The id of the record is undefined"),
            @ApiResponse(code = 401, message = "Consultation not authorized for this user"),
            @ApiResponse(code = 403, message = "No access permission"), @ApiResponse(code = 404, message = "Business object not found"),
            @ApiResponse(code = 500, message = "Unexpected error while consulting record"), })
    @GET
    public Response getRecord(@ApiParam(value = "Authorization", required = true) @HeaderParam("Authorization") String aAuthorization,
            @ApiParam(value = "Flag include children. By default true", required = false) @QueryParam("includeChildren") String aIncludeChildren,
            @ApiParam(value = "Data format of the record (pdf, json). By default json", required = false) @QueryParam("format") String aFormat,
            @ApiParam(value = "Language as iso-lang-code (fr,it,de,ro). By default de", required = false) @QueryParam("lang") String aLang) {

        try {
            logger.debug("GET - ObjectName : " + iObjectName + ", ID : " + iId);

            // -- Contrôle si paramètre présent
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null || iId == null || "undefined".equals(iId) || iObjectName == null || !RestUtil.isResource(iObjectName)) {
                logger.error("Erreur getRecord. Type: " + iObjectName + ", id:" + iId);
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, iObjectName, Verb.GET);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // Return record in specified format
            if ("pdf".equals(aFormat)) {
                return getPDF(iId, aLang, loggedUser);
            }

            // -- Get JSON object
            IValueObject rec = getRecord(iId, loggedUser).getValueObject();
            if (rec == null) {
                return Response.status(Status.NOT_FOUND).build();
            }

            // Build the response
            JsonObjectBuilder json = Json.createObjectBuilder();
            json.add(Constants.RECORD, voToJson(rec, aIncludeChildren, loggedUser));

            return Response.ok(json.build().toString()).build();

        } catch (ISSecurityException e) {
            logger.warn("Token invalide", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            logger.error("Erreur de consultation de l'objet. Objectname: " + iObjectName + ". Id: " + iId, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }

    }

    /**
     * Met à jour l'enregistrement
     *
     * @param aAuthorization
     *            token de sécurité OIDC: Authorization: "Bearer <token>"
     *
     * @param aJsonRecord
     *            l'enregistrement en string json
     *
     * @return statut 200 (ok) ou un statut erreur
     */
    @ApiOperation(value = "Update business object")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 304, message = "No modification"),
            @ApiResponse(code = 400, message = "Error updating business object"),
            @ApiResponse(code = 401, message = "Update not authorized for this user"),
            @ApiResponse(code = 403, message = "No access permission"), @ApiResponse(code = 404, message = "Business object not found"),
            @ApiResponse(code = 409, message = "Business object is not up-to-date with respect to the record in the database.") })
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    public final Response update(@ApiParam(value = "Security token", required = true) @HeaderParam("Authorization") String aAuthorization,
            @ApiParam(value = "Business object attributes. Example: {\"xxx_field1\":value1,\"xxx_field2\":value2}", required = true) String aJsonRecord) {

        try {
            logger.debug("PUT - ObjectName : " + iObjectName + ", aJsonRecord : " + aJsonRecord);

            // -- Contrôle si paramètre présent
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null || aJsonRecord == null || "undefined".equals(iId) || iObjectName == null
                    || !RestUtil.isResource(iObjectName)) {
                logger.error("Erreur update. Type: " + iObjectName + ", id:" + iId);
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, iObjectName, Verb.PUT);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // Valider les données
            IValueObject vo = jsonToVo(aJsonRecord);
            if (!isValidRequired(vo) || !isValidInput(vo)) {
                return Response.status(Status.BAD_REQUEST).build();
            }
            // -- Update the object
            IDAOResult result = update(vo, loggedUser);

            // Check the errors
            Response error = RestUtil.getErrorResponse(result);
            if (error != null) {
                return error;
            }

            // Build the response
            JsonObjectBuilder jsonB = Json.createObjectBuilder();
            if (result.getValueObject() != null) {
                jsonB.add(Constants.RECORD, voToJson(result.getValueObject(), Boolean.TRUE.toString(), loggedUser));
            }
            JsonObject json = jsonB.build();
            if (json.get(Constants.RECORD) != null) {
                return Response.ok(json.toString()).build();
            }
            return Response.ok().build();
        } catch (ISSecurityException e) {
            logger.warn("Invalid token", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (Exception e) {
            logger.error("Erreur de mise à jour d'enregistrement: " + aJsonRecord, e);
            return Response.status(Status.BAD_REQUEST).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * Fait une mise à jour partielle selon un patch
     *
     * @param aAuthorization
     *            json web token avec timeout
     *
     * @param aPatch
     *            tableau d'instructions de mise à jour selon standard Json Patch
     *
     * @return statut 200 (ok) ou un statut erreur
     */
    @ApiOperation(value = "Update business object")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 304, message = "No modification, forbidden."),
            @ApiResponse(code = 400, message = "Error updating business object"),
            @ApiResponse(code = 401, message = "Update not authorized for this user"),
            @ApiResponse(code = 403, message = "No access permission"), @ApiResponse(code = 404, message = "Business object not found"),
            @ApiResponse(code = 409, message = "Business object is not up-to-date with respect to the record in the database.") })
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    public final Response updateFields(@HeaderParam("Authorization") String aAuthorization,
            @ApiParam(value = "Array of patch instructions. Example: {\"op\":\"replace\",\"path\":nom_du_champ, \"value\": nouvelle_valeur}", required = true) String aPatch) {
        logger.debug("PATCH - ObjectName : " + iObjectName + ", iId : " + iId + ", aJsonPatch : " + aPatch);

        try {

            // -- Contrôle si paramètre présent
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null || aPatch == null || "undefined".equals(iId) || iObjectName == null || !RestUtil.isResource(iObjectName)) {
                logger.error("Erreur patch. Type: " + iObjectName + ", id:" + iId);
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, iObjectName, Verb.PATCH);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // -- Prepare update fields
            IValueObject vo = jsonPatchToVo(Long.valueOf(iId), aPatch);
            if (!isValidInput(vo)) {
                return Response.status(Status.BAD_REQUEST).build();
            }
            IDAOResult result = updateFields(vo, loggedUser);
            Response error = RestUtil.getErrorResponse(result);
            if (error != null) {
                return error;
            }

            // Build the response
            JsonObjectBuilder jsonB = Json.createObjectBuilder();
            if (result.getValueObject() != null) {
                jsonB.add(Constants.RECORD, voToJson(result.getValueObject(), Boolean.TRUE.toString(), loggedUser));
            }
            JsonObject json = jsonB.build();
            if (json.get(Constants.RECORD) != null) {
                return Response.ok(json.toString()).build();
            }
            return Response.ok().build();
        } catch (ISSecurityException e) {
            logger.warn("Token invalid", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (Exception e) {
            logger.error("Erreur de mise à jour partielle d'enregistrement: " + aPatch, e);
            return Response.status(Status.BAD_REQUEST).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * Supprime l'enregistrement
     *
     * @param aAuthorization
     *            token de sécurité OIDC: Authorization: "Bearer <token>"
     *
     * @param aRecord
     *            string json avec le champ timestamp
     *
     * @return statut 200,400 ou 404
     */
    @ApiOperation(value = "Delete business object")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Deletion not authorized for this user"),
            @ApiResponse(code = 404, message = "Business object not found"),
            @ApiResponse(code = 500, message = "Error deleting business object") })
    @DELETE
    public Response delete(@ApiParam(value = "Security token", required = true) @HeaderParam("Authorization") String aAuthorization,
            @ApiParam(value = "Json expression with timestamp. Example: {\"abc_update_date\":\"2016-06-09 08:52:53.85\"}", required = true) String aRecord) {

        try {
            logger.debug("DELETE - ObjectName : " + iObjectName + ",aRecord : " + aRecord + ", Id: " + iId);

            // -- Contrôle si paramètre présent
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            if (claims == null || aRecord == null || "undefined".equals(iId) || iObjectName == null || !RestUtil.isResource(iObjectName)) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, iObjectName, Verb.DELETE);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // -- Delete the object
            IDAOResult result = delete(jsonToVo(aRecord), loggedUser);
            if (result.isStatusNOTHING_TODO()) {
                return Response.status(Status.NOT_FOUND).build();
            }
            if (!result.isStatusOK()) {
                // Possibilité de donner une autre raison propre dans la classe
                // surchargée (sous value), sans provoquer d'erreur dans le log
                // par exemple pour implémenter un NOT_AUTHORIZED spécifique
                if (result.getValue() instanceof Response) {
                    return (Response) result.getValue();
                }
                logger.error("Création retourne id null. record: " + aRecord);
                return Response.status(Status.BAD_REQUEST).build();
            }
            return Response.ok().build();
        } catch (ISSecurityException e) {
            logger.warn("Token invalid", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            logger.error("Erreur de suppression d'enregistrement. Id:" + iId + ". Record:" + aRecord, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

}
