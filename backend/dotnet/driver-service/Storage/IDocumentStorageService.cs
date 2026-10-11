namespace DriverService.Storage;

public class StoredDocument
{
    public string OriginalFileName { get; set; } = string.Empty;
    public string StoredFileName { get; set; } = string.Empty;
    public string StoragePath { get; set; } = string.Empty;
    public string ContentType { get; set; } = string.Empty;
    public long FileSize { get; set; }
}

public interface IDocumentStorageService
{
    Task<StoredDocument> StoreAsync(IFormFile file);
    Stream LoadAsStream(string filePath);
    void Delete(string filePath);
}
