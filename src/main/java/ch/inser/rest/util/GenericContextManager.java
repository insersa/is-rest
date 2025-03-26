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

import java.util.ArrayList;
import java.util.List;

import javax.cache.CacheManager;

/**
 * @author INSER SA *
 */
public class GenericContextManager extends ch.inser.dynaplus.util.AbstractContextManager {

    /**
     * Serial Version UID
     */
    private static final long serialVersionUID = 3588992830423995532L;

    /**
     * Manager pour la gestion du cache
     */
    private transient CacheManager iCacheManager;

    /**
     * Liste des objets métiers autorisés dans service REST ObjectsResource
     */
    private List<String> iObjectsResAutorized = new ArrayList<>();

    @Override
    public CacheManager getCacheManager() {
        return iCacheManager;
    }

    /**
     *
     * @param manager
     *            the cache manager
     */
    @Override
    public void setCacheManager(CacheManager manager) {
        iCacheManager = manager;
    }

    @Override
    public List<String> getObjectsResAutorized() {
        return iObjectsResAutorized;
    }

    @Override
    public void setObjectsResAutorized(List<String> aObjectsResAutorized) {
        iObjectsResAutorized = aObjectsResAutorized;
    }

}
