namespace PaymentService.DTOs.Responses;

public class ErrorResponse
{
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;
    public int Status { get; set; }
    public string Error { get; set; } = string.Empty;
    public string Message { get; set; } = string.Empty;
    public string Path { get; set; } = string.Empty;
    public IDictionary<string, string>? Details { get; set; }
}

public class PageResponse<T>
{
    public List<T> Content { get; set; } = new();
    public int PageNumber { get; set; }
    public int PageSize { get; set; }
    public long TotalElements { get; set; }
    public int TotalPages { get; set; }
    public bool Last { get; set; }
}
