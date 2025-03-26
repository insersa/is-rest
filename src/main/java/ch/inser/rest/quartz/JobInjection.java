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

package ch.inser.rest.quartz;

import java.io.Serializable;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynaplus.bo.BOFactory;
import ch.inser.dynaplus.bo.BPFactory;
import ch.inser.dynaplus.quartz.IJobInjection;
import ch.inser.dynaplus.vo.IVOFactory;
import ch.inser.dynaplus.vo.VOFactory;
import ch.inser.rest.util.ServiceLocator;

/**
 * Classe pour donner les Quartz jobs accès au ContextManager, BOFactory et
 * VOFactory. L'objet est crée au moment où on défini le job et il est passé en
 * paramètre dans le dataMap du job detail. (voir wiki)
 *
 * @author INSER SA *
 */
public class JobInjection implements IJobInjection, Serializable {

    /**
     * Nom d'utilisateur
     */
    private String iUsername;

    /**
     * Logged user
     */
    private transient ILoggedUser iUser;

    /**
     * Serialization id
     */
    private static final long serialVersionUID = -1810832691796382138L;

    @Override
    public IContextManager getContextManager() {
        return ServiceLocator.getInstance().getContextManager();
    }

    @Override
    public BPFactory getBPFactory() {
        return BPFactory.getInstance();
    }

    @Override
    public BOFactory getBOFactory() {
        return BOFactory.getInstance();
    }

    @Override
    public IVOFactory getVOFactory() {
        return VOFactory.getInstance();
    }

    @Override
    public String getUsername() {
        return iUsername;
    }

    @Override
    public void setUsername(String aUsername) {
        iUsername = aUsername;
    }

    @Override
    public ILoggedUser getUserstart() {
        return iUser;
    }

    @Override
    public void setUserstart(ILoggedUser aUser) {
        iUser = aUser;
    }

}
