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

package ch.inser.rest.init;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.rest.util.ServiceLocator;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;

/**
 * Gère le scheduler pour les processus asynchrones
 *
 * @author INSER SA *
 */
public class QuartzServlet extends HttpServlet {

    /**
     * Serial Version UID
     */
    private static final long serialVersionUID = 7125755254027516624L;

    /**
     * Définition du Logger utilisé pour le logging.
     */
    private static final Log logger = LogFactory.getLog(QuartzServlet.class);

    /**
     * Initialisation du scheduler selon un fichier de config Quartz.
     */
    @Override
    public void init() throws ServletException {
        String quartzConfig = getQuartzProperties();
        Properties quartzProps = readProperties(quartzConfig);
        initScheduler(quartzProps);
    }

    /**
     * Initiliase le Quartz scheduler
     *
     * @param aQuartzProps
     *            propriétés du Quartz
     */
    private void initScheduler(Properties aQuartzProps) {
        IContextManager ctx = ServiceLocator.getInstance().getContextManager();
        try {
            if (aQuartzProps != null) {
                ctx.initScheduler(aQuartzProps);
            } else {
                ctx.initScheduler();
            }
            logger.info("Quartz scheduler initialisé.");
        } catch (SchedulerException e) {
            logger.error("Erreur de initialisation du Quartz scheduler", e);
        }

    }

    /**
     * Lis les propriétés de Quartz.
     *
     * JNDI: prend le nom JNDI défini dans le fichier de propriétés générale de l'application
     *
     * @param aQuartzConfig
     *            fichier de propriétés de Quartz (sans JNDI)
     * @return propriétés Quartz
     */
    private Properties readProperties(String aQuartzConfig) {
        Properties quartzProps = null;
        if (aQuartzConfig != null) {
            try (InputStream is = new FileInputStream(new File(aQuartzConfig))) {
                quartzProps = new Properties();
                quartzProps.load(is);
                String jndiUrl = ServiceLocator.getInstance().getContextManager().getProperty("datasourceName");
                String quartzJndiKey = quartzProps.getProperty("org.quartz.jobStore.dataSource");
                quartzProps.put("org.quartz.dataSource." + quartzJndiKey + ".jndiURL", jndiUrl);
            } catch (FileNotFoundException e) {
                logger.error("Erreur de recuperation du fichier de propriétés Quartz", e);
            } catch (IOException e) {
                logger.error("Erreur de lecture du fichier de propriété Quartz", e);
            }
        }

        return quartzProps;
    }

    /**
     * Cherche le filepath pour le fichier de propriétés de Quartz
     *
     * Les possibilités:
     *
     * 1. Définir la propriété "quartz.config=D:/.../mon_quartz.properties" dans le fichier de config de l'application
     *
     * 2. Stocker le fichier nommé quartz.properties dans la répértoire config
     *
     * 3. Définir la propriété de système "org.quartz.properties"
     *
     * @return le filepath pour le fichier de propriétés de Quartz
     */
    private String getQuartzProperties() {
        IContextManager ctx = ServiceLocator.getInstance().getContextManager();

        String quartzConfig = ctx.getProperty("quartz.config");
        if (quartzConfig == null) {
            String configDir = ctx.getProperty("configDir");
            String config = configDir + File.separator + "quartz.properties";
            File file = new File(config);
            if (file.exists()) {
                quartzConfig = config;
            }
        }
        return quartzConfig;
    }

    /**
     * Shut down ce servlet en libérant toute ressource ayant été allouée pendant l'initialisation.
     */
    @Override
    public void destroy() {
        IContextManager ctx = ServiceLocator.getInstance().getContextManager();
        Scheduler scheduler = ctx.getScheduler();
        try {
            scheduler.shutdown(true);
            logger.info("Destruction du servlet d'initialisation de Quartz scheduler");
        } catch (SchedulerException e) {
            logger.error("Erreur de fermeture du Quartz scheduler", e);
        }
    }
}
