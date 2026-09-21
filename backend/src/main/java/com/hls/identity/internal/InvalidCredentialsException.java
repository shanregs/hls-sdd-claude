package com.hls.identity.internal;

/**
 * FR-015: the single exception thrown for every "can't log in" case — wrong
 * password, wrong OTP, locked account, unregistered identifier. The controller
 * maps this to one generic response, deliberately not distinguishing why.
 */
public class InvalidCredentialsException extends RuntimeException {
}
