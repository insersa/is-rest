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

import java.util.List;

import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.jsl.exceptions.ISException;

import jakarta.servlet.ServletContext;

/**
 * Abstract ObjectNamesResource: Implemented according to token type: "Inser token" or OIDC
 */
public abstract class AbstractObjectNamesResource extends AbstractResource {

    /**
     *
     * @param aContext
     *            servlet config
     * @param aObjectName
     *            business object name
     */
    protected AbstractObjectNamesResource(ServletContext aContext, String aObjectName) {
        super(aContext, aObjectName);
    }

    /**
     * Create the record using the business process, this method can be overwritten to add specific behaviors like transactions before
     * and/or after the BP call.
     *
     * @param aValueObject
     *            the value object
     * @param aLoggedUser
     *            the logged user
     * @return the business process result
     * @throws ISException
     *             for any exception
     */
    protected IDAOResult create(IValueObject aValueObject, ILoggedUser aLoggedUser) throws ISException {
        return getBPDelegate().create(aValueObject, aLoggedUser);
    }

    /**
     * Get the count from the business process, this method can be overwritten to add specific behaviors like transactions before and/or
     * after the BP call.
     *
     * @param aValueObject
     *            the value object
     * @param aLoggedUser
     *            the logged user
     * @return the business process result
     * @throws ISException
     *             for any exception
     */
    protected IDAOResult getCount(IValueObject aValueObject, ILoggedUser aLoggedUser) throws ISException {
        return getBPDelegate().getListCount(aValueObject, aLoggedUser);
    }

    /**
     * Update and delete the records using the business process, this method can be overwritten to add specific behaviors like transactions
     * before and/or after the BP call.
     *
     * @param aRecords
     *            the records to add or/and update if necessary
     * @param aDeletes
     *            the records to delete
     * @param aLoggedUser
     *            the logged user
     * @return the business process result
     * @throws ISException
     *             for any exception
     */
    protected IDAOResult updateList(List<IValueObject> aRecords, List<IValueObject> aDeletes, ILoggedUser aLoggedUser) throws ISException {
        return getBPDelegate().update(aRecords, aDeletes, aLoggedUser);
    }

}
