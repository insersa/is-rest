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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.DAOParameter.Name;
import ch.inser.dynamic.common.DynamicDAO.Operator;
import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynamic.common.IValueObject.Type;
import ch.inser.dynamic.util.AttributeInfo;
import ch.inser.dynaplus.bo.ChildrenlistObject;
import ch.inser.dynaplus.format.IFormatEngine.Format;
import ch.inser.dynaplus.vo.VOFactory;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.jsl.list.ListHandler.Sort;
import ch.inser.jsl.tools.NumberTools;
import ch.inser.rest.auth.SecurityUtil;
import ch.inser.rest.util.Constants;
import ch.inser.rest.util.JsonVoUtil;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;

import io.jsonwebtoken.Claims;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;

/**
 * Resource abstrait pour objects métiers
 *
 * @author INSER SA *
 */
public abstract class AbstractResource {

    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(AbstractResource.class);

    /**
     * Le rest servlet context
     */
    protected ServletContext iContext;

    /**
     * Nom de l'objet métier
     */
    protected String iObjectName;

    /**
     *
     * @param aContext
     *            Le rest servlet context
     * @param aObjectName
     *            Nom de l'objet métier
     */
    protected AbstractResource(ServletContext aContext, String aObjectName) {
        iContext = aContext;
        iObjectName = aObjectName;
    }

    /**
     *
     * @return bp delegate pour le objectname
     */
    protected IBPDelegate getBPDelegate() {
        return RestUtil.getBPDelegate(iObjectName);
    }

    /**
     * Initialise un value object avec les attributs-valeurs donnés.
     *
     * @param aJson
     *            expression json avec attributs-valeurs
     * @return valueobject avec attributs-valeurs
     * @throws ISException
     *             on database access problems
     */
    protected IValueObject jsonToVo(String aJson) throws ISException {
        IValueObject vo = ((VOFactory) iContext.getAttribute("VOFactory")).getVO(iObjectName);
        return JsonVoUtil.jsonToVo(aJson, vo);
    }

    /**
     * Initialise un value object avec le json patch.
     *
     * @param aId
     *            Id de l'objet métier
     *
     * @param aPatch
     *            expression json avec instructions patch
     * @return valueobject avec attributs-valeurs
     * @throws ISException
     *             Exception potentiel dans la classe qui surcharge cette méthode
     */
    @SuppressWarnings("unused")
    protected IValueObject jsonPatchToVo(Object aId, String aPatch) throws ISException {
        IValueObject vo = ((VOFactory) iContext.getAttribute("VOFactory")).getVO(iObjectName);
        return JsonVoUtil.jsonPatchToVo(aId, aPatch, vo);
    }

    /**
     * Initialise une liste de value objects avec le json donné
     *
     * @param aJson
     *            expression json avec liste d'objets
     * @return liste de valueobjects
     * @throws ISException
     *             on database access problems
     */
    protected List<IValueObject> jsonToVos(String aJson) throws ISException {
        return JsonVoUtil.jsonToVos(aJson, ((VOFactory) iContext.getAttribute("VOFactory")).getVO(iObjectName));
    }

    /**
     * Converti un vo en objet json
     *
     * @param aRecord
     *            vo à convertir
     * @param aIncludeChildren
     *            flag pour inclure les enfants
     * @param aUser
     *            utilisateur
     * @return json
     * @throws ISException
     *             erreur de conversion
     */
    protected JsonObject voToJson(IValueObject aRecord, String aIncludeChildren, ILoggedUser aUser) throws ISException {
        return JsonVoUtil.voToJson(aRecord, aIncludeChildren == null || Boolean.TRUE.toString().equals(aIncludeChildren), aUser);
    }

