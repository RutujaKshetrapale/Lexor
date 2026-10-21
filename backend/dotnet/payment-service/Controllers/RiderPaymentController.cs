using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using PaymentService.DTOs.Requests;
using PaymentService.DTOs.Responses;
using PaymentService.Services;

namespace PaymentService.Controllers;

[ApiController]
[Route("api/v1/payments")]
public class RiderPaymentController : ControllerBase
{
    private readonly IPaymentService _paymentService;

    public RiderPaymentController(IPaymentService paymentService)
    {
        _paymentService = paymentService;
    }

    [HttpGet("methods")]
    [AllowAnonymous]
    public ActionResult<List<PaymentMethodDto>> GetPaymentMethods()
    {
        return Ok(_paymentService.GetEnabledPaymentMethods());
    }

    [HttpPost]
    [Authorize(Roles = "RIDER")]
    public async Task<ActionResult<PaymentResponse>> InitiatePayment(
        [FromBody] InitiatePaymentRequest request,
        [FromHeader(Name = "Idempotency-Key")] string? idempotencyKey)
    {
        var authHeader = Request.Headers.Authorization.ToString();
        var response = await _paymentService.InitiatePaymentAsync(User, request, idempotencyKey, authHeader);
        return StatusCode(StatusCodes.Status201Created, response);
    }

    [HttpGet("me")]
    [Authorize(Roles = "RIDER")]
    public async Task<ActionResult<PageResponse<PaymentResponse>>> GetMyPayments(
        [FromQuery] int pageNumber = 0,
        [FromQuery] int pageSize = 10)
    {
        var response = await _paymentService.GetRiderPaymentsAsync(User, pageNumber, pageSize);
        return Ok(response);
    }

    [HttpGet("me/{paymentUuid}")]
    [Authorize(Roles = "RIDER")]
    public async Task<ActionResult<PaymentResponse>> GetMyPaymentByUuid([FromRoute] string paymentUuid)
    {
        var response = await _paymentService.GetRiderPaymentByUuidAsync(User, paymentUuid);
        return Ok(response);
    }

    [HttpGet("me/{paymentUuid}/receipt")]
    [Authorize(Roles = "RIDER")]
    public async Task<ActionResult<PaymentReceiptResponse>> GetPaymentReceipt([FromRoute] string paymentUuid)
    {
        var authHeader = Request.Headers.Authorization.ToString();
        var response = await _paymentService.GetPaymentReceiptAsync(User, paymentUuid, authHeader);
        return Ok(response);
    }

    [HttpPost("{paymentUuid}/retry")]
    [Authorize(Roles = "RIDER")]
    public async Task<ActionResult<PaymentResponse>> RetryPayment(
        [FromRoute] string paymentUuid,
        [FromBody] RetryPaymentRequest? request)
    {
        var response = await _paymentService.RetryPaymentAsync(User, paymentUuid, request);
        return Ok(response);
    }

    [HttpPost("{paymentUuid}/refund")]
    [Authorize(Roles = "RIDER")]
    public async Task<ActionResult<RefundResponse>> RequestRefund(
        [FromRoute] string paymentUuid,
        [FromBody] RefundPaymentRequest request)
    {
        var response = await _paymentService.RequestRefundAsync(User, paymentUuid, request);
        return Ok(response);
    }

    [HttpGet("{paymentUuid}/status")]
    [Authorize(Roles = "RIDER")]
    public async Task<ActionResult<PaymentResponse>> GetPaymentStatus(
        [FromRoute] string paymentUuid,
        [FromQuery] bool refresh = false)
    {
        var response = await _paymentService.GetPaymentStatusAsync(User, paymentUuid, refresh);
        return Ok(response);
    }
}
