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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.spi.CachingProvider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.javaunderground.jdbc.DebugLevel;
import com.javaunderground.jdbc.StatementFactory;

import ch.inser.dynamic.common.AbstractDynamicDAO;
import ch.inser.dynamic.common.DAOParameter;
import ch.inser.dynamic.common.DAOParameter.Name;
import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynamic.common.IValueObject;
import ch.inser.dynamic.quality.IQualityTest;
import ch.inser.dynaplus.anonymous.BOAnonymous;
import ch.inser.dynaplus.anonymous.BPAnonymous;
import ch.inser.dynaplus.anonymous.DAOAnonymous;
import ch.inser.dynaplus.auth.SuperUser;
import ch.inser.dynaplus.bo.BOFactory;
import ch.inser.dynaplus.bo.BPFactory;
import ch.inser.dynaplus.bo.IBusinessObject;
import ch.inser.dynaplus.bo.IBusinessProcess;
import ch.inser.dynaplus.bo.IDAODelegate;
import ch.inser.dynaplus.dao.AbstractDataAcessObject;
import ch.inser.dynaplus.dao.DAOFactory;
import ch.inser.dynaplus.dao.GenericDataAccessObject;
import ch.inser.dynaplus.dao.IDataAccessObject;
import ch.inser.dynaplus.help.HelpBean;
import ch.inser.dynaplus.mail.AbstractMail;
import ch.inser.dynaplus.util.Constants.Entity;
import ch.inser.dynaplus.util.RSRUtil;
import ch.inser.dynaplus.vo.VOFactory;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISecurityImpl;
import ch.inser.rest.core.IBPDelegate;
import ch.inser.rest.util.JsonVoUtil;
import ch.inser.rest.util.RESTLocator;
import ch.inser.rest.util.ServiceLocator;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;

/**
 *
 * Le servlet <strong>AppInitServlet</strong> effectue une initialisation générale de l'application APE.
 * <p>
 * L'initialisation porte sur divers aspects concernant l'ensemble de l'application.
 *
 * @author INSER SA *
 */
public class AppInitServlet extends HttpServlet {

    /**
     * Serial Version UID
     */
    private static final long serialVersionUID = 1675675159375308738L;

    /**
     * Définition du Logger utilisé pour le logging
     */
    private static final Log logger = LogFactory.getLog(AppInitServlet.class);

    /** Taille du buffer pour copier un fichier par file input stream */
    private static final int BYTE_BUFFER_DEFAULT_SIZE = 1024;

    /**
     * Méthode d'initialisation appelée pour initialiser le servlet.
     * <p>
     * Les paramètres d'initialisation suivants sont traités:
     * <ul>
     * <li><strong>version</strong> - La version de l'application.
     * </ul>
     *
     * @exception ServletException
     *                si la configuration ne peut être réalisée.
     */
    @Override
    public void init() throws ServletException {
        IContextManager ctx = ServiceLocator.getInstance().getContextManager();

        logger.info("ConfigDir : '" + ctx.getProperty("configDir") + "'");
        logger.info("log.dir : '" + ctx.getProperty("log.dir") + "'");
        logger.info("contextManagerClass : " + ctx.getProperty("contextManagerClass"));
        logger.info("Débuté l'initialisation de l'application");

        ctx.setApplicationInitOK(false);
        setMaxRowSNumberForDBQueries(ctx);
        testDBConnection();
        optimizeJoins();
        initFactories();
        initAnonymous();
        initLocators();
        doInjections();

        // Définit le niveau DEBUG pour le StatementFactory fournissant les
        // DebuggableStatement
        StatementFactory.setDefaultDebug(DebugLevel.ON);
        initVersionCompatibility();
        initQualityRules();

        logger.info("Terminé l'initialisation de l'application");

        // initialisation avec succès
        ctx.getMessageStartApp().add("Application init : OK");
        ctx.setApplicationInitOK(true);
    }

