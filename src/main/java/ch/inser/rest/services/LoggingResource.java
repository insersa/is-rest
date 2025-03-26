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

import java.io.StringReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Ressource pour enregistrer les logs de front-end
 *
 * @author INSER SA *
 */
@Path("/logging")
@Api(value = "logging")
public class LoggingResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(LoggingResource.class);

    /**
     * Ecrit les messages reçus dans le logger de l'application
     *
     *
     * @param aLogContent
     *            le contenu à mettre dans le logger en string json
     *
     * @return statut 200 (ok)
     */
    @ApiOperation(value = "Write logs")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK") })
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response writeLogEntry(
            @ApiParam(value = "Content to be logged. {\"message\":le message,\"url\":le url,\"stack\":le stack}", required = true) String aLogContent) {

        logger.error("*********** START - LOG FRONTEND ***********");
        try (JsonReader reader = Json.createReader(new StringReader(aLogContent))) {
            JsonObject json = reader.readObject();
            // Stacktrace de error handler is-angular 7
            if (json.containsKey("message")) {
                logger.error("Message:" + json.getString("message"));
            }

            // Stacktrace de error handler is-angular 1.6.0
            if (json.containsKey("stack")) {
                logger.error(json.getString("stack"));
            }

            // Angular 14
            logger.error(json.toString());

            return Response.ok(json.toString()).build();
        } catch (JsonException e) {
            logger.error("Erreur avec paramètre d'entrée :" + aLogContent);
            logger.error("Exception : ", e);
            // Toujours répondre OK. Le logger du front est configuré pour envoyer les erreurs au backend. Boucle infinie!
            return Response.status(Status.OK).build();
        } finally {
            logger.error("*********** END - LOG FRONTEND ***********");
        }
    }

}
