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

package ch.inser.rest.services.object.arcgis;

import java.io.StringReader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.jsl.exceptions.ISException;

import jakarta.json.Json;
import jakarta.json.JsonReader;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Form;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * Resource to use the ArcGIS token generator.
 *
 * @author INSER SA */
public class TokenResource {

    /**
     * The logger.
     */
    private static final Log logger = LogFactory.getLog(TokenResource.class);

    /**
     * Private constructor to avoid object creation.
     */
    private TokenResource() {
        // Nothing to do
    }

    /**
     * Get a new token.
     *
     * @param aTokenProperties
     *            the token properties prefix, the used properties are <code>service</code> (the service URL), <code>username</code> and
     *            <code>password</code>
     *
     * @param aContextManager
     *            the context manager
     * @return the token
     * @throws ISException
     *             if the token generator return an error
     */
    protected static String getToken(String aTokenProperties, IContextManager aContextManager) throws ISException {
        // The query parameters
        Form form = new Form();
        form.param("username", aContextManager.getProperty(aTokenProperties + ".username"));
        form.param("password", aContextManager.getProperty(aTokenProperties + ".password"));
        form.param("referer", aContextManager.getProperty(aTokenProperties + ".referer"));

        // Execute the request
        WebTarget target = ClientBuilder.newBuilder().build().target(aContextManager.getProperty(aTokenProperties + ".service"));
        if (aContextManager.getProperty(aTokenProperties + ".service").indexOf("portal") != -1) {
            target = target.queryParam("f", "json");
        }

        logger.debug("getToken: request to: " + target.getUri());
        Response response = target.request().post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        logger.debug("getToken: response HTTP code: " + response.getStatus());

        // Check the result
        if (response.getStatus() != Status.OK.getStatusCode()) {
            logger.error("getToken: HTTP error code: " + response.getStatus());
            throw new ISException("Error getting a new token: " + response.getStatus());
        }

        // Return the token form ArcGIS Server
        if (aContextManager.getProperty(aTokenProperties + ".service").indexOf("portal") == -1) {
            return response.readEntity(String.class);
        }

        // Return the token form ArcGIS Portal
        String token = response.readEntity(String.class);
        logger.debug("getToken: response: " + token);
        try (JsonReader reader = Json.createReader(new StringReader(token))) {
            return reader.readObject().getString("token");
        }
    }

}
