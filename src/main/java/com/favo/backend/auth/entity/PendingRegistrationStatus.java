package com.favo.backend.auth.entity;

/**
 * Lifecycle of a pre-account row before {@code system_user} insert.
 */
public enum PendingRegistrationStatus {
    PENDING,
    COMPLETED,
    EXPIRED
}
