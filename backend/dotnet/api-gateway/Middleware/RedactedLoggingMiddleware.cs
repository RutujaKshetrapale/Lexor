using System.Diagnostics;

namespace ApiGateway.Middleware;

public class RedactedLoggingMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ILogger<RedactedLoggingMiddleware> _logger;

    public RedactedLoggingMiddleware(RequestDelegate next, ILogger<RedactedLoggingMiddleware> logger)
    {
        _next = next;
        _logger = logger;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var correlationId = context.Request.Headers[CorrelationIdMiddleware.CorrelationIdHeader].FirstOrDefault();
        var method = context.Request.Method;
        var path = context.Request.Path.Value;
        var sw = Stopwatch.StartNew();

        _logger.LogDebug("[GATEWAY INCOMING] Method: {Method}, Path: {Path}, CorrelationId: {CorrelationId}",
            method, path, correlationId);

        try
        {
            await _next(context);
        }
        finally
        {
            sw.Stop();
            var statusCode = context.Response.StatusCode;
            _logger.LogInformation("[GATEWAY OUTGOING] Method: {Method}, Path: {Path}, Status: {Status}, Duration: {Duration}ms, CorrelationId: {CorrelationId}",
                method, path, statusCode, sw.ElapsedMilliseconds, correlationId);
        }
    }
}
