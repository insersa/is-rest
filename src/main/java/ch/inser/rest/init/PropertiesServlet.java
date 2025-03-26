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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.rest.util.GenericContextManager;
import ch.inser.rest.util.ServiceLocator;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;

/**
 * Initialisation du servlet de properties. <br>
 * Les paramètres de servlet suivants sont utilisé:
 * <ul>
 * <li>configDirPropName: Le nom de la propriété contenant le directory de configuration (obligatoire).</li>
 * <li>un paramètre de nom "configDirPropName" contenant le chemin absolu du directory de configuration (facultatif). Si une propriété
 * système de nom "configDirPropName" est présente, c'est cette valeur qui est considérée.</li>
 * <li>configFileName: Le nom du fichier de propriété (obligatoire).</li>
 * </ul>
 *
 * @author INSER SA *
 */
public class PropertiesServlet extends HttpServlet {

    /** serialVersion */
    private static final long serialVersionUID = -7420366651401615986L;

    @Override
    public void init() throws ServletException {

        // nom du parametre a inserer dans le fichier de isEJaWa.properties
        String configDirPropName = getInitParameter("configDirPropName");
        if (configDirPropName == null) {
            throw new ServletException("Impossible de récupérer le paramètre de Servlet 'configDirPropName'");
        }

        // Recherche du path du dossier de configuration utilisé par
        // l'application
        // Paramètre donné au lancement du serveur applicatif
        String configDir = System.getProperty(configDirPropName);

        // Paramètre donné du web.xml de l'application
        String configInPackage = "false";
        if (configDir == null) {
            // On essaye avec la propriété d'initialisation
            configDir = getInitParameter(configDirPropName);
            if (getInitParameter("configInPackage") != null) {
                configInPackage = getInitParameter("configInPackage");
            }
        }
        // Quitte l'application si le configDir n'est pas parametré
        if (configDir == null) {
            throw new ServletException(
                    "Impossible de récupérer la propriété système ou le paramètre de Servlet '" + configDirPropName + "'");
        }

        // -- Lecture des propriétés du fichier de base
        String fileName = getInitParameter("configFileName");
        if (fileName == null) {
            throw new ServletException("Impossible de récupérer le paramètre de Servlet 'configFileName'");
        }

        Properties prop = getProperties(configDir, fileName, configInPackage);

        // -- Lecture des propriétés du fichier selon environnement
        String configEnvPropName = getInitParameter("configEnvPropName");
        // uniquement lire le fichier si le paramètre est existant dans web.xml
        if (configEnvPropName != null) {
            // Nom du fichier properties pour cet environnement
            String envFileName = System.getProperty(configEnvPropName);
            // Lecture du fichier
            Properties propEnv = getProperties(configDir, envFileName, configInPackage);
            // Supprimer l'url du fichier d'env
            propEnv.remove("configUrl");
            // Mise des propriétés environnement dans propriétés globales
            prop.putAll(propEnv);
        }

        // Replace properties with environment variables values
        environment(prop);

        try {
            createContextManager(prop);
            // Surcharge les propriétés du fichier de config s'il y a une table
            // de propriétés
            if ("true".equalsIgnoreCase((String) prop.get("function.propertiesDB"))) {
                ServiceLocator.getInstance().getContextManager().addProperties(propertiesInDB());
            }

        } catch (SQLException e) {
            throw new ServletException("Erreur de chargement des propriétés de la table T_PROPERTIES", e);
        }
    }

    /**
     * Chargement du fichier du projet <projet>.properties de dossier config vant l'écriture des paramètre BIRT, nécessaire pour les projets
     *
     * @param aConfigDir
     *            répértoire de config
     * @param aFileName
     *            nom du fichier config
     * @param aConfigInPackage
     *            true si le fichier de config est dans le package
     * @return objet properties avec les propriété du config
     * @throws ServletException
     *             erreur de lecture des propriétés
     */
    private Properties getProperties(String aConfigDir, String aFileName, String aConfigInPackage) throws ServletException {
        Properties prop = new Properties();
        prop.put("configUrl", getUrlProp(aConfigDir, aFileName, aConfigInPackage).getPath());
        try {
            prop.putAll(readConfigFile(prop.getProperty("configUrl")));
        } catch (MalformedURLException e) {
            throw new ServletException(e);
        } catch (IOException e) {
            String message = "Fichier de config (" + aFileName + ") non trouvé!";
            message = message + " ConfigDir(" + aConfigDir + ") configDirPropName(" + getInitParameter("configDirPropName") + ")";
            throw new ServletException(message, e);
        }

        prop.put("configInPackage", aConfigInPackage);
        if ("true".equalsIgnoreCase(aConfigInPackage)) {
            prop.put("configDir", getServletContext().getRealPath("") + File.separator + aConfigDir);
            // Lien BIRT
            prop.put("BIRT_RESOURCEPATH", getServletContext().getRealPath("") + File.separator + prop.get("BIRT_RESOURCEPATH"));
            prop.put("report.dir", getServletContext().getRealPath("") + File.separator + prop.get("report.dir"));
        } else {
            prop.put("configDir", aConfigDir);
        }

        return prop;
    }

