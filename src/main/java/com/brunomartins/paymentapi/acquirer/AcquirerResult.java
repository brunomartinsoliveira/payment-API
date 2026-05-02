package com.brunomartins.paymentapi.acquirer;

public record AcquirerResult(boolean approved, String errorMessage) {

    public static AcquirerResult ofApproved() {
        return new AcquirerResult(true, null);
    }

    public static AcquirerResult ofDeclined(String reason) {
        return new AcquirerResult(false, reason);
    }
}
