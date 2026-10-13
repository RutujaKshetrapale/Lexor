namespace VehicleService.Configuration;

public class StorageSettings
{
    public const string SectionName = "Storage";

    public string Location { get; set; } = "uploads/vehicle-documents";
    public string AllowedExtensions { get; set; } = "pdf,jpg,jpeg,png,webp";
    public long MaxFileSizeBytes { get; set; } = 10 * 1024 * 1024; // 10MB
}
