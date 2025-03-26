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

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jose4j.jwt.JwtClaims;

import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynamic.util.VOInfo;
import ch.inser.dynaplus.format.IFormatEngine.Format;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.core.AbstractObjectNamesResource;
import ch.inser.rest.oidc.auth.SecurityUtilOIDC;
import ch.inser.rest.services.object.ObjectNamesResource;
import ch.inser.rest.util.Constants.Verb;
import ch.inser.rest.util.JsonVoUtil;
import ch.inser.rest.util.RestUtil;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Ressource pour accéder à une collection d'objets métier d'un type donné. Authentification OIDC
 *
 * Recherche (GET), création (POST)
 *
 * @author INSER SA *
 */
@Api(value = "{objectname}")
public class ObjectNamesResourceOIDC extends AbstractObjectNamesResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(ObjectNamesResource.class);

    /**
     *
     * @param aContext
     *            Le rest servlet context
     * @param aObjectName
     *            Nom de l'objet métier
     */
    public ObjectNamesResourceOIDC(ServletContext aContext, String aObjectName) {
        super(aContext, aObjectName);
    }

    /**
     * Recherche des objets métier
     *
     * @param aAuthorization
     *            token de sécurité OIDC: Authorization: "Bearer <token>"
     *
     * @param aQuery
     *            expression json avec les critères de recherche
     * @param aSortFields
     *            tri ascending sur les champs donnés
     * @param aDescFields
     *            tri descending sur les champs donnés
     * @param aRange
     *            trance a retourner
     * @param aFieldname
     *            nom du champ pour recevoir une liste de valeurs au lieu d'enregistrements
     * @param aFormat
     *            format de output, ex. "csv". Par défaut json.
     * @param aLang
     *            Language of the formatted list
     * @param aFields
     *            noms de champs à inclure dans le résultat (csv)
     * @param aLabelKeys
     *            les clés pour les entêtes du résultat (csv)
     *
     *
     * @return la liste des objets métier recherchés pour un objectname donné
     */
    @ApiOperation(value = "Get business objects by object name")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"), @ApiResponse(code = 403, message = "No access permission"),
            @ApiResponse(code = 500, message = "Error in the query")

    })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getList(@ApiParam(value = "Security token", required = true) @HeaderParam("Authorization") String aAuthorization,
            @ApiParam(value = "Filter criteria as an expression json", required = false) @QueryParam("query") String aQuery,
            @ApiParam(value = "Names of fields for ascending sorting", required = false) @QueryParam("sort") String aSortFields,
            @ApiParam(value = "Names of fields for descending sorting", required = false) @QueryParam("desc") String aDescFields,
            @ApiParam(value = "Request a partial result. Ex. range=1-10", required = false) @QueryParam("range") String aRange,
            @ApiParam(value = "Request not the whole records, but only one field", required = false) @QueryParam("fieldname") String aFieldname,
            @ApiParam(value = "Format, ex. 'csv'. By default json.", required = false) @QueryParam("format") String aFormat,
            @ApiParam(value = "Language of the formatted list", required = false) @QueryParam("language") String aLang,
            @ApiParam(value = "Names of fields to include in the result", required = false) @QueryParam("fields") String aFields,
            @ApiParam(value = "Names of label keys for column headers", required = false) @QueryParam("labelkeys") String aLabelKeys) {

        try {
            logger.debug("GET - ObjectName : " + iObjectName + ", query : " + aQuery + ", sort : " + aSortFields + ", desc : " + aDescFields
                    + ", range : " + aRange);
            if (aFormat != null) {
                logger.debug("Format: " + aFormat + ", Fields: " + aFields);
            }

            // -- Contrôle si paramètre présent
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null || iObjectName == null || !RestUtil.isResource(iObjectName)) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, iObjectName, Verb.GET);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // Format csv
            if (Format.CSV.toString().equalsIgnoreCase(aFormat)) {
                return getCSV(jsonToVo(aQuery), loggedUser,
                        setCSVParameters(setSearchParameters(aSortFields, aDescFields, aRange), aFields, aLabelKeys, aLang));
            }

            if (aFieldname != null) {
                return getFieldsRequest(jsonToVo(aQuery), aFieldname, loggedUser, null,
                        setSearchParameters(aSortFields, aDescFields, aRange));
            }

            // Get the list
            List<IValueObject> list = getList(jsonToVo(aQuery), loggedUser, setSearchParameters(aSortFields, aDescFields, aRange))
                    .getListObject();

            // Build the response
            List<Object> idlist = new ArrayList<>();
            for (IValueObject rec : list) {
                idlist.add(rec.getId());
            }
            JsonArray records = vosToJson(list);
            JsonArrayBuilder ids = Json.createArrayBuilder();
            for (Object id : idlist) {
                ids.add((Long) id);
            }
            JsonObjectBuilder json = Json.createObjectBuilder();
            json.add("records", records);
            json.add("ids", ids.build());
            return Response.ok(json.build().toString()).build();
        } catch (ISSecurityException e) {
            logger.warn("Le token est invalid", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            logger.error("Erreur : ", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * COnversion from list of ValueObjects to Json array. Possible to customize
     *
     * @param aList
     *            vo list
     * @return JsonArray with vo items
     * @throws ISException
     *             conversion error
     */
    protected JsonArray vosToJson(List<IValueObject> aList) throws ISException {
        return JsonVoUtil.vosToJson(aList);
    }

    /**
     * Compte le nombre d'objets métier pour un nom donné
     *
     * http://url/projet/services/objects/{objectname}/count
     *
     * @param aAuthorization
     *            token de sécurité OIDC: Authorization: "Bearer <token>"
     * @param aQuery
     *            critères de recherche {"field1":"value1", "field2":"value2"}
     *
     * @return le nombre d'objects
     */
    @ApiOperation(value = "Count the number of business objects of a given type")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 400, message = "Error input paramters or counting business object"),
            @ApiResponse(code = 401, message = "Error authenfication"), @ApiResponse(code = 403, message = "No access permission"),
            @ApiResponse(code = 500, message = "Error request") })
    @GET
    @Path("count")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCount(@ApiParam(value = "Security token", required = true) @HeaderParam("Authorization") String aAuthorization,
            @ApiParam(value = "Filter criteria as an expression json", required = false) @QueryParam("query") String aQuery) {

        try {

            logger.debug("COUNT - ObjectName : " + iObjectName + ",query : " + aQuery);

            // -- Contrôle si paramètre présent
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null || iObjectName == null || !RestUtil.isResource(iObjectName)) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, iObjectName, Verb.GET);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // -- Count the objects
            Integer nb = (Integer) getCount(jsonToVo(aQuery), loggedUser).getValue();

            logger.debug("COUNT RESULT - ObjectName : " + iObjectName + ",query : " + aQuery + ",count : " + nb);

            // Build the response
            JsonObjectBuilder json = Json.createObjectBuilder().add("count", nb);
            return Response.ok(json.build().toString()).build();
        } catch (ISSecurityException e) {
            logger.warn("Invalid token", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            logger.error("Erreur : ", e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * Enregistre une liste d'objets
     *
     * http://url/projet/services/objects/{objectname}
     *
     * @param aAuthorization
     *            token de sécurité OIDC: Authorization: "Bearer <token>"
     * @param aRecords
     *            liste d'objets à enregistrer
     * @param aDeletes
     *            the list of objects to delete
     *
     * @return nombre d'enregistrements crées et modifiés
     */
    @ApiOperation(value = "Save a liste of business objects of a given type")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"), @ApiResponse(code = 400, message = "Error input parameters"),
            @ApiResponse(code = 401, message = "Error authenfication"), @ApiResponse(code = 403, message = "No access permission"),
            @ApiResponse(code = 500, message = "Error querying") })
    @PUT
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public final Response updateList(
            @ApiParam(value = "Security token", required = true) @HeaderParam("Authorization") String aAuthorization,
            @ApiParam(value = "List of business objects to create/update as an expression json", required = false) @FormParam(value = "records") String aRecords,
            @ApiParam(value = "List of business objects to delete as an expression json", required = false) @FormParam(value = "deletes") String aDeletes) {
        logger.debug("PUT - Records : " + aRecords + ", Deletes: " + aDeletes);

        try {

            // -- Contrôle si paramètre présent
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null || iObjectName == null || !RestUtil.isResource(iObjectName) || aRecords == null && aDeletes == null) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, iObjectName, Verb.PUT);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // Validate data
            List<IValueObject> records = null;
            if (aRecords != null) {
                records = jsonToVos(aRecords);
                for (IValueObject vo : records) {
                    if (!isValidRequired(vo) || !isValidInput(vo)) {
                        return Response.status(Status.BAD_REQUEST).build();
                    }
                }
            }
            List<IValueObject> deletes = null;
            if (aDeletes != null) {
                deletes = jsonToVos(aDeletes);
                for (IValueObject vo : deletes) {
                    if (!isValidRequired(vo) || !isValidInput(vo)) {
                        return Response.status(Status.BAD_REQUEST).build();
                    }
                }
            }

            // -- Update the objects
            IDAOResult result = updateList(records, deletes, loggedUser);
            if (!result.isStatusOK() && !result.isStatusNOTHING_TODO()) {
                logger.error("Update retourne erreur: " + result);

                return Response.status(Status.BAD_REQUEST).build();
            }

            // Check the errors
            Response error = RestUtil.getErrorResponse(result);
            if (error != null) {
                return error;
            }

            // Build the response
            JsonObjectBuilder json = Json.createObjectBuilder().add("nbr", result.getNbrRecords());

            return Response.status(Status.OK).entity(json.build().toString()).build();
        } catch (ISSecurityException e) {
            logger.warn("Token invalid", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            logger.error("Erreur de modification de collection d'objets métier de type: " + iObjectName + ": " + aRecords, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

    /**
     * Lien avec l'objet qui traite un objet particulier
     *
     * Defines that the next path parameter after {objectname} is treated as a parameter and passed to the ObjectResource. Allows to type
     * http://url/projet/services/objects/{objectname}/{id}. {id} will be treated as parameter object and passed to ObjectResource
     *
     * @param aId
     *            id de l'objet
     * @return ressource pour accéder à un objet métier donné
     * @throws ISException
     *             erreur d'instatiation de la classe
     */
    @Path("{id}")
    public ObjectResourceOIDC getObjectResource(@PathParam("id") String aId) throws ISException {
        String orClassName = (String) RestUtil.getVOInfo(iObjectName).getValue(VOInfo.REST_OR_CLASSNAME);
        if (orClassName == null) {
            return new ObjectResourceOIDC(iContext, iObjectName, aId);
        }

        try {
            Class<?> cl = Class.forName(orClassName);
            Constructor<?> constr = cl.getConstructor(ServletContext.class, String.class);
            return (ObjectResourceOIDC) constr.newInstance(iContext, aId);
        } catch (Exception e) {
            throw new ISException("Error instantiating Class : " + orClassName, e);
        }
    }

    /**
     * Crée un nouveau enregistrement de type {objectname}
     *
     * @param aAuthorization
     *            token de sécurité OIDC: Authorization: "Bearer <token>"
     *
     * @param aRecord
     *            string json avec les champs
     *
     * @return l'id de l'objet crée
     */
    @ApiOperation(value = "Create a business object")
    @ApiResponses(value = { @ApiResponse(code = 201, message = "Business object created"),
            @ApiResponse(code = 400, message = "Error input parameters"), @ApiResponse(code = 401, message = "Error authenfication"),
            @ApiResponse(code = 403, message = "No access permission"), @ApiResponse(code = 500, message = "Error querying") })
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@ApiParam(value = "Security token", required = true) @HeaderParam("Authorization") String aAuthorization,
            @ApiParam(value = "The attributes of the object to be created", required = true) String aRecord) {

        try {
            logger.debug("POST - Records : " + aRecord);

            // Contrôle de token et autres paramètres présents
            JwtClaims claims = SecurityUtilOIDC.validateOIDCToken(RestUtil.parseAuthorization(aAuthorization),
                    RestUtil.getContextManager());
            RestUtil.addToNdc(claims);
            if (claims == null || aRecord == null || iObjectName == null || !RestUtil.isResource(iObjectName)) {
                return Response.status(Status.BAD_REQUEST).build();
            }

            // -- Contrôle de sécurité
            ILoggedUser loggedUser = RestUtil.getLoggedUser(claims, iObjectName, Verb.POST);
            if (loggedUser == null) {
                return Response.status(Status.FORBIDDEN).build();
            }

            // Validate data
            IValueObject vo = jsonToVo(aRecord);
            if (!isValidRequired(vo) || !isValidInput(vo)) {
                return Response.status(Status.BAD_REQUEST).build();
            }
            // -- Create the object
            IDAOResult result = create(vo, loggedUser);
            if (!result.isStatusOK()) {
                // Possibilité de donner une autre réson propre dans la classe
                // surchargée (sous value), sans provoquer d'erreur dans le log
                if (result.getValue() instanceof Response) {
                    return (Response) result.getValue();
                }
                logger.error("Création retourne id null. record: " + aRecord);
                return Response.status(Status.BAD_REQUEST).build();
            }

            // Build the response
            JsonObjectBuilder json = Json.createObjectBuilder();
            if (result.getValueObject() != null) {
                json.add("record", voToJson(result.getValueObject(), Boolean.TRUE.toString(), loggedUser)).add("id", (Long) result.getId());
            }
            return Response.status(Status.CREATED).entity(json.build().toString()).build();
        } catch (ISSecurityException e) {
            logger.warn("Token invalid", e);
            return Response.status(Status.UNAUTHORIZED).build();
        } catch (ISException e) {
            logger.error("Erreur de création de l'objet métier de type: " + iObjectName + ". Champs: " + aRecord, e);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        } finally {
            RestUtil.cleanNdc();
        }
    }

}