    /**
     * Genère une réponse avec un enregistrement pdf
     *
     * @param aId
     *            id de l'objet à imprimer
     * @param aLang
     *            langue en format iso-lang-code, .p.ex 'fr', 'de'
     * @param aUser
     *            utilisateur publique
     * @return réponse http avec l'enregistrement en format PDF
     * @throws ISException
     *             erreur de préparation du PDF
     */
    protected Response getPDF(String aId, String aLang, ILoggedUser aUser) throws ISException {
        IDAOResult result = RestUtil.getBPDelegate(iObjectName).getRecord(aId, aUser, new DAOParameter(Name.RESULT_FORMAT, Format.PDF),
                new DAOParameter(Name.RESULT_LANG, aLang));
        if (result.isStatusNOTHING_TODO()) {
            logger.info("Entité " + iObjectName + ", ID: " + aId + " non trouvé");
            return Response.status(Status.NOT_FOUND).build();
        }
        ResponseBuilder response = Response.ok(result.getValue());
        response.header("Content-Disposition", "attachment;filename=\"" + iObjectName + aId + ".pdf\"");
        response.header("Content-type", "application/pdf");
        response.header("charset", "UTF-8");
        return response.build();
    }

    /**
     * Vérifie la validité des données par rapport à la configuration de l'objet métier
     *
     * @param aVo
     *            enregistrement à valider
     * @return true si l'enregistrement est conforme aux propriétés de l'objet métier
     */
    protected boolean isValidInput(IValueObject aVo) {
        for (String field : aVo.getProperties().keySet()) {
            if (aVo.getPropertyType(field) == null || Operator.IS_NULL.equals(aVo.getProperty(field))) {
                return true;
            }
            switch (aVo.getPropertyType(field)) {
                case LONG:
                case DOUBLE:
                    if (!isValidNumber(field, aVo.getProperty(field))) {
                        return false;
                    }
                    break;
                case STRING:
                    if (!isValidString(field, (String) aVo.getProperty(field))) {
                        return false;
                    }
                    break;
                default:
                    break;
            }
        }
        return true;
    }

    /**
     *
     * @param aVo
     *            enregistrement à valider
     * @return true si tous les champs obligatoires sont présents
     */
    protected boolean isValidRequired(IValueObject aVo) {

        for (String field : RestUtil.getVOInfo(aVo.getName()).getRequireds()) {
            if (aVo.getVOInfo().getId().equals(field)) {
                continue;
            }
            if (aVo.getProperty(field) == null) {
                logger.info("Valeur requise manque pour le champ: " + field);
                return false;
            }
            if (Type.STRING.equals(aVo.getPropertyType(field)) && ((String) aVo.getProperty(field)).matches("^\\s+$")) {
                logger.info("Le champ obligatoire " + field + " contient que des espaces");
                return false;
            }
        }

        return isValidRequiredChildren(aVo);
    }

