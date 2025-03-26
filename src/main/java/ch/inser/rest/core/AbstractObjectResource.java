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

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.DAOParameter.Name;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynamic.util.VOInfo;
import ch.inser.dynaplus.format.IFormatEngine.Format;
import ch.inser.jsl.exceptions.ISException;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.NotSupportedException;

/**
 * Abstraction of ObjectResource. Implemented according to token type: "Inser token" or OIDC
 */
public abstract class AbstractObjectResource extends AbstractResource {

    /** Id de l'objet métier */
    protected String iId;

    /**
     *
     * @param aContext
     *            Le rest servlet context
     * @param aObjectName
     *            nom de l'objet métier
     * @param aId
     *            Id de l'objet métier
     */
    protected AbstractObjectResource(ServletContext aContext, String aObjectName, String aId) {
        super(aContext, aObjectName);
        iId = aId;
    }

    /**
     * Convert the id from the current object property to the correct type.
     *
     * @param aInfo
     *            the value object information to retrieve the id type
     * @return the id value in the correct type according to the value object information
     */
    protected Object convertId(VOInfo aInfo) {
        switch (aInfo.getTypes().get(aInfo.getId())) {
            case DOUBLE:
                return Double.parseDouble(iId);
            case INTEGER:
                return Integer.parseInt(iId);
            case LONG:
                return Long.parseLong(iId);
            case STRING:
                return iId;
            default:
                throw new NotSupportedException("Id of type: '" + aInfo.getTypes().get(aInfo.getId()) + "' not supported");
        }
    }

    /**
     * Retrieves a record in a spcified format
     *
     * @param aId
     *            id
     * @param aLoggedUser
     *            user
     * @param aFormat
     *            format (pdf,json)
     * @param aLang
     *            language (de,fr,it,ro)
     * @return record in the requested format
     * @throws ISException
     *             error retrieving record
     */
    protected IDAOResult getRecord(String aId, ILoggedUser aLoggedUser, String aFormat, String aLang) throws ISException {
        if (aFormat == null) {
            return getRecord(aId, aLoggedUser);
        }
        return getBPDelegate().getRecord(aId, aLoggedUser, new DAOParameter(Name.RESULT_FORMAT, Format.PDF),
                new DAOParameter(Name.RESULT_LANG, aLang));
    }

    /**
     * Update the record using the business process, this method can be overwritten to add specific behaviors like transactions before
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
    protected IDAOResult update(IValueObject aValueObject, ILoggedUser aLoggedUser) throws ISException {
        return getBPDelegate().update(aValueObject, aLoggedUser);
    }

    /**
     * Update the record fields using the business process, this method can be overwritten to add specific behaviors like transactions
     * before and/or after the BP call.
     *
     * @param aValueObject
     *            the value object
     * @param aLoggedUser
     *            the logged user
     * @return the business process result
     * @throws ISException
     *             for any exception
     */
    protected IDAOResult updateFields(IValueObject aValueObject, ILoggedUser aLoggedUser) throws ISException {
        return getBPDelegate().updateFields(aValueObject, aLoggedUser);
    }

    /**
     * Delete a record using the business process, this method can be overwritten to add specific behaviors like transactions before and/or
     * after the BP call.
     *
     * @param aVo
     *            the record to be deleted
     * @param aLoggedUser
     *            the logged user
     * @return the business process result
     * @throws ISException
     *             for any exception
     */
    protected IDAOResult delete(IValueObject aVo, ILoggedUser aLoggedUser) throws ISException {
        return getBPDelegate().delete(convertId(aVo.getVOInfo()), aVo.getTimestamp(), aLoggedUser);
    }

}
