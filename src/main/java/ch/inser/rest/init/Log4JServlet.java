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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.jsl.logger.Log4JNDC;
import ch.inser.rest.util.ServiceLocator;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;

/**
 * Initialisation du servlet de log. <br>
 * Les paramètres de servlet suivants sont utilisé:
 * <ul>
 * <li>logDirPropName: Le nom de la propriété contenant le directory de logging (obligatoire).</li>
 * <li>un paramètre de nom "logDirPropName" contenant le chemin absolu du directory de log (facultatif). Si une propriété de la
 * configuration de l'application de nom "logDirPropName" est présente, c'est cette valeur qui est considérée.</li>
 * <li>configLogFileName: Le nom du fichier de configuration du log (obligatoire).</li>
 * </ul>
 *
 *
 * @author INSER SA *
 */
public class Log4JServlet extends HttpServlet {

    /** serialVersion */
    private static final long serialVersionUID = 1L;

    @Override
    public void init() throws ServletException {

        initLogDir();
        String propfile = getPropFile();

        // Choix de logging: logback, log4j, jul-to-slf
        try {
            initLog4j(propfile);
            initLogback(propfile);
            initJulBridge();
        } catch (SecurityException e) {
            throw new ServletException("Problème de sécurité de configuration du logging", e);
        } catch (NoSuchMethodException e) {
            throw new ServletException("Methode pour configurer le logging pas trouvé", e);
        } catch (IllegalArgumentException e) {
            throw new ServletException("Paramètres du method de configuration du logging sont faux", e);
        } catch (IllegalAccessException e) {
            throw new ServletException("Pas de droit de configurer le logging", e);
        } catch (InvocationTargetException e) {
            throw new ServletException("Erreur d'invocation du methode de configuration de logging", e);
        } catch (InstantiationException e) {
            throw new ServletException("Erreur de création d'objet de configuration de logging", e);
        }

        // Injection du NDC
        ServiceLocator.getInstance().getContextManager().setNdc(new Log4JNDC());
        Log logger = LogFactory.getLog(Log4JServlet.class);
        logger.info("Logger initialisé");

    }

