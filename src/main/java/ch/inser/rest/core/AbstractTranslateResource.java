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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.DAOParameter.Name;
import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.dynaplus.vo.VOFactory;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.util.RestUtil;
import ch.inser.rest.util.ServiceLocator;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Context;

/**
 * Classe abstraite pour le service multilingue.
 *
 * @author INSER SA *
 */
public class AbstractTranslateResource {

    /**
     * Le rest servlet context
     */
    @Context
    protected ServletContext iContext;

    /**
     * Construit un map arborisé selon la structure hierarchique des clés, p.ex. "Button.edit" et "Button.create" donne
     * {"Button":{"edit":"Modifier". "create":"Ajouter"}}
     *
     * @param aLang
     *            langue
     * @param aLabels
     *            map qui va contenir les traductions
     * @param aSuperUser
     *            the super user
     *
     * @throws ISException
     *             erreur de récuperation des libellés de la base
     */
    protected void addHelpTexts(String aLang, Map<String, Object> aLabels, ILoggedUser aSuperUser) throws ISException {
        List<IValueObject> listHelpText = getHelpTexts(aLang, aSuperUser);
        for (IValueObject vo : listHelpText) {
            accumulate((String) vo.getProperty("hke_name"), (String) vo.getProperty("htx_label"), aLabels);
        }
    }

    /**
     * @param aLang
     *            langue
     * @param aSuperUser
     *            the super user
     * @return liste des help
     * @throws ISException
     *             erreur de recherche des helps
     */
    protected List<IValueObject> getHelpTexts(String aLang, ILoggedUser aSuperUser) throws ISException {
        IValueObject voText = VOFactory.getInstance().getVO(Entity.HELPTEXT.toString());
        voText.setProperty("htx_iso_lang_code", aLang);
        return RestUtil.getBPDelegate(Entity.HELPTEXT.toString()).getList(voText, aSuperUser, new DAOParameter(Name.ROWNUM_MAX, 0))
                .getListObject();
    }

    /**
     * Ajoute un libellé dans le bon position du map par rapport à arborisation de clé
     *
     * @param aKey
     *            Clé simple "ver_version" ou clé arborisé "Button.edit"
     * @param aLabel
     *            libellé associé avec la clé
     * @param aLabels
     *            map avec les libellés
     * @throws ISException
     *             erreur d'ajout de libellé. Arborisation non attendu.
     */
    @SuppressWarnings("unchecked")
    protected void accumulate(String aKey, String aLabel, Map<String, Object> aLabels) throws ISException {
        if (aLabel == null) {
            return;
        }

        String subKey = aKey.split("\\.")[0];

        if (subKey.equals(aKey)) {
            // ex. project.title.buildings=Bâtiments, aKey=buildings
            Object subValue = aLabels.computeIfAbsent(subKey, k -> aLabel);
            if (subValue.equals(aLabel)) {
                return;
            }
            // ex. project.title=Projet, aKey=title (aJson contient déjà
            // project.title.buildings=Bâtiments)
            Map<String, Object> subLabels = (Map<String, Object>) aLabels.get(subKey);
            subLabels.put("", aLabel);
            return;
        }
        Object value = aLabels.get(subKey);
        // ex. project.title.buildings, aKey=title.buildings
        if (value == null) {
            value = new HashMap<>();
            aLabels.put(subKey, value);
        } else if (value instanceof String) {
            // ex. project.title.main, aKey=title.main (aJson contient déjà
            // project.title=Projet)
            Map<String, Object> subLabels = new HashMap<>();
            subLabels.put("", value);
            aLabels.put(subKey, subLabels);
        } else if (!(value instanceof Map)) {
            throw new ISException("Childkey not JsonObject and not String. Label:" + aLabel + ". Key:" + aKey + ". Childkey:" + subKey
                    + "Child:" + aLabels.get(subKey));
        }
        accumulate(aKey.substring(aKey.indexOf('.') + 1), aLabel, (Map<String, Object>) aLabels.get(subKey));
    }

