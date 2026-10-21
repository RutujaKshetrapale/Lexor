using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using PaymentService.Services;

namespace PaymentService.Controllers;

[ApiController]
[Route("api/v1/payments/webhooks")]
[AllowAnonymous]
public class PaymentWebhookController : ControllerBase
{
    private readonly IPaymentService _paymentService;

    public PaymentWebhookController(IPaymentService paymentService)
    {
        _paymentService = paymentService;
    }

    [HttpPost("{provider}")]
    public async Task<IActionResult> HandleWebhook(
        [FromRoute] string provider,
        [FromHeader(Name = "X-Razorpay-Signature")] string? razorpaySignature,
        [FromHeader(Name = "X-Webhook-Signature")] string? genericSignature)
    {
        using var reader = new StreamReader(Request.Body);
        var rawBody = await reader.ReadToEndAsync();

        var signature = razorpaySignature ?? genericSignature;
        await _paymentService.ProcessWebhookAsync(provider, rawBody, signature);

        return Ok(new { status = "ok", message = "Webhook processed successfully" });
    }
}
