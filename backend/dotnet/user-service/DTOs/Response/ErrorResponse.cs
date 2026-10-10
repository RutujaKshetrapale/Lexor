using System.Text.Json.Serialization;

namespace UserService.DTOs.Response;

public class ErrorResponse
{
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;
    public int Status { get; set; }
    public string Error { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public string Path { get; set; } = string.Empty;

    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public IDictionary<string, string>? Details { get; set; }
}
