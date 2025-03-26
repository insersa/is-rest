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

package ch.inser.rest.provider;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.rest.util.ServiceLocator;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Accept or deny a request based on the IP address of the client who made the request.
 *
 * This filter is configured by setting the "allow" and/or "deny" properties to a comma-delimited list of regular expressions (in the syntax
 * supported by the java.util.regex package) to which the client IP address will be compared.
 *
 * <filter> <filter-name>RemoteHostFilter</filter-name> <filter-class>ch.inser.rest.provider.RemoteHostFilter</filter-class> <init-param>
 * <param-name>deny</param-name> <param-value>128.0.*,192.4.5.7</param-value> </init-param> <init-param> <param-name>allow</param-name>
 * <param-value>192.4.5.6,127.0.0.*</param-value> </init-param> </filter>
 *
 * or in the properties find under
 *
 * ch.inser.rest.provider.RemoteHostFilter.allow ch.inser.rest.provider.RemoteHostFilter.deny
 *
 *
 * Evaluation proceeds as follows:
 *
 * If there are any deny expressions configured, the IP will be compared to each expression. If a match is found, this request will be
 * rejected with a "Forbidden" HTTP response. If there are any allow expressions configured, the IP will be compared to each such
 * expression. If a match is NOT found, this request will be rejected with a "Forbidden" HTTP response. Otherwise, the request will continue
 * normally.
 *
 */
public class RemoteHostFilter implements Filter {
    /** Définition de la catégorie de logging */
    private static final Log logger = LogFactory.getLog(RemoteHostFilter.class);

    /** Information sur le context de l'aplication */
    private IContextManager iCtx;

    /** Liste des adresses ID avec accès */
    private String[] iAllow;
    /**
     * Liste des adresses ID sans accès
     */
    private String[] iDeny;

    /**
     * Init method for this filter
     *
     */
    @Override
    public void init(FilterConfig filterConfig) {
        // Prise des informations dans le web.xml
        iAllow = extractRegExps(filterConfig.getInitParameter("allow"));
        iDeny = extractRegExps(filterConfig.getInitParameter("deny"));
    }

    /**
     * Prise des paramètres dans le fichier de properties de l'application
     */
    public void initContextLoaded() {
        iCtx = ServiceLocator.getInstance().getContextManager();

        if (iCtx.getProperty("ch.inser.rest.provider.RemoteHostFilter.allow") != null) {
            iAllow = iCtx.getProperty("ch.inser.rest.provider.RemoteHostFilter.allow").split(",");
        }

        if (iCtx.getProperty("ch.inser.rest.provider.RemoteHostFilter.deny") != null) {
            iDeny = iCtx.getProperty("ch.inser.rest.provider.RemoteHostFilter.deny").split(",");
        }
    }

    /**
     *
     * @param request
     *            The servlet request we are processing
     * @param chain
     *            The filter chain we are processing
     *
     * @exception IOException
     *                if an input/output error occurs
     * @exception ServletException
     *                if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (iCtx == null) {
            initContextLoaded();
        }

        String clientAddr = request.getRemoteAddr();

        if (iDeny != null && hasMatch(clientAddr, iDeny)) {
            handleInvalidAccess(request, response, clientAddr);
            return;
        }

        if (iAllow != null && iAllow.length > 0 && !hasMatch(clientAddr, iAllow)) {
            handleInvalidAccess(request, response, clientAddr);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Traitement d'une requête non valide
     *
     * @param request
     *            la requête
     * @param response
     *            la réponse à modifier
     * @param clientAddr
     *            l'adresse IP de la source
     * @throws IOException
     *             erreur de modification de la réponse
     */
    private void handleInvalidAccess(ServletRequest request, ServletResponse response, String clientAddr) throws IOException {
        String url = ((HttpServletRequest) request).getRequestURL().toString();
        logger.info("Invalid access attempt to " + url + " from " + clientAddr);
        ((HttpServletResponse) response).sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    /**
     *
     * @param clientAddr
     *            adresse IP
     * @param regExps
     *            expression pour les adresses IP "allow" ou "deny"
     * @return true si une adresse IP est inclu dans la liste "allow" ou "deny"
     */
    private boolean hasMatch(String clientAddr, String[] regExps) {
        for (int i = 0; i < regExps.length; i++) {
            if (clientAddr.matches(regExps[i])) {
                return true;
            }
        }

        return false;
    }

    /**
     * Destroy method for this filter
     *
     */
    @Override
    public void destroy() {
        iAllow = null;
        iDeny = null;
    }

    /**
     *
     * @param initParam
     *            la liste d'adresses ID "allow" ou "deny" en string
     * @return la liste découpé en expressions
     */
    private String[] extractRegExps(String initParam) {
        if (initParam == null) {
            return new String[0];
        }
        return initParam.split(",");
    }

    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ClientAddrFilter(allow:'");
        sb.append(iAllow);
        sb.append("'/deny:'");
        sb.append(iDeny);
        sb.append("')");
        return sb.toString();
    }
}