    /**
     * Initialise les locators hors BP, DAO
     */
    private void initLocators() {
        try {
            RESTLocator locator = new RESTLocator();
            locator.setContextManager(ServiceLocator.getInstance().getContextManager());
            locator.init();
            ServiceLocator.getInstance().addLocator("rest", locator);
        } catch (ISException e) {
            logger.error("Erreur d'initialisation de REST locator", e);
        }
    }

    /**
     * Injections de dépendences sur des factories et contextmanager
     *
     * @throws ServletException
     *             error initializing cache manager
     */
    private void doInjections() throws ServletException {
        ServiceLocator serviceLocator = ServiceLocator.getInstance();
        IContextManager ctx = serviceLocator.getContextManager();

        // -- Inject the default super user
        ILoggedUser superUser = new SuperUser(false);
        serviceLocator.setSuperUser(superUser);

        // -- Dependency injections dans les DAO
        for (IDataAccessObject dao : DAOFactory.getInstance().getDAOList()) {
            dao.setVOFactory(VOFactory.getInstance());
        }

        // -- Dependency injections dans les BO
        for (String boName : BOFactory.getInstance().getBOList()) {
            IBusinessObject bo = BOFactory.getInstance().getBO(boName);
            bo.setDao((IDAODelegate) serviceLocator.getLocator("dao").getService(boName));
            bo.setVOFactory(VOFactory.getInstance());
            bo.setBOFactory(BOFactory.getInstance());
        }

        // -- Dependency injection des BP
        for (String bpName : BPFactory.getInstance().getBPList()) {
            IBusinessProcess bp = BPFactory.getInstance().getBP(bpName);
            bp.setBOFactory(BOFactory.getInstance());
            bp.setVOFactory(VOFactory.getInstance());
        }

        // -- Injecter Help dans ContextManager
        if (BOFactory.getInstance().getBO("Help_key") != null) {
            HelpBean help = new HelpBean();
            help.setBPFactory(BPFactory.getInstance());
            help.setUser(superUser);
            help.init();
            ctx.setHelpBean(help);
        }

        // -- Injecter ContextManager dans les classes de la couche BO
        IBusinessObject boMail = BOFactory.getInstance().getBO("Mail");
        if (boMail != null) {
            AbstractMail.setContextManager(ctx);
        }

        // -- dans RSRUtil
        RSRUtil.setContextManager(ctx);

        initCache(ctx);

        JsonVoUtil.setVOFactory(VOFactory.getInstance());
        JsonVoUtil.setBPFactory(BPFactory.getInstance());
        JsonVoUtil.setContextManager(ctx);
    }

    /**
     * Initialize the cache.
     *
     * @param aContextManager
     *            the context manager
     * @throws ServletException
     *             error reading cache config
     */
    protected void initCache(IContextManager aContextManager) throws ServletException {
        // -- Activation du système de cache
        if ("true".equals(aContextManager.getProperty(ISecurityImpl.SECURITY_USER_CACHE))) {

            // Injection du CacheManager
            final URL myUrl = getClass().getResource("/is-rest-cache.xml");

            CachingProvider provider = Caching.getCachingProvider();
            CacheManager cacheManager;
            try {
                cacheManager = provider.getCacheManager(myUrl.toURI(), null);

                aContextManager.setCacheManager(cacheManager);
            } catch (URISyntaxException e) {
                logger.fatal("Echec d'initialisation du cache", e);
                throw new ServletException(e);
            }
        }
    }

    /**
     * Optimisation des joins dans la recherche
     */
    private void optimizeJoins() {
        String listOpt = getServletConfig().getInitParameter("ListOptimized");
        if ("true".equals(listOpt)) {
            AbstractDataAcessObject.setListOptimized(true);
        }

    }