    /**
     *
     * @param aVo
     *            vo parent
     * @return true si les champs obligatoires des enfants sont remplis
     */
    @SuppressWarnings("unchecked")
    protected boolean isValidRequiredChildren(IValueObject aVo) {
        if (aVo.getProperty("childrenlistObjects") == null) {
            return true;
        }
        for (ChildrenlistObject obj : (List<ChildrenlistObject>) aVo.getProperty("childrenlistObjects")) {
            for (IValueObject vo : obj.getCreateList()) {
                if (!isValidRequired(vo)) {
                    return false;
                }
            }
            for (IValueObject vo : obj.getUpdateList()) {
                if (!isValidRequired(vo)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     *
     * @param aField
     *            nom du champ
     * @param aValue
     *            valeur
     * @return true si la valeur ne dépasse pas la longueur maximale
     */
    protected boolean isValidLength(String aField, String aValue) {
        int maxlength = RestUtil.getVOInfo(iObjectName).getAttribute(aField).getLength();
        int length = aValue.length();

        if (length > maxlength) {
            logger.info("Valeur trop longue pour champ " + aField + ". Maxlength: " + maxlength + ", longueur reçu: " + length);
            return false;
        }
        return true;
    }

    /**
     *
     * @param aField
     *            nom du champs
     * @param aValue
     *            valeur
     * @return true si la valeur est conforme au pattern
     */
    protected boolean isValidString(String aField, String aValue) {
        if (!isValidLength(aField, aValue)) {
            return false;
        }
        String pattern = (String) RestUtil.getVOInfo(iObjectName).getAttribute(aField).getInfo(AttributeInfo.PATTERN);
        if (pattern == null) {
            return true;
        }
        if (!aValue.matches(pattern)) {
            logger.info("Pattern invalide pour champ " + aField + ". Pattern attendu: " + pattern + ". Valeur reçu: " + aValue);
            return false;
        }
        return true;
    }

    /**
     *
     * @param aField
     *            nom du champ
     * @param aValue
     *            valeur
     * @return true si la valeur est conforme aux propriétés min, max et length
     */
    protected boolean isValidNumber(String aField, Object aValue) {
        if (!isValidLength(aField, aValue.toString().replace("\\.", ""))) {
            return false;
        }
        boolean isValid = true;
        AttributeInfo info = RestUtil.getVOInfo(iObjectName).getAttribute(aField);
        if (info.getInfo(AttributeInfo.MIN) != null && NumberTools.getDouble(aValue) < (Integer) info.getInfo(AttributeInfo.MIN)) {
            logger.info("Valeur invalide pour champ " + aField + ". Attendu: min " + info.getInfo(AttributeInfo.MIN) + ", reçu: " + aValue);
            return false;
        }
        if (info.getInfo(AttributeInfo.MAX) != null && NumberTools.getDouble(aValue) > (Integer) info.getInfo(AttributeInfo.MAX)) {
            logger.info("Valeur invalide pour champ " + aField + ". Attendu: max " + info.getInfo(AttributeInfo.MAX) + ", reçu: " + aValue);
            return false;
        }
        return isValid;
    }

    /**
     * Get a record from the business process, this method can be overwritten to add specific behaviors like transactions before and/or
     * after the BP call.
     *
     * @param aId
     *            the record id
     * @param aLoggedUser
     *            the logged user
     * @return the business process result
     * @throws ISException
     *             for any exception
     */
    protected IDAOResult getRecord(String aId, ILoggedUser aLoggedUser) throws ISException {
        return getBPDelegate().getRecord(aId, aLoggedUser);
    }

    /**
     * Get the list from the business process, this method can be overwritten to add specific behaviors like transactions before and/or
     * after the BP call.
     *
     * @param aValueObject
     *            the value object
     * @param aLoggedUser
     *            the logged user
     * @param aParameters
     *            the DAO parameters
     * @return the business process result
     * @throws ISException
     *             for any exception
     */
    protected IDAOResult getList(IValueObject aValueObject, ILoggedUser aLoggedUser, DAOParameter... aParameters) throws ISException {
        return getBPDelegate().getList(aValueObject, aLoggedUser, aParameters);
    }

    /**
     * Get the fields from the business process, this method can be overwritten to add specific behaviors like transactions before and/or
     * after the BP call.
     *
     * @param aValueObject
     *            the value object
     * @param aFieldname
     *            the name of the field to be retrieved
     * @param aLoggedUser
     *            the logged user
     * @param aClaims
     *            claims pour construire un nouveau token
     * @param aParameters
     *            DAO parameters, ex. sort key, sort orientation
     * @return the business process result
     * @throws ISException
     *             for any exception
     */
    protected Response getFieldsRequest(IValueObject aValueObject, String aFieldname, ILoggedUser aLoggedUser, Claims aClaims,
            DAOParameter... aParameters) throws ISException {
        IDAOResult result = getBPDelegate().getFieldsRequest(aValueObject, aFieldname, aLoggedUser, aParameters);
        JsonObjectBuilder json = Json.createObjectBuilder();
        json.add("fields", JsonVoUtil.fieldsToJsonArray(aValueObject, aFieldname, result.getListValue()));
        if (aClaims != null) {
            json.add(Constants.TOKEN, SecurityUtil.getToken(aClaims, RestUtil.getContextManager()));
        }
        return Response.ok(json.build().toString()).build();
    }

    /**
     * Prépare les paramètres pour la recherche
     *
     * @param aSortFields
     *            champs de tri asc.
     * @param aDescFields
     *            champs de tri desc.
     * @param aRange
     *            tranche de résultat, ex. 1-12 ou 13-24
     * @return array de DAOParameters avec tri et range
     */
    protected DAOParameter[] setSearchParameters(String aSortFields, String aDescFields, String aRange) {
        List<DAOParameter> params = new ArrayList<>();
        if (aRange != null) {
            String[] range = aRange.split("-");
            params.add(new DAOParameter(Name.ROWNUM_START, NumberTools.getLong(range[0].trim())));
            params.add(new DAOParameter(Name.ROWNUM_END, NumberTools.getLong(range[1].trim())));
        }

        if (aSortFields != null) {
            params.add(new DAOParameter(Name.SORT_KEY, aSortFields.split(",")[0]));
            params.add(new DAOParameter(Name.SORT_ORIENTATION, Sort.ASCENDING));
        }
        if (aDescFields != null) {
            params.add(new DAOParameter(Name.SORT_KEY, aDescFields.split(",")[0]));
            params.add(new DAOParameter(Name.SORT_ORIENTATION, Sort.DESCENDING));
        }
        return params.toArray(new DAOParameter[params.size()]);
    }

    /**
     * Check if a business entity is public.
     *
     * @return <code>true</code> if the business entity is public
     */
    protected boolean isPublicEntity() {
        IContextManager ctx = ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getContextManager();
        if (ctx.getProperty("security.public.list") == null) {
            return false;
        }
        String[] entities = ctx.getProperty("security.public.list").split(",");
        for (int i = 0; i < entities.length; i++) {
            if (iObjectName.equalsIgnoreCase(entities[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Info: we sanityze the csv content to remove dangerous characters at start of each line to avoid potential security issues with Excel.
     * To avoid this behavior override this method in the child class but be aware of the security risks.
     *
     * @param aVo
     *            requête
     * @param aLoggedUser
     *            utilisateur authentifié
     * @param aParams
     *            paramètres de recherche et formattage
     * @return réponse 200 avec liste csv en byte[]
     * @throws ISException
     *             erreur de récuperation de csv
     */
    protected Response getCSV(IValueObject aVo, ILoggedUser aLoggedUser, DAOParameter[] aParams) throws ISException {
        IDAOResult result = getList(aVo, aLoggedUser, aParams);
        if (result.isStatusOK()) {
            byte[] csvData = (byte[]) result.getValue();
            String csvContent = new String(csvData, StandardCharsets.ISO_8859_1);

            // Remove dangerous characters at start of each line
            csvContent = csvContent.replaceAll("\"[=@+-]+", "\"");

            Response.ResponseBuilder response = Response.ok(csvContent.getBytes(StandardCharsets.ISO_8859_1));
            response.header("Content-Disposition", "attachment;filename=\"" + iObjectName + ".csv\"");
            response.header("Content-type", "text/csv");
            response.header("charset", "UTF-8");
            return response.build();
        }
        logger.error("Erreur de récuperation de csv. Status" + result.getStatus());
        return Response.status(Status.INTERNAL_SERVER_ERROR).build();
    }

    /**
     * Paramètres pour la liste en format CSV
     *
     * @param aParams
     *            paramètres de recherche
     * @param aFields
     *            champs à inclure dans le résultat
     * @param aLabelKeys
     *            clés pour les headers du résultat
     * @param aLang
     *            result language
     * @return paramètres de recherche et formattage
     */
    protected DAOParameter[] setCSVParameters(DAOParameter[] aParams, String aFields, String aLabelKeys, String aLang) {
        List<DAOParameter> params = new ArrayList<>();
        params.addAll(Arrays.asList(aParams));
        params.add(new DAOParameter(DAOParameter.Name.RESULT_FORMAT, Format.CSV));
        params.add(new DAOParameter(DAOParameter.Name.RESULT_FIELDS, aFields));
        params.add(new DAOParameter(DAOParameter.Name.RESULT_LABEL_KEYS, aLabelKeys));
        params.add(new DAOParameter(DAOParameter.Name.RESULT_LANG, aLang));
        return params.toArray(new DAOParameter[params.size()]);
    }
}