    /**
     * Log4j (slf4j-log4j.jar)
     *
     * @param aPropFile
     *            le fichier log4j.xml yc path
     * @throws NoSuchMethodException
     *             Methode pour configurer le logging pas trouvé
     * @throws SecurityException
     *             Problème de sécurité de configuration du logging
     * @throws IllegalAccessException
     *             Pas de droit de configurer le logging
     * @throws IllegalArgumentException
     *             Paramètres du method de configuration du logging sont faux
     * @throws InvocationTargetException
     *             Erreur d'invocation du methode de configuration de logging
     */
    private void initLog4j(String aPropFile) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        try {
            Class<?> propConfig = Class.forName("org.apache.logging.log4j.LogManager");
            if (propConfig != null) {
                Method method1 = propConfig.getMethod("getContext", boolean.class);
                Object context = method1.invoke(null, false);
                Method method2 = context.getClass().getMethod("setConfigLocation", URI.class);
                method2.invoke(context, new URI(aPropFile));
            }
        } catch (@SuppressWarnings("unused") ClassNotFoundException | IllegalArgumentException | URISyntaxException e) {
            // No logback binder installed. Do nothing, move on to next.
        }
    }

    /**
     * Bridge entre java.util.logging et slf4j (jul-to-slf4j.jar)
     *
     *
     * @throws NoSuchMethodException
     *             Methode pour configurer le logging pas trouvé
     * @throws SecurityException
     *             Problème de sécurité de configuration du logging
     * @throws IllegalAccessException
     *             Pas de droit de configurer le logging
     * @throws IllegalArgumentException
     *             Paramètres du method de configuration du logging sont faux
     * @throws InvocationTargetException
     *             Erreur d'invocation du methode de configuration de logging
     */
    private void initJulBridge() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

        try {
            Class<?> cl = Class.forName("org.slf4j.bridge.SLF4JBridgeHandler");
            if (cl != null) {
                Method method = cl.getMethod("install", (Class[]) null);
                method.invoke(null, (Object[]) null);
            }
        } catch (@SuppressWarnings("unused") ClassNotFoundException e) {
            // No logback binder installed. Do nothing, move on to next.
        }
    }

    /**
     * Initialise Logback (logback-classic.jar) comme logger si on trouve le api dans le lib
     *
     * @param aPropFile
     *            le fichier logback.xml yc path
     * @throws NoSuchMethodException
     *             Methode pour configurer le logging pas trouvé
     * @throws SecurityException
     *             Problème de sécurité de configuration du logging
     * @throws IllegalAccessException
     *             Pas de droit de configurer le logging
     * @throws IllegalArgumentException
     *             Paramètres du method de configuration du logging sont faux
     * @throws InvocationTargetException
     *             Erreur d'invocation du methode de configuration de logging
     * @throws InstantiationException
     *             Erreur de création d'objet de configuration de logging
     */
    private void initLogback(String aPropFile)
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        try {
            Class<?> joranCl = Class.forName("ch.qos.logback.classic.joran.JoranConfigurator");
            if (joranCl != null) {
                Class<?> loggerFactory = Class.forName("org.slf4j.LoggerFactory");
                Method getContext = loggerFactory.getMethod("getILoggerFactory", (Class[]) null);
                Object contextObj = getContext.invoke(null, (Object[]) null);
                Class<?> contextCl = Class.forName("ch.qos.logback.core.Context");
                Constructor<?> joranCon = joranCl.getConstructor((Class[]) null);
                Object joranObj = joranCon.newInstance();
                Method setContext = joranCl.getMethod("setContext", contextCl);
                setContext.invoke(joranObj, contextObj);
                Method reset = contextObj.getClass().getMethod("reset", (Class[]) null);
                reset.invoke(contextObj, (Object[]) null);
                Method doConfigure = joranCl.getMethod("doConfigure", String.class);
                doConfigure.invoke(joranObj, aPropFile);
            }
        } catch (@SuppressWarnings("unused") ClassNotFoundException e) {
            // No logback binder installed. Do nothing, move on to next.
        }
    }

    /**
     *
     * @return path pour le fichier log4j.xml ou logback.ml
     */
    private String getPropFile() {
        String propfile = ServiceLocator.getInstance().getContextManager().getProperty("configLogFileName");
        IContextManager ctx = ServiceLocator.getInstance().getContextManager();
        // Si config dans package war
        if ("true".equalsIgnoreCase(ctx.getProperty("configInPackage"))) {
            if (ctx.getProperty("configLogFileName") != null) {
                // prendre configLogFileName du fichier de propriétés
                propfile = getServletContext().getRealPath("") + File.separator + ctx.getProperty("configLogFileName");
            } else {
                // prendre paramètre du web.xml si existant
                propfile = getServletContext().getRealPath("") + File.separator + getInitParameter("configLogFileName");
            }

        }

        if (propfile == null) {
            // Paramètre du web.xml
            propfile = getInitParameter("configLogFileName");
        }

        return propfile;
    }

    /**
     * Initialise le log dir dans context manager
     *
     * @throws ServletException
     *             le paramètre logDirPropName manque.
     */
    private void initLogDir() throws ServletException {
        String logDirPropName = getInitParameter("logDirPropName");
        if (logDirPropName == null) {
            throw new ServletException("Impossible de récupérer le paramètre de Servlet 'logDirPropName'");
        }
        String logdir = ServiceLocator.getInstance().getContextManager().getProperty(logDirPropName);
        if (logdir == null) {
            logdir = getInitParameter(logDirPropName);
        }
        if (logdir != null) {
            // Paramètre donnée au démarrage du serveur applicatif
            System.setProperty(logDirPropName, logdir);
            ServiceLocator.getInstance().getContextManager().getProperties().put("log.dir", logdir);
        }
    }

    @Override
    public void destroy() {
        // Désinstallation du brigde autrement erreur au stop du serveur
        // java.util.logging et slf4j (jul-to-slf4j.jar).
        Class<?> cl;
        try {
            cl = Class.forName("org.slf4j.bridge.SLF4JBridgeHandler");

            if (cl != null) {
                Method method = cl.getMethod("uninstall", (Class[]) null);
                method.invoke(null, (Object[]) null);
            }
        } catch (@SuppressWarnings("unused") ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            // Nothing to do while the logger is not available
        }
    }
}
