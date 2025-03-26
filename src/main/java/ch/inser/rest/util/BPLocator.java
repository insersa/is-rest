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

import ch.inser.dynaplus.bo.BPFactory;
import ch.inser.dynaplus.bo.IBusinessProcess;
import ch.inser.dynaplus.util.ILocator;
import ch.inser.dynaplus.util.IService;
import ch.inser.rest.core.GenericBPDelegate;

/**
 * BPLocator pour is-rest
 *
 * @author INSER SA *
 */
public class BPLocator implements ILocator, Serializable {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -2262502492118522307L;

    /**
     * Map des delegates
     */
    private transient Map<String, IService> iDelegates = new HashMap<>();

    /**
     *
     * @param name
     *            nom de l'objet métier
     */
    @Override
    public IService getService(String name) {
        IBusinessProcess bp = BPFactory.getInstance().getBP(name);
        if (bp == null) {
            return null;
        }
        return iDelegates.computeIfAbsent(name, k -> new GenericBPDelegate(bp));
    }
}