    /**
     * Initialisation la classe de tests de qualités si la propriété ch.inser.dynamic.quality existe
     */
    private void initQualityRules() {
        IContextManager ctx = ServiceLocator.getInstance().getContextManager();
        if (ctx.getProperty("ch.inser.dynamic.quality") != null) {
            Class<?> cl;
            try {
                cl = Class.forName(ctx.getProperty("ch.inser.dynamic.quality"));

                Constructor<?> constr = cl.getConstructor();
                IQualityTest qual = (IQualityTest) constr.newInstance((Object[]) null);
                qual.setContextManager(ServiceLocator.getInstance().getContextManager());
                ctx.setQualityTest(qual);
                ctx.getMessageStartApp().add("Quality init : OK");
            } catch (Exception e) {
                logger.error("Erreur initialisation de la classe qualitlé", e);
                ctx.getMessageStartApp().add("Quality init : FAILED");
            }
        }
    }

    /**
     * Init la compatibilité de la version de l'application et la version de base de données
     *
     * @throws ServletException
     *             erreur de compatibilité
     */
    private void initVersionCompatibility() throws ServletException {
        ServiceLocator serviceLocator = ServiceLocator.getInstance();
        IContextManager ctx = serviceLocator.getContextManager();

        // Lit version de l'application
        ctx.setApplicationVersion(getServletConfig().getInitParameter("version"));

        String build = getServletConfig().getInitParameter("build");
        ctx.setApplicationBuild(build);

        String name = getServletConfig().getInitParameter("name");
        ctx.setApplicationName(name);

        IBPDelegate bp = (IBPDelegate) serviceLocator.getLocator("bp").getService("VersionDB");
        if (bp == null) {
            return;
        }
        // Crée une connexion
        IValueObject vo = VOFactory.getInstance().getVO("VersionDB");
        String versionLabel = getServletConfig().getInitParameter("databaseDependenceLabel");
        if (versionLabel != null) {
            vo.setProperty("ver_label", versionLabel);
        }
        List<IValueObject> list = null;
        try {
            list = bp.getList(vo, serviceLocator.getSuperUser(), new DAOParameter(Name.SORT_INDEX, 1)).getListObject();
        } catch (ISException e) {
            logger.warn("Error getting DB Version ", e);
            ctx.getMessageStartApp().add("BD connection : FAILED");
        }
        if (list != null && !list.isEmpty()) {
            vo = list.get(0);
            ctx.setDatabaseVersion(vo.getProperty("ver_version").toString());
            ctx.setDatabaseDependenceVersion(vo.getProperty("ver_compatibilite").toString());
            logger.info("*** Check compatibility APPL-DB ***");
            logger.info("*** Script version : " + vo.getProperty("ver_version"));
            logger.info("*** Compatibility DB : " + vo.getProperty("ver_compatibilite"));
            logger.info("*** Compatibility Appli : " + getServletConfig().getInitParameter("databaseDependenceVersion"));
            if (!((Long) vo.getProperty("ver_compatibilite")).toString()
                    .equals(getServletConfig().getInitParameter("databaseDependenceVersion"))) {
                logger.error("Compatibilty version between DB and Appli have to be the same");
                ctx.getMessageStartApp().add("Compatibility Appli-DB : FAILED");
                throw new ServletException("Incompatibility version between DB and Appli");

            }
            ctx.getMessageStartApp().add("Compatibility Appli-DB : OK");
            logger.info("Compatibility Appli-DB : OK");
        }
    }

