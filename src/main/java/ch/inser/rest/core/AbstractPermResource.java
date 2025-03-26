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

import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.rest.util.RestUtil;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Context;

/**
 * Abstract Permission resource, implemented with Inser token or OIDC token
 */
public abstract class AbstractPermResource {

    /**
     * Logger
     */
    @SuppressWarnings("unused")
    private static final Log logger = LogFactory.getLog(AbstractPermResource.class);

    /**
     * Le rest servlet context
     */
    @Context
    protected ServletContext iContext;

    /**
     * Ajouter les droits sur les menus
     *
     * @param aLoggedUser
     *            loggedUser utilisateur avec les droits sur les menus
     * @param aPermissions
     *            objet JSON avec les droits
     */
    protected void addMenus(ILoggedUser aLoggedUser, JsonObjectBuilder aPermissions) {

        // Création json pour les menus
        JsonObjectBuilder jsonMenus = Json.createObjectBuilder();

        // Traduire en json les droits sur les menus
        for (Map.Entry<String, Boolean> entry : aLoggedUser.getMapAuthMenu().entrySet()) {
            jsonMenus.add(entry.getKey(), entry.getValue());
        }

        // Ajout du paramètre menus dans le json
        aPermissions.add("menus", jsonMenus);
    }

    /**
     * Ajouter les droits sur les actions
     *
     * @param aLoggedUser
     *            loggedUser avec les droits sur les actions
     * @param aPermissions
     *            objet JSON avec les droits
     */
    protected void addActions(ILoggedUser aLoggedUser, JsonObjectBuilder aPermissions) {

        // Création json pour les actions
        JsonObjectBuilder actions = Json.createObjectBuilder();

        // Traduire en json les actions sur les menus
        for (Map.Entry<String, Map<String, Boolean>> mapMenu : aLoggedUser.getMapAuthAction().entrySet()) {
            // Nom du menu
            String menu = mapMenu.getKey();
            JsonArrayBuilder menuActions = Json.createArrayBuilder();
            // Parcourir toutes les actions
            for (Map.Entry<String, Boolean> mapAction : mapMenu.getValue().entrySet()) {
                if (Boolean.TRUE.equals(mapAction.getValue())) {
                    menuActions.add(mapAction.getKey());
                }
            }
            actions.add(menu, menuActions);
        }

        // Ajout des actions dans le json
        aPermissions.add("actions", actions);

    }

    /**
     * Ajoute les droits d'écriture et lecture des champs (optionnel)
     *
     *
     * Tous les droits sont initialisé par BPLoggedUser au moment de login:
     *
     * 1. Le UserGroup a une méthode init() qui cherche les droits sur menus, actions et champs dans la base de données et les met dans des
     * map
     *
     * 2. Le UserGroup est mis en cache pour réutilisation.
     *
     * 3. Les maps de droits sont copiés du UserGroup à LoggedUser.
     *
     * 4. Cette méthode (addFields) parcours le map de fieldAuths du LoggedUser et ajoute les droits dans le json, comme pour addMenus et
     * addActions
     *
     *
     * @param aPermissions
     *            json à remplir avec les permissions
     * @param aLoggedUser
     *            avec les droits sur les champs
     */
    protected void addFields(ILoggedUser aLoggedUser, JsonObjectBuilder aPermissions) {
        IBPDelegate bp = RestUtil.getBPDelegate(Entity.TABITEM.toString());
        if (bp == null) {
            return;
        }
        Map<String, Map<String, Integer>> fieldAuths = aLoggedUser.getMapAuthFields();
        JsonObjectBuilder jsonWrite = Json.createObjectBuilder();
        JsonObjectBuilder jsonRead = Json.createObjectBuilder();
        for (Map<String, Integer> objectFields : fieldAuths.values()) {
            for (Entry<String, Integer> field : objectFields.entrySet()) {
                jsonWrite.add(field.getKey(), field.getValue() > 0);
                jsonRead.add(field.getKey(), field.getValue() != null);
            }
        }
        aPermissions.add("writeFields", jsonWrite);
        aPermissions.add("readFields", jsonRead);
    }

}