    /**
     * Ajoute les codes dans le json translation
     *
     * @param aLang
     *            langue
     * @param aLabels
     *            aLabels map avec toutes les traductions
     * @param aSuperUser
     *            the super user
     * @throws ISException
     *             erreur de consultation des tables code
     */
    protected void addCodes(String aLang, Map<String, Object> aLabels, ILoggedUser aSuperUser) throws ISException {
        List<IValueObject> listCodTexts = getCodeList(Entity.CODETEXT.toString(), aLang, aSuperUser);
        Map<String, Object> codes = new HashMap<>();
        Map<String, Object> codeslong = new HashMap<>();

        for (IValueObject vo : listCodTexts) {
            addCode((String) vo.getProperty("cod_fieldname"), vo.getProperty("cod_code").toString(),
                    (String) vo.getProperty("ctx_textcourt"), codes);
            addCode((String) vo.getProperty("cod_fieldname"), vo.getProperty("cod_code").toString(),
                    (String) vo.getProperty("ctx_textlong"), codeslong);
        }
        if (includeUsers()) {
            addUsers(codes, aSuperUser);
        }
        if (includeCommunes()) {
            addCommunes(codes, aSuperUser);
        }
        aLabels.put("iscode", codes);
        aLabels.put("iscodelong", codeslong);
    }

    /**
     *
     * @param aEntity
     *            nom de l'objet métier Code
     * @param aLang
     *            langue
     * @param aSuperUser
     *            the super user
     * @return liste de vos avec les codes
     * @throws ISException
     *             erreur de consultation de la table de codes
     */
    protected List<IValueObject> getCodeList(String aEntity, String aLang, ILoggedUser aSuperUser) throws ISException {
        IValueObject voCtx = ((VOFactory) iContext.getAttribute("VOFactory")).getVO(aEntity);
        voCtx.setProperty("ctx_lang", aLang);
        return RestUtil.getBPDelegate(aEntity).getList(voCtx, aSuperUser, new DAOParameter(Name.ROWNUM_MAX, 0)).getListObject();
    }

    /**
     * Ajoute les communes d'une table de communes parmi les codes
     *
     *
     * @param aCodes
     *            map avec les libellés des codes
     * @param aSuperUser
     *            the super user
     * @throws ISException
     *             erreur de consultation de la table de communes
     */
    protected void addCommunes(Map<String, Object> aCodes, ILoggedUser aSuperUser) throws ISException {
        List<IValueObject> communes = RestUtil.getBPDelegate(ch.inser.rest.util.Constants.Entity.COMMUNE.toString())
                .getList(((VOFactory) iContext.getAttribute("VOFactory")).getVO(ch.inser.rest.util.Constants.Entity.COMMUNE.toString()),
                        aSuperUser)
                .getListObject();
        String noCommuneField = ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getContextManager()
                .getProperty("commune.no.field");
        for (IValueObject vo : communes) {
            addCode("commune", vo.getProperty(noCommuneField).toString(), (String) vo.getProperty("com_nom"), aCodes);
        }
    }

    /**
     * Ajoute les noms des utilisateurs (Nom Prénom) parmi les libellés des codes. *
     *
     * @param aCodes
     *            json avec les libellés des codes
     * @param aSuperUser
     *            the super user
     * @throws ISException
     *             erreur de consultation de la table des utilisateurs
     */
    protected void addUsers(Map<String, Object> aCodes, ILoggedUser aSuperUser) throws ISException {
        List<IValueObject> listUsers = RestUtil.getBPDelegate(ch.inser.rest.util.Constants.Entity.USER.toString())
                .getList(((VOFactory) iContext.getAttribute("VOFactory")).getVO(ch.inser.rest.util.Constants.Entity.USER.toString()),
                        aSuperUser)
                .getListObject();
        for (IValueObject vo : listUsers) {
            addCode("user", vo.getId().toString(), vo.getProperty("use_nom") + " " + vo.getProperty("use_prenom"), aCodes);
        }
    }

    /**
     *
     * @return true s'il faut inclure Nom/Prénom des utilisateurs dans la liste de codes
     */
    private boolean includeUsers() {
        IContextManager ctx = ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getContextManager();
        return "true".equals(ctx.getProperty("usersAsCodes"));
    }

    /**
     *
     * @return true s'il faut inclure les communes dans la liste de codes
     */
    private boolean includeCommunes() {
        IContextManager ctx = ((ServiceLocator) iContext.getAttribute("ServiceLocator")).getContextManager();
        return "true".equals(ctx.getProperty("communesAsCodes"));
    }

    /**
     * Ajoute un code dans le json object de traductions
     *
     * @param aFieldname
     *            nom du champ
     * @param aCode
     *            valeur numérique du code
     * @param aLabel
     *            libellé du code
     * @param aLabels
     *            map hierarchique avec les traductions
     */
    @SuppressWarnings("unchecked")
    protected void addCode(String aFieldname, String aCode, String aLabel, Map<String, Object> aLabels) {
        Map<String, Object> field = null;
        if (aLabels.get(aFieldname) == null) {
            field = new HashMap<>();
            aLabels.put(aFieldname, field);
        } else {
            field = (Map<String, Object>) aLabels.get(aFieldname);
        }
        field.put(aCode, aLabel);
    }

}