    /**
     * Init les factories dao, vo, bo, bp selon configDir
     *
     */
    private void initFactories() {
        IContextManager ctx = ServiceLocator.getInstance().getContextManager();
        String configDir = ServiceLocator.getInstance().getContextManager().getProperty("configDir");

        // Copie des fichiers de ressources
        // ---------------------------------
        copyResourcesFiles(configDir);

        boolean configValid = true;

        // Initialisation des DAO
        DAOFactory.getInstance().setContextManager(ServiceLocator.getInstance().getContextManager());
        boolean daoValid = DAOFactory.getInstance().init();
        applyAttributeDBNameMapping();
        VOFactory.getInstance().setContextManager(ServiceLocator.getInstance().getContextManager());
        boolean voValid = VOFactory.getInstance().init();
        BOFactory.getInstance().setContextManager(ServiceLocator.getInstance().getContextManager());
        boolean boValid = BOFactory.getInstance().init();
        BPFactory.getInstance().setContextManager(ServiceLocator.getInstance().getContextManager());
        boolean bpValid = BPFactory.getInstance().init();

        configValid = daoValid && voValid && boValid && bpValid;

        if (configValid) {
            ctx.getMessageStartApp().add("Initialisation configuration files (dao,vo,bo,bp) : OK");
        }
    }

    /**
     * Make some verification to be sure the connection is on
     *
     * @throws ServletException
     *             erreur de test de connexion
     */
    private void testDBConnection() throws ServletException {
        IContextManager ctx = ServiceLocator.getInstance().getContextManager();
        if (ctx.getDataSource() == null) {
            logger.warn("*** BD : no datasource defined!");
            return;
        }
        try (Connection con = ctx.getDataSource().getConnection()) {
            if (con == null) {
                logger.error("*** BD : connection null!");
                ctx.getMessageStartApp().add("BD : connection null!");
            } else {
                logger.info("*** BD : " + con.getMetaData().getURL());
                ctx.getMessageStartApp().add("BD connection '" + con.getMetaData().getURL() + "' : OK");
            }
        } catch (Exception e) {
            logger.error("Problem getting the connection", e);
            ctx.getMessageStartApp().add("BD connection : Failed");
            throw new ServletException("init failure", e);
        }
    }

    /**
     * Initialise le DAO, BO et BP de l'objet anonyme qui permet de consulter des tables sans fichier de config.
     */
    private void initAnonymous() {
        IContextManager ctx = ServiceLocator.getInstance().getContextManager();
        // Initialisation de DAO
        int i = 0;
        if (ctx.getProperty("datasourceMaxRowsPerQuery") != null) {
            i = Integer.parseInt(ctx.getProperty("datasourceMaxRowsPerQuery"));
        }
        DAOAnonymous.setResultSetMaxRows(i);
        DAOAnonymous dao = new DAOAnonymous();
        DAOFactory.getInstance().add(Entity.anonymous.toString(), dao);

        // Initialisation de BO
        BOAnonymous bo = new BOAnonymous();
        bo.setDao((IDAODelegate) ServiceLocator.getInstance().getLocator("dao").getService(Entity.anonymous.toString()));
        bo.setVOFactory(VOFactory.getInstance());
        BOFactory.getInstance().add(Entity.anonymous.toString(), bo);

        // Initialisation de BP
        BPAnonymous bp = new BPAnonymous();
        bp.setBOFactory(BOFactory.getInstance());
        bp.setContextManager(ctx);
        bp.setVOFactory(VOFactory.getInstance());
        BPFactory.getInstance().add(Entity.anonymous.toString(), bp);
    }

    /**
     * Copie des fichiers de ressources du directory de configuration dans le paquet déployé. Cette technique n'est pas utilisable dans tous
     * les serveurs d'application.
     *
     * @param configDir
     *            dierctory des fichier de configuration
     */
    private void copyResourcesFiles(String configDir) {
        File file;

        if (configDir != null) {
            file = new File(configDir + File.separator + "jsf");
            String realPath = getServletContext().getRealPath(File.separator);
            if (realPath == null) {
                realPath = getServletContext().getRealPath("");
            }
            if (file.list() != null) {
                for (String str : file.list()) {
                    copyResoucesFile(configDir, realPath, str);
                }
            }
        } else {
            logger.info("No JSF directory");
        }
    }

