using Microsoft.AspNetCore.Mvc;

namespace NotificationService.Controllers;

[ApiController]
public class HealthController : ControllerBase
{
    [HttpGet("health")]
    public IActionResult Liveness() => Ok(new
    {
        status = "UP",
        service = "notification-service",
        timestamp = DateTime.UtcNow
    });

    [HttpGet("health/ready")]
    public IActionResult Readiness() => Ok(new
    {
        status = "READY",
        service = "notification-service",
        channels = new
        {
            in_app = "ACTIVE",
            fcm_push = "ACTIVE",
            sms = "ACTIVE",
            email = "ACTIVE"
        },
        timestamp = DateTime.UtcNow
    });
}
