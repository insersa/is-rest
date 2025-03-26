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

package ch.inser.rest.auth;

import java.util.HashMap;
import java.util.Map;

import ch.inser.dynamic.common.ILoggedUser;

/**
 * Utilisateurs authentifiés en cache
 *
 * @author INSER SA *
 */
public class RESTActiveUsers {

    /**
     * L'instance des utilisateurs actifs
     */
    private static final RESTActiveUsers cInstance = new RESTActiveUsers();

    /** Map des utilisateurs actifs */
    private Map<String, ILoggedUser> iUsers;

    /**
     * Constructeur
     */
    private RESTActiveUsers() {
        iUsers = new HashMap<>();
    }

    /**
     *
     * @return les utilisateurs actifs logué par service REST
     */
    public static RESTActiveUsers getInstance() {
        return cInstance;
    }

    /**
     * Retourne l'utilisateur WS, null s'il n'est pas trouvable
     *
     * @param username
     *            nom d'utilisateur
     * @return L'objet LoggedUser avec les droits
     */
    public ILoggedUser getUser(String username) {
        return iUsers.get(username);
    }

    /**
     * @param aUser
     *            Objet LoggedUser authentifié
     */
    public void addUser(ILoggedUser aUser) {
        iUsers.put(aUser.getUsername(), aUser);
    }

}
