package com.bankingplatform.events;

/**
 * Domain event type names published on {@link BankingTopics#DOMAIN_EVENTS}.
 */
public final class EventTypes {

    public static final String TRANSFER_COMPLETED = "transfer.completed";
    public static final String TRANSFER_FAILED = "transfer.failed";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String ACCOUNT_OPENED = "account.opened";
    public static final String CARD_ISSUED = "card.issued";
    public static final String CARD_FROZEN = "card.frozen";
    public static final String CARD_UNFROZEN = "card.unfrozen";
    public static final String LOAN_APPLIED = "loan.applied";
    public static final String LOAN_STATUS_CHANGED = "loan.status_changed";
    public static final String CUSTOMER_KYC_UPDATED = "customer.kyc_updated";

    private EventTypes() {
    }
}
