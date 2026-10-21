package com.lexor.payment.client;

import com.lexor.payment.client.dto.BookingDetailDto;

import java.util.Optional;

public interface IBookingClient {

    Optional<BookingDetailDto> getBookingDetails(String bookingUuid, String authorizationHeader);
}
