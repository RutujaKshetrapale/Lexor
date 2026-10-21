package com.lexor.payment.service;

import com.lexor.payment.entity.PaymentEntity;

import java.math.BigDecimal;

public interface ILedgerService {

    void recordDigitalPaymentSuccess(PaymentEntity payment, BigDecimal platformCommissionPercentage);

    void recordCashCollectionSuccess(PaymentEntity payment, BigDecimal platformCommissionPercentage);

    void recordRefund(PaymentEntity payment, BigDecimal refundAmount, String refundUuid);
}
