namespace FeedbackService.DTOs;

public class PageResponse<T>
{
    public List<T> Content { get; set; } = new();
    public int Page { get; set; }
    public int Size { get; set; }
    public long TotalElements { get; set; }
    public int TotalPages { get; set; }
    public bool First { get; set; }
    public bool Last { get; set; }

    public static PageResponse<T> Create(List<T> items, int page, int size, long totalElements)
    {
        int totalPages = size > 0 ? (int)Math.Ceiling((double)totalElements / size) : 0;
        return new PageResponse<T>
        {
            Content = items,
            Page = page,
            Size = size,
            TotalElements = totalElements,
            TotalPages = totalPages,
            First = page <= 0,
            Last = page >= totalPages - 1
        };
    }
}
