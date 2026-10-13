using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Options;
using Moq;
using VehicleService.Configuration;
using VehicleService.Exceptions;
using VehicleService.Storage;
using Xunit;

namespace VehicleService.Tests;

public class FileSystemDocumentStorageServiceTests : IDisposable
{
    private readonly string _tempDir;
    private readonly FileSystemDocumentStorageService _storageService;

    public FileSystemDocumentStorageServiceTests()
    {
        _tempDir = Path.Combine(Path.GetTempPath(), "lexor_vehicle_tests_" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(_tempDir);

        var options = Options.Create(new StorageSettings
        {
            Location = _tempDir,
            AllowedExtensions = "pdf,jpg,jpeg,png,webp",
            MaxFileSizeBytes = 1024 * 1024 // 1MB for test
        });

        var logger = new Mock<ILogger<FileSystemDocumentStorageService>>();
        _storageService = new FileSystemDocumentStorageService(options, logger.Object);
    }

    public void Dispose()
    {
        try
        {
            if (Directory.Exists(_tempDir))
            {
                Directory.Delete(_tempDir, true);
            }
        }
        catch
        {
            // Ignore cleanup errors
        }
    }

    [Fact]
    public async Task StoreAsync_ValidPdf_StoresSuccessfully()
    {
        var content = new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A }; // %PDF-1.4
        using var stream = new MemoryStream(content);
        var file = new FormFile(stream, 0, content.Length, "file", "rc_document.pdf");

        var result = await _storageService.StoreAsync(file);

        Assert.NotNull(result);
        Assert.Equal("rc_document.pdf", result.OriginalFileName);
        Assert.Equal("application/pdf", result.ContentType);
        Assert.True(File.Exists(result.StoragePath));
    }

    [Fact]
    public async Task StoreAsync_ValidJpeg_StoresSuccessfully()
    {
        var content = new byte[] { 0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46 };
        using var stream = new MemoryStream(content);
        var file = new FormFile(stream, 0, content.Length, "file", "car_photo.jpg");

        var result = await _storageService.StoreAsync(file);

        Assert.NotNull(result);
        Assert.Equal("image/jpeg", result.ContentType);
        Assert.True(File.Exists(result.StoragePath));
    }

    [Fact]
    public async Task StoreAsync_ValidPng_StoresSuccessfully()
    {
        var content = new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
        using var stream = new MemoryStream(content);
        var file = new FormFile(stream, 0, content.Length, "file", "puc_scan.png");

        var result = await _storageService.StoreAsync(file);

        Assert.NotNull(result);
        Assert.Equal("image/png", result.ContentType);
        Assert.True(File.Exists(result.StoragePath));
    }

    [Fact]
    public async Task StoreAsync_EmptyFile_ThrowsBadRequestException()
    {
        using var stream = new MemoryStream(Array.Empty<byte>());
        var file = new FormFile(stream, 0, 0, "file", "empty.pdf");

        await Assert.ThrowsAsync<BadRequestException>(() => _storageService.StoreAsync(file));
    }

    [Fact]
    public async Task StoreAsync_OversizedFile_ThrowsBadRequestException()
    {
        var content = new byte[2 * 1024 * 1024]; // 2MB exceeds 1MB limit
        using var stream = new MemoryStream(content);
        var file = new FormFile(stream, 0, content.Length, "file", "huge.pdf");

        await Assert.ThrowsAsync<BadRequestException>(() => _storageService.StoreAsync(file));
    }

    [Fact]
    public async Task StoreAsync_DisallowedExtension_ThrowsBadRequestException()
    {
        var content = new byte[] { 0x4D, 0x5A, 0x90, 0x00 }; // DOS executable
        using var stream = new MemoryStream(content);
        var file = new FormFile(stream, 0, content.Length, "file", "malware.exe");

        await Assert.ThrowsAsync<BadRequestException>(() => _storageService.StoreAsync(file));
    }

    [Fact]
    public async Task StoreAsync_PathTraversalInFilename_ThrowsBadRequestException()
    {
        var content = new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34 };
        using var stream = new MemoryStream(content);
        var file = new FormFile(stream, 0, content.Length, "file", "../../../etc/passwd.pdf");

        await Assert.ThrowsAsync<BadRequestException>(() => _storageService.StoreAsync(file));
    }

    [Fact]
    public async Task StoreAsync_MimeTypeMismatch_ThrowsBadRequestException()
    {
        // Extension is pdf but content is plain text
        var content = System.Text.Encoding.UTF8.GetBytes("This is plain text pretending to be PDF");
        using var stream = new MemoryStream(content);
        var file = new FormFile(stream, 0, content.Length, "file", "fake.pdf");

        await Assert.ThrowsAsync<BadRequestException>(() => _storageService.StoreAsync(file));
    }

    [Fact]
    public async Task LoadAsStream_ValidFile_ReturnsStream()
    {
        var content = new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34 };
        using var stream = new MemoryStream(content);
        var file = new FormFile(stream, 0, content.Length, "file", "test.pdf");
        var stored = await _storageService.StoreAsync(file);

        using var readStream = _storageService.LoadAsStream(stored.StoragePath);
        Assert.NotNull(readStream);
        Assert.True(readStream.CanRead);
    }

    [Fact]
    public void LoadAsStream_NonExistentFile_ThrowsResourceNotFoundException()
    {
        var fakePath = Path.Combine(_tempDir, "non_existent.pdf");
        Assert.Throws<ResourceNotFoundException>(() => _storageService.LoadAsStream(fakePath));
    }

    [Fact]
    public async Task Delete_ExistingFile_DeletesFile()
    {
        var content = new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34 };
        using var stream = new MemoryStream(content);
        var file = new FormFile(stream, 0, content.Length, "file", "delete_me.pdf");
        var stored = await _storageService.StoreAsync(file);

        Assert.True(File.Exists(stored.StoragePath));
        _storageService.Delete(stored.StoragePath);
        Assert.False(File.Exists(stored.StoragePath));
    }
}