    /**
     *
     * @param aConfigDir
     *            répértoire config
     * @param aFileName
     *            nom du fichier config
     * @param aConfigInPackage
     *            true si le config est contenu dans le package
     * @return URL de fichier de configuration windows ou linux selon configdir et filename
     * @throws ServletException
     *             erreur de création de URI
     */
    private URL getUrlProp(String aConfigDir, String aFileName, String aConfigInPackage) throws ServletException {

        // va chercher toutes les proprietes du fichier .properties
        try {
            URL urlprop = null;
            // Charge le fichier de configuration statique
            if (aConfigDir.charAt(0) == '/' || aConfigDir.charAt(0) == '.') {
                // URL pour UNIX
                if ("true".equalsIgnoreCase(aConfigInPackage)) {
                    urlprop = new URL(
                            "file://" + getServletContext().getRealPath("") + File.separator + aConfigDir + File.separator + aFileName);
                } else {
                    urlprop = new URL("file://" + aConfigDir + File.separator + aFileName);
                }
            } else {

                if ("true".equalsIgnoreCase(aConfigInPackage)) {
                    // Si on désire mettre tous les fichiers de config dans le
                    // webapps
                    urlprop = new URL(
                            "file:///" + getServletContext().getRealPath("") + File.separator + aConfigDir + File.separator + aFileName);
                } else {
                    // Lien en absolu
                    urlprop = new URL("file:///" + aConfigDir + File.separator + aFileName);
                }
            }

            return urlprop;

        } catch (Exception e) {
            throw new ServletException("ConfigDir n'est pas un URI!!!", e);
        }
    }

    /**
     *
     * @param aConfigUrl
     *            Le nom du fichier de config, y compris le path
     * @return les propriétés du fichier de config
     * @throws MalformedURLException
     *             erreur dans le url du fichier du config
     * @throws IOException
     *             erreur de lecture du fichier de config
     */
    public static Properties readConfigFile(String aConfigUrl) throws IOException {
        Properties prop = new Properties();
        InputStream is = null;
        try {
            is = new URL("file:///" + aConfigUrl).openStream();
            prop.load(is);
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return prop;

    }

    /**
     *
     * @return properties stockés dans la table T_PROPERTIES
     * @throws SQLException
     *             erreur de lecture de la table T_PROPERTIES
     */
    public static Properties propertiesInDB() throws SQLException {
        Properties props = new Properties();
        try (ResultSet rs = ServiceLocator.getInstance().getContextManager().getDataSource().getConnection().createStatement()
                .executeQuery("select pro_key, pro_value from t_properties")) {
            while (rs.next()) {
                props.put(rs.getString("pro_key"), rs.getString("pro_value"));
            }
        }
        return props;
    }

    /**
     * Choix de la classe utilisée pour le management du context, par défaut GenericContextManager() est utilisée.
     *
     * Un système hiérarchique est en place
     * <li>Recherche d'un nom de classe dans le fichier de propriété sous la clé 'contextManagerClass'</li>
     * <li>Recherche d'un nom de classe comme paramètre d'initialisation de la servlet sous le paramètre 'contextManagerClass'</li>
     * <li>Utilisation de la classe 'ch.inser.dynajsf.util.GenericContextManager'</li>
     *
     * @see ch.inser.rest.util.GenericContextManager
     *
     * @param aProp
     *            contenu du fichier de propriété
     * @throws ServletException
     *             si la classe définie n'existe pas
     */
    protected void createContextManager(Properties aProp) throws ServletException {
        String fileName;
        // choix du context manager dans le fichier .properties
        if (aProp.get("contextManagerClass") != null) {
            fileName = (String) aProp.get("contextManagerClass");
        } else if (getInitParameter("contextManagerClass") != null) {
            fileName = getInitParameter("contextManagerClass");
            aProp.put("contextManagerClass", getInitParameter("contextManagerClass"));
        } else {
            fileName = null;
            aProp.put("contextManagerClass", "ch.inser.rest.util.GenericContextManager");
        }

        if (fileName != null) {
            // context management autre que le generic, choix configurable
            try {
                Class<?> cl = Class.forName(fileName);
                Constructor<?> constr = cl.getDeclaredConstructor();
                if (Modifier.isPrivate(constr.getModifiers())) {
                    // It's a singleton make a getInstance in place
                    Method method = cl.getMethod("getInstance");
                    ServiceLocator.getInstance().setContextManager((IContextManager) method.invoke(null));

                } else {
                    ServiceLocator.getInstance().setContextManager((IContextManager) constr.newInstance());
                }
                ServiceLocator.getInstance().getContextManager().addProperties(aProp);
            } catch (Exception e) {
                throw new ServletException("Nom de la class pour le context " + "management ( " + fileName + " ) incorrect!", e);
            }
        } else {
            // ContextManagement par défaut
            ServiceLocator.getInstance().setContextManager(new GenericContextManager());
            ServiceLocator.getInstance().getContextManager().addProperties(aProp);
        }
    }

    /**
     * Get values (secrets) from the environment variables and replace it in the properties. Maybe to improve using //
     * https://github.com/webcompere/lightweight-config or a similar solution
     *
     * @param aProperties
     *            the properties
     */
    protected void environment(Properties aProperties) {
        // The pattern to search for environment variables in the properties values
        Pattern pattern = Pattern.compile("^\\$\\{(.+)\\}$");

        // Search for properties values using environment variables
        aProperties.entrySet().stream().filter(entry -> {
            Matcher matcher = pattern.matcher((String) entry.getValue());

            if (matcher.find()) {
                // If found check if the environment variable is set
                String env = System.getenv(matcher.group(1));
                if (env != null && env.length() > 0) {
                    // If the environment variable is set replace the value in the property
                    entry.setValue(env);
                    return true;
                }
            }

            return false;
        }).forEach(entry -> System.out
                .println(String.format("Replaced the value of property '%s' by the environment varable value", entry.getKey())));
    }
}
