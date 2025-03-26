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

import java.sql.Timestamp;
import java.util.List;

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.bo.IBusinessProcess;
import ch.inser.dynaplus.util.Constants.Mode;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.jsl.list.ListHandler.Sort;

/**
 * BPDelegate pour is-rest
 *
 * @author INSER SA *
 */
public abstract class AbstractBPDelegate implements IBPDelegate {

    /**
     * UID
     */
    private static final long serialVersionUID = 2523816184683104271L;

    /**
     * Business process
     */
    private transient IBusinessProcess iBp;

    /**
     *
     * @param aBP
     *            business process
     */
    protected AbstractBPDelegate(IBusinessProcess aBP) {
        iBp = aBP;
    }

    @Override
    public IDAOResult create(IValueObject valueObject, ILoggedUser user) throws ISException {
        return iBp.create(valueObject, user);
    }

    @Override
    public IDAOResult delete(Object id, Timestamp timestamp, ILoggedUser user, DAOParameter... aParameter) throws ISException {
        return iBp.delete(id, timestamp, user, aParameter);
    }

    @Override
    public IDAOResult getField(Object id, String fieldName) throws ISException {
        return iBp.getField(id, fieldName);
    }

    @Override
    public IDAOResult getFieldsRequest(IValueObject aVo, String aFieldName, DAOParameter... aParameters) throws ISException {
        return iBp.getFieldsRequest(aVo, aFieldName, aParameters);
    }

    @Override
    public IDAOResult getFieldsRequest(IValueObject aVo, String aFieldName, ILoggedUser aUser, DAOParameter... aParameters)
            throws ISException {
        return iBp.getFieldsRequest(aVo, aFieldName, aUser, aParameters);
    }

    @Override
    public IValueObject getInitVO(ILoggedUser user) throws ISException {
        return iBp.getInitVO(user);
    }

    @Override
    public IValueObject getInitVO(Mode mode, ILoggedUser user) throws ISException {
        return iBp.getInitVO(mode, user);
    }

    @Override
    public IDAOResult getRecord(Object id, ILoggedUser user, DAOParameter... aParameters) throws ISException {
        return iBp.getRecord(id, user, aParameters);
    }

    @Override
    public IDAOResult getTimestamp(Object id, ILoggedUser user) throws ISException {
        return iBp.getTimestamp(id, user);
    }

    @Override
    public IDAOResult update(IValueObject valueObject, ILoggedUser user) throws ISException {
        return iBp.update(valueObject, user);
    }

    @Override
    public IDAOResult update(List<IValueObject> aRecords, List<IValueObject> aDeletes, ILoggedUser aUser, DAOParameter... aParameter)
            throws ISException {
        return iBp.update(aRecords, aDeletes, aUser, aParameter);
    }

    @Override
    public IDAOResult getList(IValueObject aVo, ILoggedUser aUser, DAOParameter... aParameters) throws ISException {
        return iBp.getList(aVo, aUser, aParameters);
    }

    @Override
    public IDAOResult updateField(List<Object> aLstId, String aFieldName, List<Object> aLstValue, ILoggedUser aUser) throws ISException {
        return iBp.updateField(aLstId, aFieldName, aLstValue, aUser);
    }

    /**
     * Retourne la clé du tri par défaut
     */
    @Override
    public String getDefaultOrderKey() {
        return iBp.getDefaultOrderKey();
    }

    /**
     * Retourne l'orientation du tri par défaut
     */
    @Override
    public Sort getDefaultSortOrder() {
        return iBp.getDefaultSortOrder();
    }

    @Override
    public IDAOResult updateFields(Object id, String[] aFieldNames, Object[] aValues) throws ISException {
        return iBp.updateFields(id, aFieldNames, aValues);
    }

    @Override
    public IDAOResult updateFields(IValueObject aValueObject, ILoggedUser aUser) throws ISException {
        return iBp.updateFields(aValueObject, aUser);
    }

    @Override
    public IDAOResult getListCount(IValueObject vo, ILoggedUser aUser, DAOParameter... aParameters) throws ISException {
        return iBp.getListCount(vo, aUser, aParameters);
    }

    /**
     *
     * @return le business process
     */
    protected IBusinessProcess getBP() {
        return iBp;
    }

}
