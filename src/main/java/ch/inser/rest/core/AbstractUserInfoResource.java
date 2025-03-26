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

import javax.cache.Cache;

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.util.ServiceLocator;

import jakarta.json.JsonObjectBuilder;
import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Context;

/**
 * Abstract User info resource. Implemeted with Inser token or OIDC token
 */
public abstract class AbstractUserInfoResource {

    /**
     * Le rest servlet context
     */
    @Context
    protected ServletContext iContext;

    /**
     * Ajoute les infos Service d'enquête, commune, source de données
     *
     * @param aUser
     *            nom d'utilisateur
     * @param aJson
     *            données de permission
     * @throws ISException
     *             erreur de consultation de service d'enquête
     */
    @SuppressWarnings("unused")
    protected void addInfo(ILoggedUser aUser, JsonObjectBuilder aJson) throws ISException {
        // Implémenter dans la classe spécialisée métier, selon besoin
    }

    /**
     * Donne le LoggedUser pour un nom d'utilisateur donné
     *
     * @param aUsername
     *            nom d'utilisateur
     * @param aRefresh
     *            true s'il faut rafraichir le logged user dans le cache
     * @return LoggedUser
     * @throws ISException
     *             erreur d'init de l'utilisateurs
     */
    protected ILoggedUser getLoggedUser(String aUsername, boolean aRefresh) throws ISException {
        ServiceLocator serviceLocator = (ServiceLocator) iContext.getAttribute("ServiceLocator");

        Cache<String, ILoggedUser> cache = serviceLocator.getContextManager().getCacheManager().getCache("userCache", String.class,
                ILoggedUser.class);
        if (!cache.containsKey(aUsername) || aRefresh) {
            ILoggedUser newUser = (ILoggedUser) ((IBPDelegate) serviceLocator.getLocator("bp").getService("LoggedUser"))
                    .executeMethode("initLoggedUser", aUsername, serviceLocator.getSuperUser());
            cache.put(aUsername, newUser);
        }

        return cache.get(aUsername);
    }
}
