using Microsoft.AspNetCore.Http;

namespace VehicleService.Storage;

public interface IDocumentStorageService
{
    Task<StoredDocument> StoreAsync(IFormFile file);
    Stream LoadAsStream(string filePath);
    void Delete(string filePath);
}
