namespace RideRequestService.DTOs.Response;

public class RideTypeDto
{
    public string Uuid { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Code { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public decimal BaseFare { get; set; }
    public decimal PerKmRate { get; set; }
    public decimal PerMinuteRate { get; set; }
    public decimal MinimumFare { get; set; }
    public decimal CancellationFee { get; set; }
    public int Capacity { get; set; }
    public string? IconUrl { get; set; }
    public bool IsActive { get; set; }
}
