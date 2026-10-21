using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using PaymentService.DTOs.Requests;
using PaymentService.DTOs.Responses;
using PaymentService.Services;

namespace PaymentService.Controllers;

[ApiController]
[Route("api/v1/driver/payments")]
[Authorize(Roles = "DRIVER")]
public class DriverPaymentController : ControllerBase
{
    private readonly IPaymentService _paymentService;

    public DriverPaymentController(IPaymentService paymentService)
    {
        _paymentService = paymentService;
    }

    [HttpPost("{paymentUuid}/cash-collection")]
    public async Task<ActionResult<PaymentResponse>> ConfirmCashCollection(
        [FromRoute] string paymentUuid,
        [FromBody] CashCollectionRequest? request)
    {
        var response = await _paymentService.ConfirmCashCollectionAsync(User, paymentUuid, request);
        return Ok(response);
    }
}
