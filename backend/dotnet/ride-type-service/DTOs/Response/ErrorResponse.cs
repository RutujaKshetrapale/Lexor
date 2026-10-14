namespace RideTypeService.DTOs.Response;

public class ErrorResponse
{
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;
    public int Status { get; set; }
    public string Error { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public string Path { get; set; } = string.Empty;
    public Dictionary<string, string>? Details { get; set; }
}
