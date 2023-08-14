/*
 * (c) 2018 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 *
 */
package org.geoserver.security.oauth2;

import org.geoserver.security.config.RoleSource;

/**
 * Filter configuration for OpenId Connect. This is completely freeform, so adding only the basic
 * bits in here.
 */
public class OpenIdConnectFilterConfig extends GeoServerOAuth2FilterConfig {

    String principalKey = "email";
    String jwkURI;
    String tokenRolesClaim;
    String responseMode;
    boolean sendClientSecret = false;
    boolean allowBearerTokens = true;

    /** Supports extraction of roles among the token claims */
    public static enum OpenIdRoleSource implements RoleSource {
        IdToken,
        AccessToken,
        MSGraphAPI,
        UserInfo;

        @Override
        public boolean equals(RoleSource other) {
            return other != null && other.toString().equals(toString());
        }
    };

    public OpenIdConnectFilterConfig() {
        this.redirectUri = "http://localhost:8080/geoserver";
        this.scopes = "user";
        this.enableRedirectAuthenticationEntryPoint = false;
        this.forceAccessTokenUriHttps = true;
        this.forceUserAuthorizationUriHttps = true;
        this.loginEndpoint = "/j_spring_oauth2_openid_connect_login";
        this.logoutEndpoint = "/j_spring_oauth2_openid_connect_logout";
    };

    public String getPrincipalKey() {
        return principalKey == null ? "email" : principalKey;
    }

    public void setPrincipalKey(String principalKey) {
        this.principalKey = principalKey;
    }

    public String getJwkURI() {
        return jwkURI;
    }

    public void setJwkURI(String jwkURI) {
        this.jwkURI = jwkURI;
    }

    public String getTokenRolesClaim() {
        return tokenRolesClaim;
    }

    public void setTokenRolesClaim(String tokenRolesClaim) {
        this.tokenRolesClaim = tokenRolesClaim;
    }

    public String getResponseMode() {
        return responseMode;
    }

    public void setResponseMode(String responseMode) {
        this.responseMode = responseMode;
    }

    public boolean isSendClientSecret() {
        return sendClientSecret;
    }

    public void setSendClientSecret(boolean sendClientSecret) {
        this.sendClientSecret = sendClientSecret;
    }

    public boolean isAllowBearerTokens() {
        return allowBearerTokens;
    }

    public void setAllowBearerTokens(boolean allowBearerTokens) {
        this.allowBearerTokens = allowBearerTokens;
    }

    @Override
    protected StringBuilder buildAuthorizationUrl() {
        StringBuilder sb = super.buildAuthorizationUrl();
        String responseMode = getResponseMode();
        if (responseMode != null && !"".equals(responseMode.trim()))
            sb.append("&response_mode=").append(responseMode);
        return sb;
    }

    protected StringBuilder buildEndSessionUrl(final String idToken) {
        final StringBuilder logoutUri = new StringBuilder(getLogoutUri());
        if (idToken != null) {
            logoutUri.append("?").append("id_token_hint=").append(idToken);
        }
        return logoutUri;
    }
}
