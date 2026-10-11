namespace DriverService.Configuration;

public class StorageSettings
{
    public const string SectionName = "Storage";

    public string Location { get; set; } = "uploads/documents";
    public string AllowedExtensions { get; set; } = "pdf,jpg,jpeg,png,webp";
    public long MaxFileSizeBytes { get; set; } = 10485760; // 10 MB
}
