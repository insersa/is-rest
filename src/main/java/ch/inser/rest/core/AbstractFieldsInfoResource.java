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

package ch.inser.rest.core;

import java.util.Set;

import ch.inser.dynamic.util.AttributeInfo;
import ch.inser.dynamic.util.VOInfo;
import ch.inser.rest.util.RESTLocator;
import ch.inser.rest.util.ServiceLocator;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Context;

/**
 * Abstract resource that provides the infos for initializing an input component. Implemented with Inser token or OIDC token
 *
 * - Required, readonly, min, max, pattern (regular expression in JS syntax)
 */
public abstract class AbstractFieldsInfoResource {

    /**
     * Le rest servlet context
     */
    @Context
    private ServletContext iContext;

    /**
     * Ajout les infos de tous les champs dans un json
     *
     * { geb_egid: {required:true, readonly:true}, geb_gparz: {...}}
     *
     * @param aObjectName
     *            nom de l'objet métier
     * @param aJson
     *            json à poopuler
     */
    protected void addFieldsInfo(String aObjectName, JsonObjectBuilder aJson) {
        VOInfo voInfo = getVOInfo(aObjectName);
        Set<String> fields = voInfo.getAttributes().keySet();
        for (String field : fields) {
            JsonObjectBuilder fieldJson = Json.createObjectBuilder();
            AttributeInfo info = voInfo.getAttribute(field);
            fieldJson.add(AttributeInfo.LENGTH, info.getLength());
            fieldJson.add(AttributeInfo.REQUIRED, (Boolean) info.getInfo(AttributeInfo.REQUIRED));
            fieldJson.add(AttributeInfo.READONLY, Boolean.valueOf((String) info.getValue(AttributeInfo.READONLY)));
            if (info.getInfo(AttributeInfo.MIN) != null) {
                fieldJson.add(AttributeInfo.MIN, (Integer) info.getInfo(AttributeInfo.MIN));
            }
            if (info.getInfo(AttributeInfo.MAX) != null) {
                fieldJson.add(AttributeInfo.MAX, (Integer) info.getInfo(AttributeInfo.MAX));
            }
            if (info.getInfo(AttributeInfo.PATTERN) != null) {
                fieldJson.add(AttributeInfo.PATTERN, (String) info.getInfo(AttributeInfo.PATTERN));
            }
            aJson.add(field, fieldJson);
        }
    }

    /**
     *
     * @param aEntity
     *            nom de l'objet métier
     * @return la configuration d'un objet métier
     */
    protected VOInfo getVOInfo(String aEntity) {
        return ((RESTLocator) ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getLocator("rest")).getVOInfo(aEntity);
    }

}
