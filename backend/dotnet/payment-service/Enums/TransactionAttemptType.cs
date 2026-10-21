namespace PaymentService.Enums;

public enum TransactionAttemptType
{
    AUTHORIZATION,
    CAPTURE,
    SALE,
    CASH_COLLECTION,
    REFUND,
    VERIFICATION
}
