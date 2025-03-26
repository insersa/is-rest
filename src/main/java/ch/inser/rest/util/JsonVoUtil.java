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
import java.sql.Date;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.DynamicDAO.Operator;
import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynamic.common.IValueObject.Type;
import ch.inser.dynamic.util.ChildrenInfo;
import ch.inser.dynaplus.bo.BPFactory;
import ch.inser.dynaplus.bo.ChildrenlistObject;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.dynaplus.vo.IVOFactory;
import ch.inser.dynaplus.vo.VOFactory;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.jsl.tools.NumberTools;
import ch.inser.jsl.tools.StringTools;
import ch.inser.rest.util.Constants.PatchItem;
import ch.inser.rest.util.Constants.PatchOperation;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.JsonValue.ValueType;

/**
 * Outils pour la transformation entre vo et JSONObject
 *
 * @author INSER SA *
 */
public class JsonVoUtil {

    /** Définition de catégorie de logging */
    private static final Log logger = LogFactory.getLog(JsonVoUtil.class);

    /**
     * Types d'attributs traités pour le transformation entre json et vo
     */
    private static final List<Type> JSON_DATATYPES = Arrays.asList(Type.LONG, Type.STRING, Type.DOUBLE, Type.BOOLEAN, Type.TIMESTAMP,
            Type.DATE, Type.CLOB);

    /**
     * Property name of flag that converts Java dates and timestamps to strings in ISO 8601 format with "T" between date and time:
     * yyyy-MM-ddTHH:mm:ss...
     */
    private static final String ISO_DATE_FORMAT_PROPERTY = "date.iso8601";

    /**
     * Property name of flag that converts Java dates to strings in ISO 8601 format with "T" between date and time AND timezone UTC:
     * yyyy-MM-ddTHH:mm:ss.000Z
     */
    private static final String ISO_DATE_UTC_PROPERTY = "date.iso.timezone.zero";

    /**
     * Formats de date reconnus.
     */
    private static LinkedHashMap<String, String> DATE_FORMATS = new LinkedHashMap<>();

    /** VO Factory pour créer des VOs à partir des JSON */
    private static IVOFactory iVOFactory;

    /**
     * BP Factory
     */
    private static BPFactory iBPFactory;

    /**
     * IContextManager
     */
    private static IContextManager iContextManager;

