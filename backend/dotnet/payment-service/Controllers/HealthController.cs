using Microsoft.AspNetCore.Mvc;

namespace PaymentService.Controllers;

[ApiController]
[Route("health")]
public class HealthController : ControllerBase
{
    [HttpGet]
    public IActionResult Health()
    {
        return Ok(new
        {
            status = "UP",
            service = "payment-service",
            timestamp = DateTime.UtcNow
        });
    }

    [HttpGet("ready")]
    public IActionResult Readiness()
    {
        return Ok(new
        {
            status = "READY",
            service = "payment-service",
            timestamp = DateTime.UtcNow
        });
    }
}
