using DriverService.Configuration;
using DriverService.Exceptions;
using DriverService.Storage;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging.Abstractions;
using Microsoft.Extensions.Options;
using Xunit;

namespace DriverService.Tests;

public class FileSystemDocumentStorageServiceTests : IDisposable
{
    private readonly string _testStorageDir;
    private readonly FileSystemDocumentStorageService _storageService;

    public FileSystemDocumentStorageServiceTests()
    {
        _testStorageDir = Path.Combine(Path.GetTempPath(), "lexor_test_storage_" + Guid.NewGuid());
        var options = Options.Create(new StorageSettings
        {
            Location = _testStorageDir,
            AllowedExtensions = "pdf,jpg,jpeg,png,webp",
            MaxFileSizeBytes = 10 * 1024 * 1024
        });

        _storageService = new FileSystemDocumentStorageService(options, NullLogger<FileSystemDocumentStorageService>.Instance);
    }

    public void Dispose()
    {
        try
        {
            if (Directory.Exists(_testStorageDir))
            {
                Directory.Delete(_testStorageDir, true);
            }
        }
        catch
        {
            // Ignore test cleanup errors
        }
    }

    [Fact]
    public async Task StoreAsync_ValidPdfWithMagicBytes_Succeeds()
    {
        var content = "%PDF-1.4 sample pdf content for unit test"u8.ToArray();
        var file = CreateMockFormFile("document.pdf", "application/pdf", content);

        var result = await _storageService.StoreAsync(file);

        Assert.NotNull(result);
        Assert.Equal("document.pdf", result.OriginalFileName);
        Assert.Equal("application/pdf", result.ContentType);
        Assert.True(File.Exists(result.StoragePath));
    }

    [Fact]
    public async Task StoreAsync_ValidJpegWithMagicBytes_Succeeds()
    {
        var content = new byte[] { 0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01 };
        var file = CreateMockFormFile("photo.jpg", "image/jpeg", content);

        var result = await _storageService.StoreAsync(file);

        Assert.NotNull(result);
        Assert.Equal("image/jpeg", result.ContentType);
        Assert.True(File.Exists(result.StoragePath));
    }

    [Fact]
    public async Task StoreAsync_ValidPngWithMagicBytes_Succeeds()
    {
        var content = new byte[] { 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D };
        var file = CreateMockFormFile("image.png", "image/png", content);

        var result = await _storageService.StoreAsync(file);

        Assert.NotNull(result);
        Assert.Equal("image/png", result.ContentType);
    }

    [Fact]
    public async Task StoreAsync_EmptyFile_ThrowsBadRequest()
    {
        var file = CreateMockFormFile("empty.pdf", "application/pdf", Array.Empty<byte>());

        await Assert.ThrowsAsync<BadRequestException>(() => _storageService.StoreAsync(file));
    }

    [Fact]
    public async Task StoreAsync_DisallowedExtension_ThrowsBadRequest()
    {
        var content = "malicious script content"u8.ToArray();
        var file = CreateMockFormFile("virus.exe", "application/octet-stream", content);

        await Assert.ThrowsAsync<BadRequestException>(() => _storageService.StoreAsync(file));
    }

    [Fact]
    public async Task StoreAsync_MismatchedMagicBytes_ThrowsBadRequest()
    {
        var textContent = "This is plain text pretending to be pdf"u8.ToArray();
        var file = CreateMockFormFile("fake.pdf", "application/pdf", textContent);

        await Assert.ThrowsAsync<BadRequestException>(() => _storageService.StoreAsync(file));
    }

    [Fact]
    public async Task StoreAsync_PathTraversalInFilename_ThrowsBadRequest()
    {
        var content = "%PDF-1.4 content"u8.ToArray();
        var file = CreateMockFormFile("../../evil.pdf", "application/pdf", content);

        await Assert.ThrowsAsync<BadRequestException>(() => _storageService.StoreAsync(file));
    }

    [Fact]
    public async Task LoadAsStream_ExistingFile_ReturnsReadableStream()
    {
        var content = "%PDF-1.4 sample"u8.ToArray();
        var file = CreateMockFormFile("test.pdf", "application/pdf", content);
        var stored = await _storageService.StoreAsync(file);

        using var stream = _storageService.LoadAsStream(stored.StoragePath);

        Assert.NotNull(stream);
        Assert.True(stream.CanRead);
    }

    [Fact]
    public void LoadAsStream_NonExistentFile_ThrowsResourceNotFound()
    {
        Assert.Throws<ResourceNotFoundException>(() => _storageService.LoadAsStream(Path.Combine(_testStorageDir, "nonexistent.pdf")));
    }

    [Fact]
    public async Task Delete_ExistingFile_DeletesSuccessfully()
    {
        var content = "%PDF-1.4 sample"u8.ToArray();
        var file = CreateMockFormFile("delete_me.pdf", "application/pdf", content);
        var stored = await _storageService.StoreAsync(file);

        Assert.True(File.Exists(stored.StoragePath));

        _storageService.Delete(stored.StoragePath);

        Assert.False(File.Exists(stored.StoragePath));
    }

    private static IFormFile CreateMockFormFile(string fileName, string contentType, byte[] content)
    {
        var stream = new MemoryStream(content);
        return new FormFile(stream, 0, content.Length, "file", fileName)
        {
            Headers = new HeaderDictionary(),
            ContentType = contentType
        };
    }
}