    static {
        // Initialisation des formats de date
        DATE_FORMATS.put("\\d{4}", "yyyy");
        DATE_FORMATS.put("\\d{2}.\\d{2}.\\d{4}", "dd.MM.yyyy");
        DATE_FORMATS.put("\\d{4}-\\d{2}-\\d{2}", "yyyy-MM-dd");
        DATE_FORMATS.put("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}", "yyyy-MM-dd HH:mm");
        DATE_FORMATS.put("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}", "yyyy-MM-dd HH:mm:ss");
        DATE_FORMATS.put("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}", "yyyy-MM-dd'T'HH:mm:ss");
        DATE_FORMATS.put("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3}", "yyyy-MM-dd HH:mm:ss.SSS");
        DATE_FORMATS.put("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}", "yyyy-MM-dd'T'HH:mm:ss.SSS");
        DATE_FORMATS.put("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    }

    /**
     * Constructeur privé
     */
    private JsonVoUtil() {
    }

    /**
     *
     * @param aVos
     *            liste de vos
     * @return json array avec les enregistrements [{obj1},{obj2}]
     * @throws ISException
     *             on database access problems
     */
    public static JsonArray vosToJson(Collection<IValueObject> aVos) throws ISException {
        return vosToJson(aVos, true);
    }

    /**
     * Conversion de vos en json array
     *
     * @param aVos
     *            liste de vos
     * @param aChildren
     *            flag pour inclusion des enfants
     * @param aExcludRecursive
     *            true s'il faut exclure la récursion sur les enfants du même type que aVos
     * @return aVos convertis en json array
     * @throws ISException
     *             erreur bd
     */
    public static JsonArray vosToJson(Collection<IValueObject> aVos, boolean aChildren, boolean aExcludRecursive) throws ISException {
        return vosToJson(aVos, aChildren, null, aExcludRecursive, null).build();
    }

    /**
     * Conversion de vos en json array
     *
     * @param aVos
     *            liste de vos
     * @param aChildren
     *            flag pour inclusion des enfants
     * @param aExcludeType
     *            type of child to exclude, ex Document
     * @param aExcludRecursive
     *            true s'il faut exclure la récursion sur les enfants du même type que aVos
     * @return aVos convertis en json array
     * @throws ISException
     *             erreur bd
     */
    public static JsonArray vosToJson(Collection<IValueObject> aVos, boolean aChildren, String aExcludeType, boolean aExcludRecursive)
            throws ISException {
        return vosToJson(aVos, aChildren, aExcludeType, aExcludRecursive, null).build();
    }

    /**
     * Conversion de vos en json array
     *
     * @param aVos
     *            liste de vos
     * @param aExcludRecursive
     *            true s'il faut exclure la récursion sur les enfants du même type que aVos
     * @return aVos convertis en json array
     * @throws ISException
     *             erreur bd
     */
    public static JsonArray vosToJson(Collection<IValueObject> aVos, boolean aExcludRecursive) throws ISException {
        return vosToJson(aVos, false, null, aExcludRecursive, null).build();
    }

    /**
     *
     * @param aVos
     *            liste de vos
     * @param aChildren
     *            flag inclure les enfants
     * @param aExcludeType
     *            Type d'enfant à exclure
     * @param aExcludeRecursive
     *            true s'il faut exclure la récursion sur les enfants du même type que aVos
     * @param aUser
     *            utilisateur du service
     * @return json array avec les enregistrements [{obj1},{obj2}]
     * @throws ISException
     *             on database access problems
     */
    private static JsonArrayBuilder vosToJson(Collection<IValueObject> aVos, boolean aChildren, String aExcludeType,
            boolean aExcludeRecursive, ILoggedUser aUser) throws ISException {
        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
        for (IValueObject vo : aVos) {
            jsonArrayBuilder.add(voToJson(vo, aChildren, aExcludeType, aExcludeRecursive, aUser));
        }
        return jsonArrayBuilder;
    }

    /**
     * Transform a value object to a JSON object.
     *
     * @param aVo
     *            the value object
     * @param aChildren
     *            <code>true</code> to get also the children on the value object
     * @param aUser
     *            the user to get the children, can be <code>null</code> if the children aren't required
     * @return the JSON object with the attributes from the value object
     * @throws ISException
     *             on database access problems
     */
    public static JsonObject voToJson(IValueObject aVo, boolean aChildren, ILoggedUser aUser) throws ISException {
        return voToJson(aVo, aChildren, true, aUser);
    }

    /**
     * Conversion vo -> json avec flag de récursion sur les enfants du même type que aVo
     *
     * @param aVo
     *            vo de base
     * @param aChildren
     *            true s'il faut inclure les enfants
     * @param aExcludeRecursive
     *            true s'il faut exclure la récursion sur les enfants du même type que le vo de base
     * @param aUser
     *            utilisateur
     * @return vo transformé en json
     * @throws ISException
     *             erreur au niveau bd
     */
    public static JsonObject voToJson(IValueObject aVo, boolean aChildren, boolean aExcludeRecursive, ILoggedUser aUser)
            throws ISException {
        if (aVo == null) {
            return null;
        }
        return voToJson(aVo, aChildren, null, aExcludeRecursive, aUser).build();
    }

    /**
     * Transform a value object to a JSON object en incluant les enfants selon option
     *
     * @param aVo
     *            vo à transformer
     * @param aChildren
     *            flag inclure les enfants
     * @param aExcludeType
     *            type d'enfant à exclure (p.ex. rélations N:N)
     * @param aExcludeRecursive
     *            true s'il faut exclure la recursion sur les enfants du même type que aVo
     * @param aUser
     *            utilisateur
     * @return json avec les attributs du vo (et enfants)
     * @throws ISException
     *             erreur au niveau bd
     */
    private static JsonObjectBuilder voToJson(IValueObject aVo, boolean aChildren, String aExcludeType, boolean aExcludeRecursive,
            ILoggedUser aUser) throws ISException {
        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
        for (String field : aVo.getProperties().keySet()) {
            addJsonValue(aVo, field, jsonBuilder);
        }

        // Add the children VOs
        if (aChildren) {
            addChildren(aVo, aExcludeType, aExcludeRecursive, aUser, jsonBuilder);
        }
        return jsonBuilder;
    }

    /**
     * Add the children.
     *
     * @param aVo
     *            the value object
     * @param aExcludeType
     *            type d'enfant à exclure, p.ex. pour éviter un boucle infini pour les rélations N:N
     * @param aExcludeRecursive
     *            true s'il faut exclure la recursion sur les enfants du même type que aVo
     * @param aUser
     *            the user to get the children
     * @param aJsonBuilder
     *            the JSON builder
     * @throws ISException
     *             on database access problems
     */
    private static void addChildren(IValueObject aVo, String aExcludeType, boolean aExcludeRecursive, ILoggedUser aUser,
            JsonObjectBuilder aJsonBuilder) throws ISException {
        List<ChildrenInfo> childrens = aVo.getVOInfo().getChildrens();
        if (childrens == null || childrens.isEmpty()) {
            return;
        }

        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
        for (ChildrenInfo info : childrens) {
            if (info.getChildrenName().equals(aExcludeType)) {
                continue;
            }
            if (aVo.getId() == null) {
                jsonBuilder.add(info.getChildrenName(), Json.createArrayBuilder());
            } else {
                IValueObject query = childrenQuery(aVo, info);

                // Get the children
                if (!query.isEmpty()) {
                    IDAOResult result = iBPFactory.getBP(info.getChildrenName()).getList(query, aUser);
                    jsonBuilder.add(info.getChildrenName(), vosToJson(result.getListObject(), info.isSubChildrens(),
                            aExcludeRecursive ? aVo.getVOInfo().getName() : null, aExcludeRecursive, aUser));
                }
            }
        }
        aJsonBuilder.add("children", jsonBuilder);
    }

    /**
     * Ajoute une valeur dans l'objet JSON
     *
     * @param aVo
     *            vo avec attributs-valeurs
     * @param aField
     *            nom de l'attribut
     * @param aJsonBuilder
     *            json object en construction
     * @throws ISException
     *             erreur d'ajout d'un childrenmap
     */
    private static void addJsonValue(IValueObject aVo, String aField, JsonObjectBuilder aJsonBuilder) throws ISException {
        if ("childrenmap".equals(aField)) {
            aJsonBuilder.add("childrenmap", addChildrenMap((Map<?, ?>) aVo.getProperty("childrenmap")));
            return;
        }
        if (aVo.getPropertyType(aField) == null) {
            addUntypedJsonValue(aField, aVo.getProperty(aField), aJsonBuilder);
            return;
        }
        switch (aVo.getPropertyType(aField)) {
            case LONG:
                aJsonBuilder.add(aField, (Long) aVo.getProperty(aField));
                break;
            case DOUBLE:
                aJsonBuilder.add(aField, (Double) aVo.getProperty(aField));
                break;
            case BOOLEAN:
                aJsonBuilder.add(aField, (Boolean) aVo.getProperty(aField));
                break;
            case STRING:
                aJsonBuilder.add(aField, (String) aVo.getProperty(aField));
                break;
            case DATE:
                aJsonBuilder.add(aField, dateTimeToString((Date) aVo.getProperty(aField)));
                break;
            case TIMESTAMP:
                aJsonBuilder.add(aField, timestampToString((Timestamp) aVo.getProperty(aField)));
                break;
            default:
                aJsonBuilder.add(aField, aVo.getProperty(aField).toString());
        }
    }

    /**
     *
     * @param aTimestamp
     *            un objet timestamp
     * @return le timestamp en format string (iso 8601 selon configuration)
     */
    private static String timestampToString(Timestamp aTimestamp) {
        if ("true".equals(iContextManager.getProperty("date.iso8601"))) {
            String origFormat = aTimestamp.toString();
            return origFormat.replace(" ", "T");
        }
        return aTimestamp.toString();
    }

    /**
     * Converti une liste de champs (ex. ids) en json array
     *
     * @param aVo
     *            vo pour consulter la config des champs
     * @param aField
     *            nom du champ
     * @param aList
     *            liste de valeurs
     * @return jsonarray avec les valeurs
     */
    public static JsonArray fieldsToJsonArray(IValueObject aVo, String aField, List<Object> aList) {
        JsonArrayBuilder jarray = Json.createArrayBuilder();
        for (Object value : aList) {
            addJsonValue(aVo, aField, value, jarray);

        }
        return jarray.build();
    }

    /**
     * Ajoute une valeur dans l'array JSON
     *
     * @param aVo
     *            vo pour consulter la config des champs
     * @param aField
     *            nom de l'attribut
     * @param aValue
     *            valeur de l'attribut
     * @param aJsonArrayBuilder
     *            json array en construction
     */
    private static void addJsonValue(IValueObject aVo, String aField, Object aValue, JsonArrayBuilder aJsonArrayBuilder) {
        switch (aVo.getPropertyType(aField)) {
            case LONG:
                aJsonArrayBuilder.add((Long) aValue);
                break;
            case DOUBLE:
                aJsonArrayBuilder.add((Double) aValue);
                break;
            case BOOLEAN:
                aJsonArrayBuilder.add((Boolean) aValue);
                break;
            case STRING:
                aJsonArrayBuilder.add((String) aValue);
                break;
            case DATE:
                aJsonArrayBuilder.add(dateTimeToString((Date) aValue));
                break;
            case TIMESTAMP:
                aJsonArrayBuilder.add(aValue.toString());
                break;
            default:
                aJsonArrayBuilder.add(aValue.toString());
        }
    }

    /**
     * Ajoute les heures et minutes à la date si dans le fichier de config la clé date.showtime est à true
     *
     * @param aDate
     *            Format date d'un champ
     * @return Une chaine de caractères contenant la date et l'heure
     */
    private static String dateTimeToString(java.sql.Date aDate) {
        if (iContextManager != null && "true".equals(iContextManager.getProperty("date.showtime"))) {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            if ("true".equals(iContextManager.getProperty(ISO_DATE_FORMAT_PROPERTY))) {
                if ("true".equals(iContextManager.getProperty(ISO_DATE_UTC_PROPERTY))) {
                    df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                } else {
                    df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                }
            }
            return df.format(aDate);
        }
        return aDate.toString();
    }

    /**
     * Ajoute une valeur d'un type inconnu à un JsonBuilder
     *
     * @param aField
     *            nom du champ
     * @param aValue
     *            valeur avec type inconnu
     * @param aJsonBuilder
     *            objet json en construction
     * @throws ISException
     *             erreur en ajoutant une valeur de type IValueObject
     */
    private static void addUntypedJsonValue(String aField, Object aValue, JsonObjectBuilder aJsonBuilder) throws ISException {
        if (aValue instanceof Collection) {
            JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
            for (Object value : (Collection<?>) aValue) {
                if (value instanceof IValueObject) {
                    arrayBuilder.add(getAnonymousVOBuilder((IValueObject) value));
                } else {
                    arrayBuilder.add(value.toString());
                }
            }
            aJsonBuilder.add(aField, arrayBuilder);
            return;
        }
        if (aValue instanceof IValueObject) {
            aJsonBuilder.add(aField, getAnonymousVOBuilder((IValueObject) aValue));
            return;
        }
        aJsonBuilder.add(aField, aValue.toString());
    }

    /**
     * Add a map of advanced search filters to the VO.
     *
     * @param aVo
     *            the VO to complete
     * @return the JSON object with the advanced search filters
     * @throws ISException
     *             if an error occurs
     */
    private static JsonObjectBuilder getAnonymousVOBuilder(IValueObject aVo) throws ISException {

        JsonObjectBuilder voJsonBuilder = Json.createObjectBuilder();
        for (String field : aVo.getProperties().keySet()) {
            addJsonValue(aVo, field, voJsonBuilder);
        }
        return voJsonBuilder;
    }

    /**
     * @param aChildrenMap
     *            map d'enfants d'un vo recherche type->childVo
     * @return objet json qui correspond au map
     * @throws ISException
     *             erreur de construction de l'objet json
     */
    private static JsonObjectBuilder addChildrenMap(Map<?, ?> aChildrenMap) throws ISException {
        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
        for (Entry<?, ?> child : aChildrenMap.entrySet()) {
            jsonBuilder.add((String) child.getKey(), voToJson((IValueObject) child.getValue(), false, null));
        }
        return jsonBuilder;
    }

    /**
     * Initialise un liste de value objects avec le string json donné
     *
     * @param aJson
     *            liste d'objets en format json array
     * @param aVo
     *            vo vierge
     * @return liste d'objets en format vo
     * @throws ISException
     *             on database access problems
     */
    public static List<IValueObject> jsonToVos(String aJson, IValueObject aVo) throws ISException {
        List<IValueObject> vos = new ArrayList<>();
        try (JsonReader reader = Json.createReader(new StringReader(aJson))) {
            JsonArray array = reader.readArray();
            for (Object json : array) {
                vos.add(jsonToVo(json.toString(), (IValueObject) aVo.clone()));
            }
        }
        return vos;
    }

    /**
     * Initialise un value object avec les attributs-valeurs donnés
     *
     * @param aJson
     *            expression json avec attributs-valeurs
     * @param aVo
     *            vo vierge
     * @return valueobject avec attributs-valeurs
     * @throws ISException
     *             on database access problems
     */
    public static IValueObject jsonToVo(String aJson, IValueObject aVo) throws ISException {
        if (aJson == null) {
            return aVo;
        }
        try (JsonReader jsonReader = Json.createReader(new StringReader(aJson))) {
            JsonObject json = jsonReader.readObject();
            for (String field : json.keySet()) {
                addJsonValue(json, field, aVo);
            }
            // Listes d'enfants
            if (json.containsKey("children")) {
                addChildren(aVo, json.getJsonObject("children"));
            }
            // Objets métier joints
            if (json.containsKey("joins")) {
                addJoins(aVo, json.getJsonArray("joins"));
            }
            // Critères de recherche sur enfants
            if (json.containsKey("childrenmap")) {
                addChildrenMap(aVo, json.getJsonObject("childrenmap"));
            }
            // Map extent
            if (json.containsKey("extent")) {
                addExtent(aVo, json.getJsonObject("extent"));
            }
            if (json.containsKey("advancedSearchMap")) {
                addAdvancedSearchFilter(json.getJsonObject("advancedSearchMap"), aVo);
            }

        }
        return aVo;
    }

    /**
     * Add a map extent tot he VO.
     *
     * @param aVo
     *            the vo to complete
     * @param aJsonObject
     *            the JSON map extent
     */
    private static void addExtent(IValueObject aVo, JsonObject aJsonObject) {
        Map<String, Double> extent = new HashMap<>();
        for (String key : aJsonObject.keySet()) {
            extent.put(key, aJsonObject.getJsonNumber(key).doubleValue());
        }
        aVo.setProperty("extent", extent);
    }

    /**
     * Ajoute un childrenMap (critères de recherche sur enfants) dans le VO
     *
     * @param aVo
     *            vo à remplir
     * @param aJson
     *            requête avec critères sur des enfants
     * @throws ISException
     *             erreur de construction du childrenmap
     */
    private static void addChildrenMap(IValueObject aVo, JsonObject aJson) throws ISException {
        Map<String, IValueObject> childrenMap = new HashMap<>();
        for (String type : aJson.keySet()) {
            childrenMap.put(type, jsonToVo(aJson.getJsonObject(type).toString(), iVOFactory.getVO(type)));
        }
        aVo.setProperty("childrenmap", childrenMap);
    }

    /**
     * Ajoute des objets au VO qui ne sont pas des enfants mais qui doivent aussi être transformés en VO
     *
     * @param aVo
     *            vo à remplir
     * @param aJoins
     *            array d'objets métiers à joindre à VO, format [{objType:{attributs}}, {objType:{attributs}},...]
     * @throws ISException
     *             erreur de transformation de json en vo
     */
    private static void addJoins(IValueObject aVo, JsonArray aJoins) throws ISException {
        Map<String, List<IValueObject>> joinVos = new HashMap<>();
        for (int i = 0; i < aJoins.size(); i++) {
            JsonObject join = aJoins.getJsonObject(i);
            String objectType = join.keySet().iterator().next();
            List<IValueObject> joins = joinVos.computeIfAbsent(objectType, k -> new ArrayList<>());
            joins.add(jsonToVo(join.getJsonObject(objectType).toString(), iVOFactory.getVO(objectType)));
        }
        aVo.setProperty("joins", joinVos);
    }

    /**
     * Ajoute une valeur Json dans le vo, selon le type de champ
     *
     * @param aField
     *            nom du champ
     * @param aJson
     *            objet json
     * @param aVo
     *            vo à remplir
     */
    private static void addJsonValue(JsonObject aJson, String aField, IValueObject aVo) {
        if ("DISTINCT".equalsIgnoreCase(aField)) {
            aVo.setProperty("DISTINCT", aJson.getBoolean(aField));
            return;
        }
        if ("children".equalsIgnoreCase(aField) || "joins".equalsIgnoreCase(aField) || "childrenmap".equalsIgnoreCase(aField)
                || "extent".equalsIgnoreCase(aField)) {
            return;
        }
        String propertyField = getPropertyField(aField);

        if (aVo.getPropertyType(propertyField) == null) {
            // Champ non-configuré. Type inconnu
            Object value = getJsonValue(aJson, aField);
            if (value == null || value.toString().isEmpty() || "null".equals(value)) {
                return;
            }
            if (value instanceof JsonNumber) {
                aVo.setProperty(aField, value.toString());
                return;
            }
            aVo.setProperty(aField, value);
            return;
        }
        if (!JSON_DATATYPES.contains(aVo.getPropertyType(propertyField)) && aJson.get(aField).getValueType() != ValueType.STRING) {
            throw new UnsupportedOperationException(
                    "Le type " + aVo.getPropertyType(propertyField) + " de l'attribut " + aField + " n'est pas supporté.");
        }
        aVo.setProperty(aField, getValueOf(aJson, aField, aVo.getPropertyType(propertyField)));
    }

    /**
     * Ajoute les enfants du json dans le VO
     *
     * @param aVo
     *            vo parent
     * @param aChildren
     *            enfants à ajouter
     * @throws ISException
     *             on database access problems
     */
    private static void addChildren(IValueObject aVo, JsonObject aChildren) throws ISException {
        List<ChildrenInfo> childrens = aVo.getVOInfo().getChildrens();
        if (childrens == null || childrens.isEmpty()) {
            return;
        }

        List<ChildrenlistObject> childrenObjects = new ArrayList<>();
        for (ChildrenInfo info : childrens) {
            ChildrenlistObject childrenObject = getChildrenObject(aVo, info.getChildrenName(),
                    aChildren.getJsonArray(info.getChildrenName()));
            if (childrenObject != null) {
                childrenObjects.add(childrenObject);
            }
        }
        if (!childrenObjects.isEmpty()) {
            aVo.setProperty("childrenlistObjects", childrenObjects);
        }
    }

    /**
     * Crée un ChildrenlistObject (créations, updates, deletes) pour un type d'enfant
     *
     * @param aVo
     *            enregistrement parent
     * @param aChildName
     *            nom de l'enfant
     * @param aRecords
     *            enregistrements enfant reçu du frontend
     * @return objet qui liste les vos enfant à créer, mettre à jour et supprimer
     * @throws ISException
     *             erreur de requête bd
     */
    private static ChildrenlistObject getChildrenObject(IValueObject aVo, String aChildName, JsonArray aRecords) throws ISException {
        List<IValueObject> creations = new ArrayList<>();
        List<IValueObject> updates = new ArrayList<>();
        List<Object> deletes = getChildrenIds(aVo, aChildName);
        if (aRecords != null) {
            for (int i = 0; i < aRecords.size(); i++) {
                IValueObject vo = jsonToVo(aRecords.getJsonObject(i).toString(), iVOFactory.getVO(aChildName));
                if (vo.getId() == null) {
                    creations.add(vo);
                } else {
                    deletes.remove(vo.getId());
                    updates.add(vo);
                }
            }
        }
        if (!creations.isEmpty() || !updates.isEmpty() || !deletes.isEmpty()) {
            ChildrenlistObject childObject = new ChildrenlistObject();
            childObject.setObjectType(aChildName);
            childObject.setCreateList(creations);
            childObject.setUpdateList(updates);
            childObject.setDeleteList(deletes);
            return childObject;
        }
        return null;
    }

    /**
     * Get the IDs of the children.
     *
     * @param aVo
     *            the parent value object
     * @param aName
     *            the children name
     * @return the list of children IDs
     * @throws ISException
     *             on database access problems
     */
    private static List<Object> getChildrenIds(IValueObject aVo, String aName) throws ISException {
        List<Object> result = new ArrayList<>();
        List<ChildrenInfo> childrens = aVo.getVOInfo().getChildrens();
        if (aVo.getId() == null || childrens == null || childrens.isEmpty()) {
            return result;
        }

        for (ChildrenInfo info : childrens) {
            if (!info.getChildrenName().equals(aName)) {
                continue;
            }
            IValueObject query = childrenQuery(aVo, info);
            if (query.isEmpty()) {
                return result;
            }

            // Get the children
            for (Object obj : iBPFactory.getBP(info.getChildrenName()).getFieldsRequest(query, query.getVOInfo().getId()).getList()) {
                if (obj instanceof String) {
                    result.add(obj);
                } else {
                    result.add(NumberTools.getLong(obj));
                }
            }
            return result;
        }
        return result;
    }

    /**
     * Build the value object to make the children query.
     *
     * @param aVo
     *            the parent value object
     * @param info
     *            the children info
     * @return a value object to make the children query
     */
    private static IValueObject childrenQuery(IValueObject aVo, ChildrenInfo info) {
        // Constructing query for childrens
        IValueObject query = VOFactory.getInstance().getVO(info.getChildrenName());
        String[] childLinks = info.getChildrenLink().split(",");
        String[] masterLinks = info.getMasterLink().split(",");
        for (int i = 0; i < childLinks.length; i++) {
            query.setProperty(childLinks[i], aVo.getProperty(masterLinks[i]));
        }
        if (info.getLinkTable() != null || query.isEmpty()) {
            // We have a link table construct the request
            // select "cTable" from "linkTable" where "mTable"=
            // valueOf("mLink")
            String request = info.getLinkTable();
            if (request != null && aVo.getProperty(info.getMasterLink()) != null) {
                request = StringTools.replace(request, "?", aVo.getProperty(info.getMasterLink()).toString());
                Map<Operator, String> map = new EnumMap<>(Operator.class);
                map.put(Operator.IN, request);
                query.setProperty(info.getChildrenLink(), map);
            }
        }
        if (Entity.DOCUMENT.toString().equals(info.getChildrenName())) {
            query.setProperty("doc_obj_name", aVo.getName());
        }
        return query;
    }

    /**
     * Initialise un value object avec les attributs-valeurs donnés
     *
     * @param aId
     *            id
     * @param aPatch
     *            expression json avec instructions patch
     * @param aVo
     *            vo vierge
     * @return valueobject avec attributs-valeurs
     */
    public static IValueObject jsonPatchToVo(Object aId, String aPatch, IValueObject aVo) {
        if (aPatch == null) {
            return aVo;
        }
        aVo.setId(aId);
        try (JsonReader jsonReader = Json.createReader(new StringReader(aPatch))) {
            JsonArray json = jsonReader.readArray();
            for (int i = 0; i < json.size(); i++) {
                JsonObject instruction = json.getJsonObject(i);
                if (!PatchOperation.REPLACE.toString().equals(instruction.getString(PatchItem.OP.toString()))) {
                    continue;
                }
                aVo.setTimestamp(translate(Type.TIMESTAMP, instruction.getString(PatchItem.DATE.toString())));
                String field = instruction.getString(PatchItem.PATH.toString());
                Object value = getJsonValue(instruction, PatchItem.VALUE.toString());
                if (value == null || value.toString().isEmpty() || "null".equals(value.toString())) {
                    aVo.setProperty(field, Operator.IS_NULL);
                } else if (value instanceof JsonArray) {
                    aVo.setProperty(field, getList((JsonArray) value, null));
                } else if (aVo.getVOInfo().getAttributes().get(field) == null) {
                    aVo.setProperty(field, value.toString());
                } else {
                    aVo.setProperty(field, getValueOf(instruction, PatchItem.VALUE.toString(), aVo.getPropertyType(field)));
                }
            }
        }
        return aVo;
    }

    /**
     * Pour un champ speciale de recherche, donne le nom du champ auxel il fait référence
     *
     * Exemples:
     *
     * @param aField
     *            nom du champ
     * @return nom du champ originale dans le config
     */
    private static String getPropertyField(String aField) {
        for (String suffix : new String[] { "_sec", "_multi" }) {
            if (aField.endsWith(suffix)) {
                return aField.substring(0, aField.indexOf(suffix));
            }
        }
        return aField;
    }

    /**
     * Transforme les valeurs json (integer,double,boolean,string) en bon type selon vo info
     *
     * @param aJson
     *            the JSON value
     * @param aField
     *            the field name
     * @param aType
     *            the type
     * @return la valeur dans le bon type selon config
     */
    private static Object getValueOf(JsonObject aJson, String aField, Type aType) {
        if (aJson.get(aField) == null) {
            return null;
        }
        Object value = getJsonValue(aJson, aField);
        if (value == null) {
            return null;
        }
        if (value instanceof JsonArray) {
            return getList((JsonArray) value, aType);
        }
        if (value instanceof String && ((String) value).isEmpty() || "null".equals(value)) {
            return null;
        }

        // On peut avoir du Json dans un clob!
        if (aType == Type.CLOB) {
            return translate(aType, value);
        }

        if (isOperator(value)) {
            return getOperator(value, aType);
        }
        return translate(aType, value);
    }

    /**
     * Pour une attribut json recupère la valeur
     *
     * @param aJson
     *            objet json
     * @param aField
     *            nom de l'attribut
     * @return valeur: boolean, string, JsonObject, JsonArray, JsonNumber (long, double, ...)
     */
    private static Object getJsonValue(JsonObject aJson, String aField) {
        JsonValue jsonValue = aJson.get(aField);
        if (jsonValue == null) {
            return null;
        }
        switch (jsonValue.getValueType()) {
            case NULL:
                return null;
            case ARRAY:
                return aJson.getJsonArray(aField);
            case STRING:
                return aJson.getString(aField);
            case NUMBER:
                return aJson.getJsonNumber(aField);
            case TRUE:
                return true;
            case FALSE:
                return false;
            case OBJECT:
                return aJson.getJsonObject(aField);
            default:
                break;
        }
        return null;
    }

    /**
     * Pour une attribut json recupère la valeur
     *
     * @param aJsonArray
     *            a JSON array
     * @param index
     *            the index
     * @return valeur: boolean, string, JsonObject, JsonArray, JsonNumber (long, double, ...)
     */
    private static Object getJsonValue(JsonArray aJsonArray, int index) {
        JsonValue jsonValue = aJsonArray.get(index);
        switch (jsonValue.getValueType()) {
            case NULL:
                return null;
            case ARRAY:
                return aJsonArray.getJsonArray(index);
            case STRING:
                return aJsonArray.getString(index);
            case NUMBER:
                return aJsonArray.getJsonNumber(index);
            case TRUE:
                return true;
            case FALSE:
                return false;
            case OBJECT:
                return aJsonArray.getJsonObject(index);
            default:
                break;
        }
        return null;
    }

    /**
     * Pour une array de valeurs, donne une arraylist avec les valeurs dans le bon type
     *
     * @param aList
     *            de valeurs
     * @param aType
     *            type de chaque valeur
     * @return arraylist avec les valeurs en type java
     */
    private static Object getList(JsonArray aList, Type aType) {
        if (aList.isEmpty()) {
            return null;
        }
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < aList.size(); i++) {
            list.add(translate(aType, getJsonValue(aList, i)));
        }
        return list;
    }

    /**
     * Traduit une valeur json en type java
     *
     * @param aType
     *            type VO (Long, Double,..)
     * @param aValue
     *            valeur extrait du json
     * @return valeur en type java
     */
    private static Object translate(Type aType, Object aValue) {
        if (aValue instanceof String) {
            return translateString(aType, (String) aValue);
        }
        return translateObject(aType, aValue);
    }

    /**
     * Translate Object to other types
     *
     * @param aType
     *            the type of the attribute in VO
     * @param aValue
     *            the value extracted from Json
     * @return the translated object
     */
    private static Object translateObject(Type aType, Object aValue) {
        switch (aType) {
            case STRING:
                return ((JsonNumber) aValue).toString();
            case LONG:
                return ((JsonNumber) aValue).longValue();
            case DOUBLE:
                return ((JsonNumber) aValue).doubleValue();
            case BOOLEAN:
                return aValue;
            case CLOB:
                if (aValue != null) {
                    return aValue.toString();
                }
                return null;

            default:
                throw new UnsupportedOperationException("Transformation pas implémenté pour json value: " + aValue + ", vo type: " + aType);
        }
    }

    /**
     * Translate String to other types
     *
     * @param aType
     *            the type of the attribute in VO
     * @param aValue
     *            the value extracted from Json
     * @return the translated object
     */
    private static Object translateString(Type aType, String aValue) {
        if (aType == null) {
            return aValue;
        }
        switch (aType) {
            case LONG:
                return Long.valueOf(aValue);
            case DOUBLE:
                return Double.valueOf(aValue);
            case BOOLEAN:
                return Boolean.valueOf(aValue);
            case TIMESTAMP:
                return getTimestamp(aValue);
            case DATE:
                return getDate(aValue);
            default:
                return aValue;
        }
    }

    /**
     * Transforme une expression "{operateur:valeur}" en map operateur->valeur
     *
     * @param aValue
     *            valeur
     * @param aType
     *            type de l'objet
     * @return map avec operateur (et valeur)
     */
    private static Object getOperator(Object aValue, Type aType) {
        if ("IS_NULL".equals(aValue)) {
            return Operator.getOperator(Operator.IS_NULL, null);
        }
        if ("IS_NOT_NULL".equals(aValue)) {
            return Operator.getOperator(Operator.IS_NOT_NULL, null);
        }
        if (aValue instanceof JsonObject) {
            JsonObject json = (JsonObject) aValue;
            Map<Operator, Object> operators = new EnumMap<>(Operator.class);
            for (String operator : json.keySet()) {
                operators.put(getOperator(operator), getValueOf(json, operator, aType));
            }
            return operators.size() > 0 ? operators : null;
        }
        return null;
    }

    /**
     * Get the operator.
     *
     * @param aOperator
     *            the operator
     * @return the operator
     */
    private static Operator getOperator(String aOperator) {
        switch (aOperator) {
            case "LIKE":
            case "UPPER_LIKE":
            case "FULL_LIKE":
            case "BIGGER":
            case "BIGGER_EQU":
            case "SMALLER":
            case "SMALLER_EQU":
            case "IN":
            case "NOT_IN":
            case "DIFF":
            case "UPPER_EQU":
            case "EQU":
            case "DAY_EQU":
            case "MONTH_EQU":
            case "YEAR_EQU":
                return Operator.valueOf(aOperator);
            default:
                throw new UnsupportedOperationException("Operateur " + aOperator + " pas encore implémenté");
        }
    }

    /**
     * @param aValue
     *            valeur d'un élément json
     * @return true si la valeur est un opérateur ou un objet JSON contenant un opérateur ('IS_NULL', {'BIGGER': 0}, etc.)
     */
    private static boolean isOperator(Object aValue) {
        if (aValue instanceof JsonObject) {
            return true;
        }
        if (aValue instanceof String) {
            switch ((String) aValue) {
                case "IS_NULL":
                case "IS_NOT_NULL":
                    return true;
                default:
                    break;
            }
        }
        return false;
    }

    /**
     * @param aValue
     *            date ou timestamp en string
     * @return objet timestamp
     */
    public static Object getTimestamp(String aValue) {
        String dateFormat = getDateFormat(aValue);
        if (dateFormat != null) {
            try {
                return new java.sql.Timestamp(new SimpleDateFormat(dateFormat).parse(aValue).getTime());
            } catch (ParseException e) {
                logger.error("Erreur de parsing de timestamp: " + aValue, e);
            }
        }
        if ("true".equals(iContextManager.getProperty("date.iso8601"))) {
            String nonIsoFormat = aValue.replace("T", " ");
            return Timestamp.valueOf(nonIsoFormat);
        }
        return Timestamp.valueOf(aValue);
    }

    /**
     * @param aValue
     *            date en format string
     * @return java sql date
     */
    public static Date getDate(String aValue) {
        if (aValue != null) {
            try {
                String dateFormat = getDateFormat(aValue);
                if (dateFormat != null) {
                    return new java.sql.Date(new SimpleDateFormat(dateFormat).parse(aValue).getTime());
                }
            } catch (ParseException e) {
                logger.error("Erreur de parsing de date: " + aValue, e);
            }
        }
        logger.error("Format de date non connu: " + aValue);
        throw new IllegalArgumentException("Format de date non connu: " + aValue);
    }

    /**
     * @param aValue
     *            valeur date ou timestamp
     * @return format pour créer un objet date
     */
    private static String getDateFormat(String aValue) {
        for (Map.Entry<String, String> entry : DATE_FORMATS.entrySet()) {
            if (aValue.matches(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Prépare les requête avancé avec des opérateurs "de-à" (différents combinaisons de >, >=, <, <=)
     *
     * @param aAdvancedSearchMap
     *            l'objet avec les opérateurs de critères avancés exemple: {dos_create_date: 0, childrenmap: {DEC: {dec_seance_date: 0}}}
     *
     *
     *
     *            { dos_create_date: date1, dos_create_date_sec: date2, childrenmap: {DEC: {dec_seance_date: date1, dec_seance_date_sec:
     *            date2}}, advancedSearchMap: {dos_create_date: 0, childrenmap: {DEC: {dec_seance_date: 0}}}}
     * @param aVo
     *            vo requête exemple { dos_create_date: date1, dos_create_date_sec: date2, childrenmap: {DEC: {dec_seance_date: date1,
     *            dec_seance_date_sec: date2}}}
     */
    private static void addAdvancedSearchFilter(JsonObject aAdvancedSearchMap, IValueObject aVo) {
        for (String searchField : aAdvancedSearchMap.keySet()) {
            if ("childrenmap".equals(searchField)) {
                JsonObject childrenmap = aAdvancedSearchMap.getJsonObject("childrenmap");
                for (String type : childrenmap.keySet()) {
                    IValueObject voChild = (IValueObject) ((Map<?, ?>) aVo.getProperty("childrenmap")).get(type);
                    addAdvancedSearchFilter(childrenmap.getJsonObject(type), voChild);
                }
            } else {
                convertAdvancedField(searchField, aVo, aAdvancedSearchMap.getInt(searchField));
            }
        }
    }

    /**
     * Converti une critère de recherche en requête "de - à" en fonction d'opérateur (différents combinaisons de >, >=, <, <=)
     *
     * @param aSearchField
     *            nom du champ
     * @param aVo
     *            vo requête
     * @param aOperator
     *            opérateur
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void convertAdvancedField(String aSearchField, IValueObject aVo, int aOperator) {
        Object from = aVo.getProperty(aSearchField);
        Object to = aVo.getProperty(aSearchField + "_sec");
        if (from == null && to == null) {
            return;
        }
        // Inversion du from et du to si nécessaire
        if (to != null && from instanceof Comparable
        // si from est null instanceof
        // retourne false!
                && ((Comparable) from).compareTo(to) > 0) {
            aVo.setProperty(aSearchField, to);
            aVo.setProperty(aSearchField + "_sec", from);
        }
        if (aOperator == 0 || from != null) {
            addOperator(aOperator, aSearchField, aVo);
        }
    }

    /**
     * Ajoute une attribut-valeur avec un opérateur selon type de recherche avancé
     *
     * @param aOperator
     *            numéro d'opérateur (0=aucun, 1=DIFF, 2=SMALLER etc.
     * @param aSearchField
     *            nom du champ
     * @param aVo
     *            vo requête
     */
    private static void addOperator(int aOperator, String aSearchField, IValueObject aVo) {
        Object from = aVo.getProperty(aSearchField);
        Object to = aVo.getProperty(aSearchField + "_sec");
        Map<Operator, Object> map = new EnumMap<>(Operator.class);
        switch (aOperator) {
            case 0:
                // Cas sans indication d'opérateur
                if (from != null) {
                    map.put(Operator.BIGGER_EQU, from);
                }
                if (to != null) {
                    map.put(Operator.SMALLER_EQU, to);
                }
                aVo.setProperty(aSearchField, map);
                break;
            case 1:
                // btw < <
                map.put(Operator.BIGGER, from);
                map.put(Operator.SMALLER, to);
                aVo.setProperty(aSearchField, map);
                break;
            case 2:
                // btw <= <
                map.put(Operator.BIGGER_EQU, from);
                map.put(Operator.SMALLER, to);
                aVo.setProperty(aSearchField, map);
                break;
            case 3:
                // btw < <=
                map.put(Operator.BIGGER, from);
                map.put(Operator.SMALLER_EQU, to);
                aVo.setProperty(aSearchField, map);
                break;
            case 4:
                // btw <= <=
                map.put(Operator.BIGGER_EQU, from);
                map.put(Operator.SMALLER_EQU, to);
                aVo.setProperty(aSearchField, map);
                break;
            default:
                break;
        }
    }

    /**
     * Set the VOFactory
     *
     * @param aVOFactory
     *            the VOFactory
     */
    public static void setVOFactory(IVOFactory aVOFactory) {
        iVOFactory = aVOFactory;
    }

    /**
     * Set the BPFactory.
     *
     * @param aBPFactory
     *            the BPFactory
     */
    public static void setBPFactory(BPFactory aBPFactory) {
        iBPFactory = aBPFactory;
    }

    /**
     * Set the iContextManager.
     *
     * @param aIContextManager
     *            the iContextmanager
     */
    public static void setContextManager(IContextManager aIContextManager) {
        iContextManager = aIContextManager;
    }

}
