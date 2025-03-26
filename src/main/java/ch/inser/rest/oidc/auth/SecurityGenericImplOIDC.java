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

import javax.cache.Cache;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jose4j.jwa.AlgorithmConstraints.ConstraintType;
import org.jose4j.jwk.HttpsJwks;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.keys.resolvers.HttpsJwksVerificationKeyResolver;

import ch.inser.dynamic.common.IContextManager;
import ch.inser.dynamic.common.IDAOResult;
import ch.inser.dynamic.common.ILoggedUser;
import ch.inser.dynaplus.auth.SuperUser;
import ch.inser.jsl.exceptions.ISException;
import ch.inser.rest.auth.ISSecurityException;
import ch.inser.rest.core.IBPDelegate;
import ch.inser.rest.util.JsonUtil;
import ch.inser.rest.util.ServiceLocator;

import jakarta.json.JsonObject;

/**
 * Implémentation par défaut de la validation d'un token OIDC
 */
public class SecurityGenericImplOIDC implements ISecurityImplOIDC {

    /** Définition de catégorie de logging */
    private static final Log logger = LogFactory.getLog(SecurityGenericImplOIDC.class);

    /**
     * The property key name for the url that validates the certificate of the token
     */
    private static final String SECURITY_TOKEN_CERT_URL = "security.token.cert.url";

    /**
     * The property key name for the audience that has access, for example an application name
     */
    private static final String SECURITY_AUDIENCE = "security.audience";

    /**
     * The property key name for the autorized party that has access, for example an application name. Used when an application does not
     * have roles and therefore no audience
     */
    private static final String SECURITY_AUTHORIZED_PARTY = "security.authorized.party";

    @Override
    public JwtClaims validateOIDCToken(String token, IContextManager aContextManager) throws ISSecurityException {
        return validateOIDCToken(token, aContextManager.getProperty(SECURITY_TOKEN_CERT_URL),
                aContextManager.getProperty(SECURITY_AUDIENCE), aContextManager.getProperty(SECURITY_AUTHORIZED_PARTY));
    }

    @Override
    public ILoggedUser getUser(JwtClaims aClaims, ServiceLocator aServiceLocator) throws ISException {
        if (aClaims == null) {
            return null;
        }
        // -- Get the user name
        JsonObject claimsJSON = JsonUtil.stringToJsonObject(aClaims.getRawJson());
        String username = !claimsJSON.isEmpty() ? claimsJSON.getString("preferred_username") : null;
        if (username != null && "true".equals(aServiceLocator.getContextManager().getProperty(SECURITY_SESSION_CONTROL))) {
            validateUserSession(username, aServiceLocator);
        }
        return getLoggedUser(username, claimsJSON, aServiceLocator);
    }

    /**
     *
     * @param aUsername
     *            nom d'utilisateur
     * @param aClaims
     *            les attributs de l'utilisateur reçu dans le token
     * @param aServiceLocator
     *            service locator
     * @return objet LoggedUser de la caché ou initialisé la première fois
     * @throws ISException
     *             erreur d'initialisation de l'utilisateur
     */
    private ILoggedUser getLoggedUser(String aUsername, JsonObject aClaims, ServiceLocator aServiceLocator) throws ISException {
        // -- Gestion du retour du ILoggedUser par le cache
        IContextManager contextManager = aServiceLocator.getContextManager();
        Object initData = aUsername;
        if (aClaims != null && Boolean.TRUE.toString().equals(contextManager.getProperty("security.inituser.claims"))) {
            // Initialise le logged user à la base des claims
            initData = aClaims;
        }
        if ("true".equals(contextManager.getProperty(SECURITY_USER_CACHE))) {
            Cache<String, ILoggedUser> cache = contextManager.getCacheManager().getCache("userCache", String.class, ILoggedUser.class);

            if (!cache.containsKey(aUsername)) {

                ILoggedUser user = (ILoggedUser) ((IBPDelegate) aServiceLocator.getLocator("bp").getService("LoggedUser"))
                        .executeMethode("initLoggedUser", initData, new SuperUser());
                cache.put(aUsername, user);
            }
            return cache.get(aUsername);
        }

        // -- Retour de l'utilisateur si configuration sans cache
        return (ILoggedUser) ((IBPDelegate) aServiceLocator.getLocator("bp").getService("LoggedUser")).executeMethode("initLoggedUser",
                initData, new SuperUser());
    }

    /**
     * Vérifie que l'utilisateur a une session en cours dans la base de données: le timestamp logout est null
     *
     * @param aUsername
     *            nom d'utilisateur
     * @param aServiceLocator
     *            servicelocator avec accès à BP
     * @throws ISException
     *             erreur de consultation de table user
     */
    private void validateUserSession(String aUsername, ServiceLocator aServiceLocator) throws ISException {
        IDAOResult result = (IDAOResult) ((IBPDelegate) aServiceLocator.getLocator("bp").getService("LoggedUser"))
                .executeMethode("validateSession", aUsername, new SuperUser());
        if (!result.isStatusOK()) {
            throw new ISSecurityException("User is not logged in: " + aUsername, null);
        }
    }

    @Override
    public JwtClaims validateOIDCToken(String aToken, String aCertUrl, String aAudience, String aAzp) throws ISSecurityException {

        HttpsJwks httpsJkws = new HttpsJwks(aCertUrl);
        HttpsJwksVerificationKeyResolver httpsJwksKeyResolver = new HttpsJwksVerificationKeyResolver(httpsJkws);
        // Create the consumer that will process the JWT
        JwtConsumerBuilder jwtConsumer = new JwtConsumerBuilder().setRequireExpirationTime()
                .setJwsAlgorithmConstraints(ConstraintType.PERMIT, AlgorithmIdentifiers.RSA_USING_SHA256)
                .setVerificationKeyResolver(httpsJwksKeyResolver);
        if (aAudience != null && !aAudience.isBlank()) {
            jwtConsumer.setExpectedAudience(aAudience);
        } else if (aAzp != null && !aAzp.isBlank()) {
            jwtConsumer.setSkipDefaultAudienceValidation();
            jwtConsumer.registerValidator(new AzpValidator(aAzp));
        }

        // JWT token parsing
        JwtClaims jwtClaims;
        try {
            jwtClaims = jwtConsumer.build().processToClaims(aToken);
            logger.debug("JWT validation succeeded! " + jwtClaims);
            return jwtClaims;

        } catch (InvalidJwtException e) {
            throw new ISSecurityException("The token is invalid", new RuntimeException(e));
        }
    }

}
