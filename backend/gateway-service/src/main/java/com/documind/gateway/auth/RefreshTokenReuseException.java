package com.documind.gateway.auth;

/**
 * Thrown when a refresh token that was already rotated (or explicitly revoked) is presented again.
 * A legitimate client never does this, since it always uses the newest token in the chain, so this
 * is treated as evidence the token was stolen: the whole rotation family is revoked in response,
 * signing every session descended from it out at once.
 */
public class RefreshTokenReuseException extends RuntimeException {

    public RefreshTokenReuseException() {
        super(
                "This refresh token was already used; every session from that login has been signed out");
    }
}
