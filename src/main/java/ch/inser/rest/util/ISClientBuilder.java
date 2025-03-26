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

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

import ch.inser.dynamic.common.IContextManager;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

/**
 * HTTP client builder that gives a client with or without proxy configuration and SSL verifier disabled.
 *
 * The proxy is configured in project.properties with the three properties client.proxy.active, client.proxy.host, client.proxy.port. THE
 * SSL verifier is configured in project.properties with the the property client.ssl.validation.disable.
 *
 * This was originally in is-map library as ch.inser.isejawa.map.util.ISClientBuilder, moved to use it also in projects without is-map.
 *
 * @author INSER SA */
public class ISClientBuilder {
    /**
     * Logger
     */
    private static final Log logger = LogFactory.getLog(ISClientBuilder.class);

    /** Context manager */
    private static IContextManager iCtx;

    /** Nom de la propriété pour activer proxy */
    private static final String PROXY_ACTIVE_PROP = "client.proxy.active";

    /** Nom de la propriété pour proxy host */
    private static final String PROXY_HOST_PROP = "client.proxy.host";

    /** Nom de la propriété pour proxy port */
    private static final String PROXY_PORT_PROP = "client.proxy.port";

    /**
     * The context property to disable the SSL validation.
     *
     * Code inspired from https://nakov.com/blog/2009/07/16/disable-certificate-validation-in-java-ssl-connections/
     */
    private static final String SSL_VALIDATION = "client.ssl.validation.disable";

    /**
     * The SSL context to disable the SSL validation.
     */
    private static SSLContext SSL_CONTEXT;

    /**
     * The host name verifier to disable the SSL verifier.
     */
    private static HostnameVerifier ALL_HOST_VALID;

    /** Private constructor to hide the public one. Only static methods */
    private ISClientBuilder() {
    }

    /**
     * Build an HTTP client.
     *
     * @return the HTTP client
     */
    public static Client build() {
        ResteasyClientBuilder builder = (ResteasyClientBuilder) ClientBuilder.newBuilder();

        // Configure the proxy
        if (iCtx != null && Boolean.TRUE.toString().equals(iCtx.getProperty(PROXY_ACTIVE_PROP))) {
            builder = builder.defaultProxy(iCtx.getProperty(PROXY_HOST_PROP), Integer.parseInt(iCtx.getProperty(PROXY_PORT_PROP)), "http");
        }

        // Disable the SSL validation.
        if (iCtx != null && Boolean.TRUE.toString().equals(iCtx.getProperty(SSL_VALIDATION))) {
            if (SSL_CONTEXT == null) {
                try {
                    initSSLContext();
                } catch (KeyManagementException | NoSuchAlgorithmException e) {
                    logger.error("Error initializing the SSL context to disable the SSL verifier", e);
                }
            }
            if (ALL_HOST_VALID == null) {
                initAllHostValid();
            }
            builder = builder.sslContext(SSL_CONTEXT).hostnameVerifier(ALL_HOST_VALID);
        }

        return builder.build();
    }

    /**
     *
     * @param aCtx
     *            context manager
     */
    public static void setContextManager(IContextManager aCtx) {
        iCtx = aCtx;
    }

    /**
     * Initialize an SSL context to disable the SSL verifier.
     *
     * @throws NoSuchAlgorithmException
     *             if SSL is not supported
     * @throws KeyManagementException
     *             if the context can't be initialized
     */
    private static void initSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                // Nothing to do
            }

            @Override
            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                // Nothing to do
            }
        } };

        SSL_CONTEXT = SSLContext.getInstance("SSL");
        SSL_CONTEXT.init(null, trustAllCerts, new java.security.SecureRandom());
    }

    /**
     * Initialize the name verifier to disable the SSL verifier.
     */
    private static void initAllHostValid() {
        ALL_HOST_VALID = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }

        };
    }
}
