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

package ch.inser.rest.util;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

/**
 * Méthodes pour manipuler un object JsonObject
 *
 * @author INSER SA *
 */
public class JsonUtil {

    /**
     * Private constructor to hide the implicit public one
     */
    private JsonUtil() {
    }

    /**
     * Crée un JsonObjectBuilder d'un objet json pour permettre de le modifier
     *
     * @param aJson
     *            objet Json immutable
     * @return jsonbuilder pour modifier le json
     */
    public static JsonObjectBuilder jsonObjectToBuilder(JsonObject aJson) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        for (Entry<String, JsonValue> entry : aJson.entrySet()) {
            job.add(entry.getKey(), entry.getValue());
        }
        return job;
    }

    /**
     * Crée un JsonArrayBuilder d'un objet json array pour permettre de le modifier
     *
     * @param aJson
     *            objet Json array immutable
     * @return jsonArrayBuilder pour modifier le array
     */
    public static JsonArrayBuilder jsonArrayToBuilder(JsonArray aJson) {
        JsonArrayBuilder job = Json.createArrayBuilder();
        for (JsonValue element : aJson) {
            job.add(element);
        }
        return job;
    }

    /**
     * Ajoute une propriété Json à un Json sous forme de String
     *
     * @param aPropertyName
     *            nom de la propriété
     * @param aValue
     *            valeur de la propriété
     * @param aJson
     *            représentation string du json à modifier
     * @return une représentation string du json modifié
     */
    public static String addJsonProperty(String aPropertyName, Object aValue, String aJson) {
        if (aValue == null || aPropertyName == null || aPropertyName.length() == 0) {
            return aJson;
        }
        try (JsonReader jsonReader = Json.createReader(new StringReader(aJson))) {
            JsonObjectBuilder job = jsonObjectToBuilder(jsonReader.readObject());

            if (aValue instanceof Long) {
                job.add(aPropertyName, (Long) aValue);
            } else if (aValue instanceof String) {
                job.add(aPropertyName, (String) aValue);
            } else if (aValue instanceof Integer) {
                job.add(aPropertyName, (Integer) aValue);
            }
            return job.build().toString();
        }
    }

    /**
     * Transforme un map a un json object builder
     *
     * @param aObjects
     *            structure hierarchique avec des clés et libellés Utilisé pour codes et traductions
     *
     * @return JsonObject hierarchique selon le structure hierarchique du map
     */
    public static JsonObjectBuilder mapToJsonObject(Map<?, ?> aObjects) {
        JsonObjectBuilder job = Json.createObjectBuilder();
        for (Entry<?, ?> entry : aObjects.entrySet()) {
            addObject(job, entry.getKey(), entry.getValue());
        }
        return job;
    }

    /**
     * Ajoute une clé valeur dans un json object builder
     *
     * @param aJob
     *            le object builder
     * @param aKey
     *            la clé
     * @param aValue
     *            la valeur (String, Long, Map ou List)
     */
    @SuppressWarnings("unchecked")
    private static void addObject(JsonObjectBuilder aJob, Object aKey, Object aValue) {
        if (aValue == null) {
            return;
        }
        if (aValue instanceof JsonObject) {
            aJob.add(aKey.toString(), (JsonObject) aValue);
            return;
        }
        if (aValue instanceof JsonArray) {
            JsonArray jArray = (JsonArray) aValue;
            JsonArrayBuilder builder = Json.createArrayBuilder();
            for (JsonValue jVal : jArray) {
                builder.add(jVal);
            }
            aJob.add(aKey.toString(), builder);
            return;
        }
        if (aValue instanceof List) {
            aJob.add(aKey.toString(), listToJsonArray((List<?>) aValue));
            return;
        }
        if (aValue instanceof Map) {
            aJob.add(aKey.toString(), mapToJsonObject((Map<String, Object>) aValue));
            return;
        }
        if (aValue instanceof String) {
            aJob.add(aKey.toString(), (String) aValue);
        }
        if (aValue instanceof Long) {
            aJob.add(aKey.toString(), (Long) aValue);
        }
        if (aValue instanceof BigDecimal) {
            aJob.add(aKey.toString(), (BigDecimal) aValue);
        }
    }

    /**
     * Transforme une liste en un json array builder
     *
     * @param aList
     *            liste d'éléments
     * @return json array builder avec les éléments de la liste
     */
    private static JsonArrayBuilder listToJsonArray(List<?> aList) {
        JsonArrayBuilder arrayJob = Json.createArrayBuilder();
        for (Object item : aList) {
            if (item instanceof String) {
                arrayJob.add((String) item);
            } else if (item instanceof Long) {
                arrayJob.add((Long) item);
            } else if (item instanceof List) {
                arrayJob.add(listToJsonArray((List<?>) item));
            } else if (item instanceof Map) {
                arrayJob.add(mapToJsonObject((Map<?, ?>) item));
            }
        }
        return arrayJob;
    }

    /**
     * Transforme un objet json en map
     *
     * @param aJson
     *            l'objet json
     * @return la nouvelle map
     */
    public static Map<String, Object> jsonObjectToMap(JsonObject aJson) {
        if (aJson == null) {
            return null;
        }
        Map<String, Object> ret = new HashMap<>();
        for (Entry<String, JsonValue> str : aJson.entrySet()) {
            ret.put(str.getKey(), jsonValueToObject(str.getValue()));
        }
        return ret;
    }

    /**
     * Transform a JSON string in a map.
     *
     * @param aJson
     *            the JSON string
     * @return the map
     */
    public static Map<String, Object> jsonObjectToMap(String aJson) {
        return jsonObjectToMap(stringToJsonObject(aJson));
    }

    /**
     * Transform a JSON string in a JSON object.
     *
     * @param aJson
     *            the JSON string
     * @return the JSON Object
     */
    public static JsonStructure stringToJsonStructure(String aJson) {
        try (JsonReader jsonReader = Json.createReader(new StringReader(aJson))) {
            return jsonReader.read();
        }
    }

    /**
     * @param aJson
     *            the string that will be used to create the JsonObject
     * @return the JsonObject created from aJson
     */
    public static JsonObject stringToJsonObject(String aJson) {
        if (aJson == null || aJson.isEmpty()) {
            return null;
        }
        try (JsonReader jsonReader = Json.createReader(new StringReader(aJson))) {
            return jsonReader.readObject();
        }
    }

    /**
     * Transforme un json en objet
     *
     * @param aJson
     *            le json
     * @return l'objet
     */
    public static Object jsonValueToObject(JsonValue aJson) {
        ValueType val = aJson.getValueType();
        switch (val) {
            case ARRAY:
                List<Object> list = new ArrayList<>();
                for (JsonValue obj : (JsonArray) aJson) {
                    list.add(jsonValueToObject(obj));
                }
                return list;
            case FALSE:
                return false;
            case TRUE:
                return true;
            case NUMBER:
                return ((JsonNumber) aJson).bigDecimalValue();
            case STRING:
                return ((JsonString) aJson).getString();
            case OBJECT:
                return jsonObjectToMap((JsonObject) aJson);
            case NULL:
            default:
                return null;

        }
    }

    /**
     * @param jsonObject
     *            from which to get the JsonValue
     * @param key
     *            of the JsonValue to get. It can be a sub key (e.g : { "my-root": { "my-key-one": { "my-key-target" : "the target" } } } if
     *            you want my-key-target, you can pass "my-root.my-key-one.my-key-target"
     * @return null if nothing is found or the JsonValue of the matching key
     */
    public static JsonValue getJsonValue(JsonObject jsonObject, String key) {
        JsonObject object = jsonObject;
        String[] keys = key.split("\\.");

        for (int i = 0; i < keys.length - 1; i++) {
            object = object.getJsonObject(keys[i]);
            if (object == null) {
                return null;
            }
        }

        return object.get(keys[keys.length - 1]);
    }
}
