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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.DAOResult;
import ch.inser.dynamic.common.DynamicDAO.Operator;
import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.jsl.tools.NumberTools;
import ch.inser.rest.services.object.ObjectNamesResource;
import ch.inser.rest.util.ServiceLocator;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.ResponseProcessingException;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

/**
 * ObjectNamesResource for ArcGIS Feature Server objects.
 *
 * Uses the <code>map.service.extent</code> property as request template and <code>map.service.iObjectName</code> property as service URL.
 *
 * @author INSER SA */
public abstract class FeatureObjectNamesResource extends ObjectNamesResource {

    /**
     * The logger.
     */
    private static final Log logger = LogFactory.getLog(FeatureObjectNamesResource.class);

    /**
     * The token properties prefix, the used properties are <code>service</code> (the service URL), <code>username</code> and
     * <code>password</code>
     */
    private String iTokenProperties;

    /**
     * Constructor.
     *
     * @param aContext
     *            REST servlet context
     * @param aObjectName
     *            the object name
     * @param aTokenProperties
     *            the token properties prefix, the used properties are <code>service</code> (the service URL), <code>username</code> and
     *            <code>password</code>
     */
    protected FeatureObjectNamesResource(ServletContext aContext, String aObjectName, String aTokenProperties) {
        super(aContext, aObjectName);
        iTokenProperties = aTokenProperties;
    }

    @Override
    protected IDAOResult getList(IValueObject aValueObject, ILoggedUser aLoggedUser, DAOParameter... aParameters) throws ISException {
        @SuppressWarnings("unchecked")
        Map<String, Double> extent = (Map<String, Double>) aValueObject.getProperty("extent");
        JsonObject polygon = (JsonObject) aValueObject.getProperty("polygon");
        if (extent != null || polygon != null) {
            Entry<String, Map<Operator, Object>> operator = null;

            if (extent != null) {
                // Process a getList query having an extent parameter
                operator = extentToInOperator(extent);
            } else {
                // (polygon != null)
                // Process a getList query having a polygon parameter
                operator = polygonToInOperator(polygon);
            }

            // If the extent is empty return an empty list
            if (operator == null) {
                return new DAOResult(new ArrayList<>(0));
            }

            // Add the operator
            aValueObject.setProperty(operator.getKey(), operator.getValue());
        }

        // Get the list from the super SQL implementation
        return super.getList(aValueObject, aLoggedUser, aParameters);
    }

    @Override
    protected IDAOResult getCount(IValueObject aValueObject, ILoggedUser aLoggedUser) throws ISException {
        @SuppressWarnings("unchecked")
        Map<String, Double> extent = (Map<String, Double>) aValueObject.getProperty("extent");
        if (extent != null) {
            // Process a getCount query having an extent parameter
            Entry<String, Map<Operator, Object>> operator = extentToInOperator(extent);

            // If the extent is empty return 0
            if (operator == null) {
                return new DAOResult(NumberTools.getInteger(0));
            }

            // Add the operator
            aValueObject.setProperty(operator.getKey(), operator.getValue());
        }

        // Get the list count from the super SQL implementation
        return getBPDelegate().getListCount(aValueObject, aLoggedUser);
    }

    /**
     * Convert an extent to an IN operator on the object ids.
     *
     * @param aExtent
     *            the extent
     * @return the IN operator, <code>null</code> if the extent is empty
     * @throws ISException
     *             if the ArcGIS server return an error
     */
    protected Entry<String, Map<Operator, Object>> extentToInOperator(Map<String, Double> aExtent) throws ISException {
        IContextManager contextManager = ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getContextManager();

        String query = String.format(contextManager.getProperty("map.service.extent"),
                contextManager.getProperty("map.service." + iObjectName), aExtent.get("xmin"), aExtent.get("ymin"), aExtent.get("xmax"),
                aExtent.get("ymax"), getToken());
        return queryToInOperator(query);
    }

    /**
     * Convert a polygon to an IN operator on the object ids.
     *
     * @param aPolygon
     *            the polygon
     * @return the IN operator, <code>null</code> if the extent is empty
     * @throws ISException
     *             if the ArcGIS server return an error
     */
    protected Entry<String, Map<Operator, Object>> polygonToInOperator(JsonObject aPolygon) throws ISException {
        IContextManager contextManager = ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getContextManager();

        try {
            String query = String.format(contextManager.getProperty("map.service.polygon"),
                    contextManager.getProperty("map.service." + iObjectName), URLEncoder.encode(aPolygon.toString(), "UTF-8"), getToken());
            return queryToInOperator(query);
        } catch (UnsupportedEncodingException e) {
            throw new ISException(e);
        }
    }

    /**
     * Get a new token.
     *
     * @return the token
     * @throws ISException
     *             if the token generator return an error
     */
    protected String getToken() throws ISException {
        return TokenResource.getToken(iTokenProperties, ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getContextManager());
    }

    /**
     * Convert a query to an IN operator on the object ids.
     *
     * @param aQuery
     *            the query
     * @return the IN operator, <code>null</code> if the extent is empty
     * @throws ISException
     *             if the ArcGIS server return an error
     */
    protected Entry<String, Map<Operator, Object>> queryToInOperator(String aQuery) throws ISException {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(aQuery);

        // Execute the request
        logger.debug("getList: request to: " + target.getUri());
        Response response = target.request().get();

        // Check the result
        if (response.getStatus() != Status.OK.getStatusCode()) {
            logger.error("getList: HTTP error code: " + response.getStatus());
            throw new ResponseProcessingException(response, "Error getting a new token");
        }

        // Read the JSON from the response
        JsonObject json;
        try (JsonReader jsonReader = Json.createReader(new StringReader(response.readEntity(String.class)))) {
            json = jsonReader.readObject();
        }
        logger.debug("getList: response: " + json);

        // Check the response content
        JsonValue error = json.get("error");
        if (error != null) {
            logger.error("getList: ArcGIS error: " + error);
            throw new ISException("Error getting the extent objects ids: " + error);
        }

        // Check if the result is empty
        if (json.get("objectIds").equals(JsonValue.NULL)) {
            return null;
        }

        // Build the IN operator
        List<JsonNumber> ids = json.getJsonArray("objectIds").getValuesAs(JsonNumber.class);
        List<Long> listStatuts = new ArrayList<>(ids.size());
        for (JsonNumber id : ids) {
            listStatuts.add(id.longValue());
        }
        Map<Operator, Object> map = new EnumMap<>(Operator.class);
        map.put(Operator.IN, listStatuts);
        return new SimpleEntry<>(json.getString("objectIdFieldName"), map);
    }
}
