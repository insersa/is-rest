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

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.util.SchemaInfo;
import ch.inser.dynamic.util.VOInfo;
import ch.inser.dynaplus.util.ILocator;
import ch.inser.dynaplus.util.IService;
import ch.inser.jsl.exceptions.ISException;

/**
 * Locator pour accéder aux configs propres à la couche REST
 *
 * @author INSER SA *
 */
public class RESTLocator implements ILocator, Serializable {

    /**
     * UID
     */
    private static final long serialVersionUID = -9156989194205245320L;

    /** Logger */
    private static final Log logger = LogFactory.getLog(RESTLocator.class);

    /** ContextManager */
    private transient IContextManager iCtx;

    /**
     * VOInfos avec les configs propres à la couche REST
     */
    private Map<String, VOInfo> iVOInfos = new HashMap<>();

    /**
     *
     * @param aName
     *            nom de l'objet métier
     * @return les configurations propres à la couche REST
     */
    public VOInfo getVOInfo(String aName) {
        return iVOInfos.get(aName);
    }

    /**
     * Charge les configurations REST en mémoire
     *
     * @throws ISException
     *             erreur d'initialisation
     */
    public void init() throws ISException {
        logger.debug("INIT");
        String configDir = iCtx.getProperty("configDir");
        URL urlprop = null;

        if (configDir == null) {
            return;
        }
        File file = new File(configDir + File.separator + "rest");
        if (file.list() == null) {
            return;
        }

        for (String str : file.list()) {
            if (!str.endsWith(".xsd")) {
                continue;
            }
            try {
                if (configDir.charAt(0) == '/' || configDir.charAt(0) == '.') {
                    // URL pour UNIX
                    urlprop = new URL("file://" + configDir + File.separator + "rest" + File.separator + str);
                } else {
                    // URL pour windows
                    urlprop = new URL("file:///" + configDir + File.separator + "rest" + File.separator + str);
                }
                add(new SchemaInfo(urlprop));
            } catch (Exception e) {
                logger.error("Problem by initialisation of REST config : " + str, e);
                throw new ISException(e);
            }
        }
    }

    /**
     * Ajoute un VOInfo pour chaque objet métier du schema
     *
     * @param aSchema
     *            schema info pour un fichier de config .xsd
     */
    public void add(SchemaInfo aSchema) {
        for (String obj : aSchema.getVONameSet()) {
            String str = obj;
            VOInfo voInfo = aSchema.getVOInfo(str);
            iVOInfos.put(str, voInfo);
            logger.info("REST config  '" + str + "' added");
        }
    }

    /**
     *
     * @param aCtx
     *            contextmanager
     */
    public void setContextManager(IContextManager aCtx) {
        iCtx = aCtx;
    }

    @Override
    public IService getService(String aName) {
        return null;
    }

}