    /**
     * Copy a resources file from the configuration folder to the deployment path. Not exploitable on all the application servers.
     *
     * @param aConfigDir
     *            the configuration folder
     * @param aRealPath
     *            the deployment path
     * @param aFileName
     *            the resource file
     */
    private void copyResoucesFile(String aConfigDir, String aRealPath, String aFileName) {
        if (aFileName.startsWith("ApplicationResources") && aFileName.endsWith(".properties")) {
            try {
                File f1 = new File(aConfigDir + File.separator + "jsf" + File.separator + aFileName);
                String realPath = aRealPath;
                if (!aRealPath.endsWith(File.separator)) {
                    realPath = aRealPath + File.separator;
                }
                File f2 = new File(realPath + "WEB-INF" + File.separator + "classes" + File.separator + aFileName);
                if (!f2.createNewFile()) {
                    logger.info("Replace the file: " + aFileName);
                }
                copyFile(f1, f2);
            } catch (IOException e1) {
                logger.error("Error rewriting Application " + "Resources", e1);
            }
        }
    }

    /**
     * Copy the file content.
     *
     * @param sourceFile
     *            the source file
     * @param destinationFile
     *            the desination file
     * @throws IOException
     *             an exception reading or writing the files
     */
    private void copyFile(File sourceFile, File destinationFile) throws IOException {
        try (InputStream in = new FileInputStream(sourceFile); OutputStream out = new FileOutputStream(destinationFile);) {
            byte[] buf = new byte[BYTE_BUFFER_DEFAULT_SIZE];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    /**
     * Set maximal rows number for DB queries
     *
     * @param ctx
     *            IContextManager
     */
    private void setMaxRowSNumberForDBQueries(IContextManager ctx) {
        try {
            int maxRowsNumberForDBQuery = Integer.parseInt(ctx.getProperty("datasourceMaxRowsPerQuery"));
            AbstractDynamicDAO.setResultSetMaxRows(maxRowsNumberForDBQuery);
            DAOAnonymous.setResultSetMaxRows(maxRowsNumberForDBQuery);
            logger.info("datasourceMaxRowsPerQuery : '" + ctx.getProperty("datasourceMaxRowsPerQuery") + "'");
        } catch (NumberFormatException numberFormatException) {
            logger.debug(numberFormatException.toString());

            AbstractDynamicDAO.setResultSetMaxRows(0);
            logger.info("datasourceMaxRowsPerQuery : '" + ctx.getProperty("datasourceMaxRowsPerQuery") + "'");
        }
    }

    /**
     * Apply attribute and db name mapping to all DAOs Le mapping des attributs est fait de manière globale pour éviter de mettre à jour
     * tous les fichiers. Dans le système de joins on peut laisser le soin à la table principale de gérer l'alias!
     */
    private void applyAttributeDBNameMapping() {
        Map<String, String> attributeDBNameMappingOfAllTablesMap = new HashMap<>();

        // Determine mapping for all different attribute and DB name values for
        // all DAOs
        for (String daoName : DAOFactory.getInstance().getAllDAONames()) {
            IDataAccessObject dao = DAOFactory.getInstance().getDAO(daoName);

            if (dao instanceof GenericDataAccessObject) {
                GenericDataAccessObject genericDataAccessObject = (GenericDataAccessObject) dao;
                Map<String, String> attributeNameDbNameMap = genericDataAccessObject.getAttributeNameDBNameMap();
                if (!attributeNameDbNameMap.isEmpty()) {
                    attributeDBNameMappingOfAllTablesMap.putAll(attributeNameDbNameMap);
                }
            }
        }

        // Apply mapping to all DAOs
        for (String daoName : DAOFactory.getInstance().getAllDAONames()) {
            IDataAccessObject dao = DAOFactory.getInstance().getDAO(daoName);

            if (dao instanceof GenericDataAccessObject) {
                GenericDataAccessObject genericDataAccessObject = (GenericDataAccessObject) dao;
                genericDataAccessObject.applyMapping(attributeDBNameMappingOfAllTablesMap);
            }
        }
    }
}
