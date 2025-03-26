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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.format.IFormatEngine.Format;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.core.AbstractResource;
import ch.inser.rest.util.Constants.Verb;
import ch.inser.rest.util.JsonVoUtil;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;

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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Variante publique de ObjectNamesResource
 *
 * @author INSER SA *
 */
@Api(value = "{objectname}")
public class PublicNamesResource extends AbstractResource {

    /**
     * The logger.
     */
    private static final Log logger = LogFactory.getLog(PublicNamesResource.class);

    /**
     *
     * @param aContext
     *            Le rest servlet context
     * @param aObjectName
     *            Nom de l'objet métier
     */
    public PublicNamesResource(ServletContext aContext, String aObjectName) {
        super(aContext, aObjectName);
    }

    /**
     * Lien avec l'objet qui traite un objet particulier
     *
     * Defines that the next path parameter after {objectname} is treated as a parameter and passed to the PublicObjectResource. Allows to
     * type http://url/projet/services/public/{objectname}/{id}. {id} will be treated as parameter object and passed to PublicObjectResource
     *
     * @param aId
     *            id de l'objet
     * @return ressource pour accéder à un objet métier donné
     */
    @Path("{id}")
    public PublicObjectResource getPublicObjectResource(@PathParam("id") String aId) {
        return new PublicObjectResource(iContext, iObjectName, aId);
    }

    /**
     * Recherche des objets métier
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
    public final Response getList(
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

            ILoggedUser loggedUser = SecurityUtil.getPublicUser((ServiceLocator) iContext.getAttribute("ServiceLocator"));
            RestUtil.addToNdc(loggedUser.getUsername());
            Response error = validateAccess(loggedUser, aFormat);
            if (error != null) {
                return error;
            }

            // Format csv
            if (Format.CSV.toString().equalsIgnoreCase(aFormat)) {
                return getCSV(jsonToVo(aQuery), loggedUser,
                        setCSVParameters(setSearchParameters(aSortFields, aDescFields, aRange), aFields, aLabelKeys, aLang));
            }

            if (aFieldname != null) {
                return getFieldsRequest(jsonToVo(aQuery), aFieldname, loggedUser, null);
            }

            // Get the list
            List<IValueObject> list = getList(jsonToVo(aQuery), loggedUser, setSearchParameters(aSortFields, aDescFields, aRange))
                    .getListObject();

            // Build the response
            List<Object> idlist = new ArrayList<>();
            for (IValueObject rec : list) {
                idlist.add(rec.getId());
            }
            JsonArray records = JsonVoUtil.vosToJson(list);
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
