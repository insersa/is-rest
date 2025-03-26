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

import java.util.Arrays;

import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.JwtContext;
import org.jose4j.jwt.consumer.Validator;

/**
 * Validation of the claim azp (Authorized Party) instead of aud (Audience)
 *
 * Used when an application is accessible by all users and no role is required, for example in a public mobile application
 */
public class AzpValidator implements Validator {

    /**
     * The expected Authorized Party: for example the name of the mobile application
     */
    private final String iExpectedAzpClientId;

    /**
     *
     * @param aExpectedAzpClientId
     *            The expected Authorized Party: for example the name of the mobile application
     */
    public AzpValidator(String aExpectedAzpClientId) {
        iExpectedAzpClientId = aExpectedAzpClientId;
    }

    @Override
    public String validate(JwtContext jwtContext) throws MalformedClaimException {
        // Extract the azp claim from the JWT
        String azpClaimValue = (String) jwtContext.getJwtClaims().getClaimValue("azp");
        String[] acceptedAzpValues = iExpectedAzpClientId.split(",");

        // Validate the azp claim against the expected client ID
        boolean isAzpAccepted = Arrays.asList(acceptedAzpValues).contains(azpClaimValue);
        if (azpClaimValue != null && isAzpAccepted) {
            // The azp claim matches the expected client ID, indicating a valid
            // token
            return null; // Indicate successful validation
        }
        // The azp claim does not match the expected client ID, indicating an invalid token
        throw new MalformedClaimException("Invalid azp claim");
    }
}
