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

import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynaplus.bo.IBusinessProcess;
import ch.inser.jsl.exceptions.ISException;

/**
 * BP delegate pour les services REST pour le CRUD de isEJaWa
 *
 * @author INSER SA *
 */
public class GenericBPDelegate extends AbstractBPDelegate {

    /**
     * UID
     */
    private static final long serialVersionUID = -616538078984502154L;

    /**
     *
     * @param aBP
     *            business process
     */
    public GenericBPDelegate(IBusinessProcess aBP) {
        super(aBP);
    }

    @Override
    public Object executeMethode(String aNameMethode, Object anObject, ILoggedUser aUser) throws ISException {
        return getBP().executeMethode(aNameMethode, anObject, aUser);
    }
}
