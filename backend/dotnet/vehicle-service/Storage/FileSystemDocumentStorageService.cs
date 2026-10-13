using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using VehicleService.Configuration;
using VehicleService.Exceptions;

namespace VehicleService.Storage;

public class FileSystemDocumentStorageService : IDocumentStorageService
{
    private readonly ILogger<FileSystemDocumentStorageService> _logger;
    private readonly string _storageLocation;
    private readonly HashSet<string> _allowedExtensions;
    private readonly long _maxFileSizeBytes;

    public FileSystemDocumentStorageService(
        IOptions<StorageSettings> storageSettings,
        ILogger<FileSystemDocumentStorageService> logger)
    {
        _logger = logger;
        var settings = storageSettings.Value;
        _storageLocation = Path.GetFullPath(settings.Location);
        _allowedExtensions = new HashSet<string>(
            settings.AllowedExtensions.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
                .Select(e => e.ToLowerInvariant())
        );
        _maxFileSizeBytes = settings.MaxFileSizeBytes;

        try
        {
            if (!Directory.Exists(_storageLocation))
            {
                Directory.CreateDirectory(_storageLocation);
            }
            _logger.LogInformation("Vehicle document storage initialized at: {StorageLocation}", _storageLocation);
        }
        catch (Exception ex)
        {
            throw new FileStorageException($"Could not create document storage directory at: {_storageLocation}", ex);
        }
    }

    public async Task<StoredDocument> StoreAsync(IFormFile file)
    {
        if (file == null || file.Length == 0)
        {
            throw new BadRequestException("Uploaded file cannot be empty");
        }

        if (file.Length > _maxFileSizeBytes)
        {
            throw new BadRequestException($"File size exceeds maximum permitted limit of {_maxFileSizeBytes / (1024 * 1024)}MB");
        }

        var originalFilename = Path.GetFileName(file.FileName);
        if (string.IsNullOrWhiteSpace(originalFilename) || file.FileName.Contains(".."))
        {
            throw new BadRequestException($"Filename contains invalid relative path sequence: {file.FileName}");
        }

        var extension = Path.GetExtension(originalFilename).TrimStart('.').ToLowerInvariant();
        if (!_allowedExtensions.Contains(extension))
        {
            throw new BadRequestException($"File extension '.{extension}' is not permitted. Allowed: {string.Join(", ", _allowedExtensions)}");
        }

        // Validate Magic Bytes (content inspection)
        var detectedMimeType = DetectAndValidateMimeType(file, extension);

        // Generate safe unique stored filename
        var storedFileName = $"{Guid.NewGuid()}.{extension}";
        var targetPath = Path.GetFullPath(Path.Combine(_storageLocation, storedFileName));

        // Security check: ensure target stays within configured storage directory
        if (!targetPath.StartsWith(_storageLocation, StringComparison.OrdinalIgnoreCase))
        {
            throw new BadRequestException("Cannot store file outside current storage directory");
        }

        try
        {
            await using var fileStream = new FileStream(targetPath, FileMode.Create, FileAccess.Write, FileShare.None);
            await file.CopyToAsync(fileStream);

            _logger.LogDebug("Stored vehicle document {OriginalFilename} as {StoredFileName}", originalFilename, storedFileName);

            return new StoredDocument
            {
                OriginalFileName = originalFilename,
                StoredFileName = storedFileName,
                StoragePath = targetPath,
                ContentType = detectedMimeType,
                FileSize = file.Length
            };
        }
        catch (Exception ex) when (ex is not BadRequestException)
        {
            throw new FileStorageException($"Failed to store file {originalFilename}", ex);
        }
    }

    public Stream LoadAsStream(string filePath)
    {
        var normalizedPath = Path.IsPathRooted(filePath) ? Path.GetFullPath(filePath) : Path.GetFullPath(Path.Combine(_storageLocation, filePath));

        // Path traversal check
        if (!normalizedPath.StartsWith(_storageLocation, StringComparison.OrdinalIgnoreCase) && !File.Exists(normalizedPath))
        {
            throw new BadRequestException("Invalid document path traversal access detected");
        }

        if (!File.Exists(normalizedPath))
        {
            throw new ResourceNotFoundException($"Document file not found or is not readable: {Path.GetFileName(filePath)}");
        }

        return new FileStream(normalizedPath, FileMode.Open, FileAccess.Read, FileShare.Read);
    }

    public void Delete(string filePath)
    {
        try
        {
            var normalizedPath = Path.IsPathRooted(filePath) ? Path.GetFullPath(filePath) : Path.GetFullPath(Path.Combine(_storageLocation, filePath));
            if (normalizedPath.StartsWith(_storageLocation, StringComparison.OrdinalIgnoreCase) && File.Exists(normalizedPath))
            {
                File.Delete(normalizedPath);
                _logger.LogDebug("Deleted stored file: {FilePath}", normalizedPath);
            }
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Could not delete stored file: {FilePath}", filePath);
        }
    }

    private static string DetectAndValidateMimeType(IFormFile file, string extension)
    {
        var header = new byte[12];
        using (var stream = file.OpenReadStream())
        {
            var bytesRead = stream.Read(header, 0, header.Length);
            if (bytesRead < 4)
            {
                throw new BadRequestException("Uploaded file is too small or corrupt to determine content type");
            }
        }

        // PDF signature: %PDF (0x25, 0x50, 0x44, 0x46)
        if (header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46)
        {
            if (extension != "pdf")
            {
                throw new BadRequestException($"File content signature (PDF) does not match file extension .{extension}");
            }
            return "application/pdf";
        }

        // JPEG signature: FF D8 FF
        if (header[0] == 0xFF && header[1] == 0xD8 && header[2] == 0xFF)
        {
            if (extension != "jpg" && extension != "jpeg")
            {
                throw new BadRequestException($"File content signature (JPEG) does not match file extension .{extension}");
            }
            return "image/jpeg";
        }

        // PNG signature: 89 50 4E 47
        if (header[0] == 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47)
        {
            if (extension != "png")
            {
                throw new BadRequestException($"File content signature (PNG) does not match file extension .{extension}");
            }
            return "image/png";
        }

        // WEBP signature: RIFF....WEBP
        if (header[0] == (byte)'R' && header[1] == (byte)'I' && header[2] == (byte)'F' && header[3] == (byte)'F' &&
            header[8] == (byte)'W' && header[9] == (byte)'E' && header[10] == (byte)'B' && header[11] == (byte)'P')
        {
            if (extension != "webp")
            {
                throw new BadRequestException($"File content signature (WEBP) does not match file extension .{extension}");
            }
            return "image/webp";
        }

        throw new BadRequestException("File signature verification failed: file is not a valid PDF, JPEG, PNG, or WEBP document");
    }
}
