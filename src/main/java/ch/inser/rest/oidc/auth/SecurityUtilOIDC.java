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

package ch.inser.rest.oidc.auth;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jose4j.jwt.JwtClaims;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.util.ServiceLocator;

/**
 * Function to validate OIDC JSON Web Token. The tokens are created externally by an auth server
 *
 * @author INSER SA */
public class SecurityUtilOIDC {

    /** Définition de catégorie de logging */
    @SuppressWarnings("unused")
    private static final Log logger = LogFactory.getLog(SecurityUtilOIDC.class);

    /**
     * The implementation to use.
     */
    private static ISecurityImplOIDC cSecurityImpl = new SecurityGenericImplOIDC();

    /**
     * Constructeur
     */
    private SecurityUtilOIDC() {
    }

    /**
     * Validate and get the token attributes.
     *
     * @param token
     *            the token value
     * @param aContextManager
     *            the context manager
     * @return the token attributes or <code>null</code> if the security token is disabled
     * @throws ISSecurityException
     *             an exception trying to validate the token: the token is invalid
     */
    public static JwtClaims validateOIDCToken(String token, IContextManager aContextManager) throws ISSecurityException {
        return cSecurityImpl.validateOIDCToken(token, aContextManager);
    }

    /**
     * Get the user.
     *
     * @param aClaims
     *            the claims
     * @param aServiceLocator
     *            the service locator
     * @return the user
     * @throws ISException
     *             erreur d'initialisation de l'utilisateur
     */
    public static ILoggedUser getUser(JwtClaims aClaims, ServiceLocator aServiceLocator) throws ISException {
        return cSecurityImpl.getUser(aClaims, aServiceLocator);
    }

    /**
     * Set the security implementation to use.
     *
     * @param aSecurityImpl
     *            the security implementation
     */
    public static void setSecurityImpl(ISecurityImplOIDC aSecurityImpl) {
        cSecurityImpl = aSecurityImpl;
    }
}
