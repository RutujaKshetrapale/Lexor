using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using PaymentService.Data;
using PaymentService.DTOs.Requests;
using PaymentService.DTOs.Responses;
using PaymentService.Entities;
using PaymentService.Enums;
using PaymentService.Services;

namespace PaymentService.Controllers;

[ApiController]
[Route("api/v1/admin/payments")]
[Authorize(Roles = "ADMIN")]
public class AdminPaymentController : ControllerBase
{
    private readonly IPaymentService _paymentService;
    private readonly PaymentDbContext _dbContext;

    public AdminPaymentController(IPaymentService paymentService, PaymentDbContext dbContext)
    {
        _paymentService = paymentService;
        _dbContext = dbContext;
    }

    [HttpGet]
    public async Task<ActionResult<PageResponse<PaymentResponse>>> GetAllPayments(
        [FromQuery] PaymentStatus? status,
        [FromQuery] PaymentMethod? method,
        [FromQuery] string? riderUuid,
        [FromQuery] string? driverUuid,
        [FromQuery] int pageNumber = 0,
        [FromQuery] int pageSize = 10)
    {
        var response = await _paymentService.GetAdminPaymentsAsync(status, method, riderUuid, driverUuid, pageNumber, pageSize);
        return Ok(response);
    }

    [HttpGet("{paymentUuid}")]
    public async Task<ActionResult<PaymentResponse>> GetPaymentByUuid([FromRoute] string paymentUuid)
    {
        var response = await _paymentService.GetAdminPaymentByUuidAsync(paymentUuid);
        return Ok(response);
    }

    [HttpPost("{paymentUuid}/refund")]
    public async Task<ActionResult<RefundResponse>> ProcessRefund(
        [FromRoute] string paymentUuid,
        [FromBody] AdminRefundRequest request)
    {
        var response = await _paymentService.ProcessAdminRefundAsync(User, paymentUuid, request);
        return Ok(response);
    }

    [HttpGet("reconciliation")]
    public async Task<ActionResult<ReconciliationResponse>> GetReconciliation()
    {
        var response = await _paymentService.GetReconciliationReportAsync();
        return Ok(response);
    }

    [HttpGet("{paymentUuid}/audit")]
    public async Task<ActionResult<List<PaymentTransaction>>> GetPaymentAudit([FromRoute] string paymentUuid)
    {
        var transactions = await _dbContext.PaymentTransactions
            .AsNoTracking()
            .Where(t => t.PaymentUuid == paymentUuid)
            .OrderByDescending(t => t.CreatedAt)
            .ToListAsync();
        return Ok(transactions);
    }
}
