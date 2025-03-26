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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynaplus.util.DAOLocator;
import ch.inser.dynaplus.util.ILocator;

/**
 * Singleton livrant les locator et le ContextManager pour is-rest, doit être
 * initialisé par fichier de configuration.
 *
 * @author INSER SA *
 */
public class ServiceLocator implements Serializable {

    /**
     * UID
     */
    private static final long serialVersionUID = -1601168254263290328L;

    /**
     * Context manager
     */
    private transient IContextManager iContextManager;

    /**
     * Locators bp et dao
     */
    private transient Map<String, ILocator> iLocators = new HashMap<>();

    /**
     * The super user.
     */
    private transient ILoggedUser iSuperUser;

    /**
     * Instance singleton
     */
    private static ServiceLocator cInstance = new ServiceLocator();

    /**
     * Constructeur privé
     */
    private ServiceLocator() {
        iLocators.put("bp", new BPLocator());
        iLocators.put("dao", new DAOLocator());
    }

    /**
     * Ajoute un locator
     * 
     * @param aName
     *            nom de locator
     * @param aLocator
     *            le locator
     */
    public void addLocator(String aName, ILocator aLocator) {
        iLocators.put(aName, aLocator);
    }

    /**
     * @return instance singleton de service locator
     */
    public static ServiceLocator getInstance() {
        return cInstance;
    }

    /**
     *
     * @return context manager
     */
    public IContextManager getContextManager() {
        return iContextManager;
    }

    /**
     * @param aContextManager
     *            le context manager
     */
    public void setContextManager(IContextManager aContextManager) {
        iContextManager = aContextManager;
    }

    /**
     * Retourne le service locator propre à une couche
     *
     * @param name
     *            "bp" ou "dao"
     * @return BPLocator ou DAOLocator
     */
    public ILocator getLocator(String name) {
        return iLocators.get(name);
    }

    /**
     * Get the super user.
     *
     * @return the super user
     */
    public ILoggedUser getSuperUser() {
        return iSuperUser;
    }

    /**
     * Set the super user.
     *
     * @param aSuperUser
     *            the super user
     */
    public void setSuperUser(ILoggedUser aSuperUser) {
        iSuperUser = aSuperUser;
    }

}
