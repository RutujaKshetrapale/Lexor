package com.lexor.notification.service;

import com.lexor.notification.dto.request.OtpDeliveryRequest;
import com.lexor.notification.dto.response.ApiResponse;

public interface IOtpDeliveryService {

    ApiResponse<String> deliverOtp(OtpDeliveryRequest request);
}
