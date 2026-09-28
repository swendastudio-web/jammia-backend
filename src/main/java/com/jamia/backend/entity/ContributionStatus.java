package com.jamia.backend.entity;

/**
 * Payment status of one contribution. JAMIA never moves money; members report it.
 */
public enum ContributionStatus {
    // Not paid yet.
    PENDING,
    // The payer says "I paid".
    PAID,
    // The recipient says "I received it".
    CONFIRMED
}
