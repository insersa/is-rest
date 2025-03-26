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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.DAOParameter.Name;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.dynaplus.vo.VOFactory;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.util.ServiceLocator;

import jakarta.servlet.ServletContext;
import jakarta.ws.rs.core.Context;

/**
 * Code resource implemented with Inser token or OIDC token
 */
public abstract class AbstractCodeResource {

    /**
     * Le rest servlet context
     */
    @Context
    protected ServletContext iContext;

    /**
     * Add the codes to the map of codes.
     *
     * @param aCodes
     *            the map of codes
     * @param aUser
     *            the user
     * @throws ISException
     *             error reading the codes
     */
    protected void addCodes(Map<String, List<String>> aCodes, ILoggedUser aUser) throws ISException {
        // Search the valid codes
        IValueObject qVo = getVO(getEntity());
        qVo.setProperty("cod_valide", true);
        List<IValueObject> codelist = getBPDelegate(getEntity()).getList(qVo, aUser, new DAOParameter(Name.ROWNUM_MAX, 0)).getListObject();

        // Fill the map
        for (IValueObject code : codelist) {
            addCode(aCodes, code);
        }
    }

    /**
     * Add a code value to a codelist
     *
     * @param aCodes
     *            map of codes (fieldname -> codelist) to be converted to json at the end
     * @param aCode
     *            vo code with a code value
     */
    protected void addCode(Map<String, List<String>> aCodes, IValueObject aCode) {
        String fieldname = (String) aCode.getProperty("cod_fieldname");

        if (!aCodes.keySet().contains(fieldname)) {
            aCodes.put(fieldname, new ArrayList<>());
        }
        aCodes.get(fieldname).add((String) aCode.getProperty("cod_code"));
    }

    /**
     * Get the ServiceLocator.
     *
     * @return the service locator
     */
    protected ServiceLocator getServiceLocator() {
        return (ServiceLocator) iContext.getAttribute("ServiceLocator");
    }

    /**
     * Get a BP delegate.
     *
     * @param aName
     *            the object delegate name
     * @return a BP delegate
     */
    protected IBPDelegate getBPDelegate(String aName) {
        return (IBPDelegate) getServiceLocator().getLocator("bp").getService(aName);
    }

    /**
     * Get a VO.
     *
     * @param aName
     *            name of business object
     *
     * @return the VO
     */
    protected IValueObject getVO(String aName) {
        return ((VOFactory) iContext.getAttribute("VOFactory")).getVO(aName);
    }

    /**
     *
     * @return Nom de l'obet métier code, par défaut "Code"
     */
    protected String getEntity() {
        return Entity.CODE.toString();
    }
}
