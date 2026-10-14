using Microsoft.Extensions.Logging.Abstractions;
using RideTypeService.DTOs.Request;
using RideTypeService.Entities;
using RideTypeService.Exceptions;
using Xunit;

namespace RideTypeService.Tests;

public class RideTypeServiceTests
{
    private static (Services.RideTypeService service, Data.LexorDbContext context) CreateService(string dbName)
    {
        var context = TestDbContextFactory.Create(dbName);
        var logger = NullLogger<Services.RideTypeService>.Instance;
        var service = new Services.RideTypeService(context, logger);
        return (service, context);
    }

    [Fact]
    public async Task GetEnabledRideTypesAsync_ReturnsOnlyActiveRideTypes()
    {
        var (service, context) = CreateService(nameof(GetEnabledRideTypesAsync_ReturnsOnlyActiveRideTypes));

        context.RideTypes.AddRange(
            new RideType { Id = 1, Uuid = "uuid-1", Name = "LEXOR Go", Code = "GO", BaseFare = 50, PerKmRate = 15, PerMinuteRate = 2.5m, MinimumFare = 80, Capacity = 4, IsActive = true },
            new RideType { Id = 2, Uuid = "uuid-2", Name = "LEXOR XL", Code = "XL", BaseFare = 90, PerKmRate = 22, PerMinuteRate = 3.5m, MinimumFare = 140, Capacity = 6, IsActive = true },
            new RideType { Id = 3, Uuid = "uuid-3", Name = "LEXOR Vintage", Code = "VINTAGE", BaseFare = 200, PerKmRate = 50, PerMinuteRate = 10, MinimumFare = 300, Capacity = 3, IsActive = false }
        );
        await context.SaveChangesAsync();

        var result = await service.GetEnabledRideTypesAsync();

        Assert.Equal(2, result.Count);
        Assert.Contains(result, r => r.Code == "GO");
        Assert.Contains(result, r => r.Code == "XL");
        Assert.DoesNotContain(result, r => r.Code == "VINTAGE");
    }

    [Fact]
    public async Task GetEnabledRideTypeByUuidAsync_WhenActive_ReturnsRideType()
    {
        var (service, context) = CreateService(nameof(GetEnabledRideTypeByUuidAsync_WhenActive_ReturnsRideType));

        context.RideTypes.Add(new RideType
        {
            Id = 1, Uuid = "uuid-go", Name = "LEXOR Go", Code = "GO", BaseFare = 50, PerKmRate = 15, PerMinuteRate = 2.5m, MinimumFare = 80, Capacity = 4, IsActive = true
        });
        await context.SaveChangesAsync();

        var result = await service.GetEnabledRideTypeByUuidAsync("uuid-go");

        Assert.NotNull(result);
        Assert.Equal("GO", result.Code);
        Assert.Equal("LEXOR Go", result.Name);
    }

    [Fact]
    public async Task GetEnabledRideTypeByUuidAsync_WhenInactive_ThrowsResourceNotFoundException()
    {
        var (service, context) = CreateService(nameof(GetEnabledRideTypeByUuidAsync_WhenInactive_ThrowsResourceNotFoundException));

        context.RideTypes.Add(new RideType
        {
            Id = 1, Uuid = "uuid-inactive", Name = "LEXOR Vintage", Code = "VINTAGE", BaseFare = 200, PerKmRate = 50, PerMinuteRate = 10, MinimumFare = 300, Capacity = 3, IsActive = false
        });
        await context.SaveChangesAsync();

        await Assert.ThrowsAsync<ResourceNotFoundException>(() => service.GetEnabledRideTypeByUuidAsync("uuid-inactive"));
    }

    [Fact]
    public async Task GetEnabledRideTypeByUuidAsync_WhenNotFound_ThrowsResourceNotFoundException()
    {
        var (service, _) = CreateService(nameof(GetEnabledRideTypeByUuidAsync_WhenNotFound_ThrowsResourceNotFoundException));

        await Assert.ThrowsAsync<ResourceNotFoundException>(() => service.GetEnabledRideTypeByUuidAsync("non-existent-uuid"));
    }

    [Fact]
    public async Task GetAllRideTypesAdminAsync_ReturnsActiveAndInactive()
    {
        var (service, context) = CreateService(nameof(GetAllRideTypesAdminAsync_ReturnsActiveAndInactive));

        context.RideTypes.AddRange(
            new RideType { Id = 1, Uuid = "uuid-1", Name = "LEXOR Go", Code = "GO", BaseFare = 50, PerKmRate = 15, PerMinuteRate = 2.5m, MinimumFare = 80, Capacity = 4, IsActive = true },
            new RideType { Id = 2, Uuid = "uuid-2", Name = "LEXOR Vintage", Code = "VINTAGE", BaseFare = 200, PerKmRate = 50, PerMinuteRate = 10, MinimumFare = 300, Capacity = 3, IsActive = false }
        );
        await context.SaveChangesAsync();

        var result = await service.GetAllRideTypesAdminAsync();

        Assert.Equal(2, result.Count);
        Assert.Contains(result, r => r.Code == "VINTAGE" && !r.IsActive);
    }

    [Fact]
    public async Task GetRideTypeByUuidAdminAsync_RetrievesInactive()
    {
        var (service, context) = CreateService(nameof(GetRideTypeByUuidAdminAsync_RetrievesInactive));

        context.RideTypes.Add(new RideType
        {
            Id = 1, Uuid = "uuid-inactive", Name = "LEXOR Vintage", Code = "VINTAGE", BaseFare = 200, PerKmRate = 50, PerMinuteRate = 10, MinimumFare = 300, Capacity = 3, IsActive = false
        });
        await context.SaveChangesAsync();

        var result = await service.GetRideTypeByUuidAdminAsync("uuid-inactive");

        Assert.NotNull(result);
        Assert.False(result.IsActive);
        Assert.Equal("VINTAGE", result.Code);
    }

    [Fact]
    public async Task CreateRideTypeAsync_SucceedsAndNormalizesCodeAndName()
    {
        var (service, _) = CreateService(nameof(CreateRideTypeAsync_SucceedsAndNormalizesCodeAndName));

        var request = new CreateRideTypeRequest
        {
            Name = "  LEXOR Green  ",
            Code = "green_ev",
            Description = "Eco friendly rides",
            BaseFare = 60,
            PerKmRate = 16,
            PerMinuteRate = 2.8m,
            MinimumFare = 90,
            CancellationFee = 35,
            Capacity = 4,
            IconUrl = "https://cdn.lexor.mobility/icons/green.png",
            IsActive = true
        };

        var result = await service.CreateRideTypeAsync(request);

        Assert.NotNull(result);
        Assert.Equal("LEXOR Green", result.Name);
        Assert.Equal("GREEN_EV", result.Code);
        Assert.NotEmpty(result.Uuid);
        Assert.True(result.IsActive);
    }

    [Fact]
    public async Task CreateRideTypeAsync_DuplicateCode_ThrowsDuplicateResourceException()
    {
        var (service, context) = CreateService(nameof(CreateRideTypeAsync_DuplicateCode_ThrowsDuplicateResourceException));

        context.RideTypes.Add(new RideType
        {
            Id = 1, Uuid = "uuid-1", Name = "LEXOR Go", Code = "GO", BaseFare = 50, PerKmRate = 15, PerMinuteRate = 2.5m, MinimumFare = 80, Capacity = 4, IsActive = true
        });
        await context.SaveChangesAsync();

        var request = new CreateRideTypeRequest
        {
            Name = "Another Go",
            Code = "go", // Duplicate code
            BaseFare = 50,
            PerKmRate = 15,
            PerMinuteRate = 2.5m,
            MinimumFare = 80,
            Capacity = 4
        };

        await Assert.ThrowsAsync<DuplicateResourceException>(() => service.CreateRideTypeAsync(request));
    }

    [Fact]
    public async Task CreateRideTypeAsync_DuplicateName_ThrowsDuplicateResourceException()
    {
        var (service, context) = CreateService(nameof(CreateRideTypeAsync_DuplicateName_ThrowsDuplicateResourceException));

        context.RideTypes.Add(new RideType
        {
            Id = 1, Uuid = "uuid-1", Name = "LEXOR Go", Code = "GO", BaseFare = 50, PerKmRate = 15, PerMinuteRate = 2.5m, MinimumFare = 80, Capacity = 4, IsActive = true
        });
        await context.SaveChangesAsync();

        var request = new CreateRideTypeRequest
        {
            Name = "LEXOR Go", // Duplicate name
            Code = "GO_NEW",
            BaseFare = 50,
            PerKmRate = 15,
            PerMinuteRate = 2.5m,
            MinimumFare = 80,
            Capacity = 4
        };

        await Assert.ThrowsAsync<DuplicateResourceException>(() => service.CreateRideTypeAsync(request));
    }

    [Fact]
    public async Task CreateRideTypeAsync_MinimumFareLessThanBaseFare_ThrowsBadRequestException()
    {
        var (service, _) = CreateService(nameof(CreateRideTypeAsync_MinimumFareLessThanBaseFare_ThrowsBadRequestException));

        var request = new CreateRideTypeRequest
        {
            Name = "Invalid",
            Code = "INVALID",
            BaseFare = 100,
            PerKmRate = 15,
            PerMinuteRate = 2.5m,
            MinimumFare = 50, // Invalid: minFare < baseFare
            Capacity = 4
        };

        await Assert.ThrowsAsync<BadRequestException>(() => service.CreateRideTypeAsync(request));
    }

    [Fact]
    public async Task CreateRideTypeAsync_NegativeBaseFare_ThrowsBadRequestException()
    {
        var (service, _) = CreateService(nameof(CreateRideTypeAsync_NegativeBaseFare_ThrowsBadRequestException));

        var request = new CreateRideTypeRequest
        {
            Name = "Negative",
            Code = "NEG",
            BaseFare = -10,
            PerKmRate = 15,
            PerMinuteRate = 2.5m,
            MinimumFare = 50,
            Capacity = 4
        };

        await Assert.ThrowsAsync<BadRequestException>(() => service.CreateRideTypeAsync(request));
    }

    [Fact]
    public async Task CreateRideTypeAsync_InvalidCapacity_ThrowsBadRequestException()
    {
        var (service, _) = CreateService(nameof(CreateRideTypeAsync_InvalidCapacity_ThrowsBadRequestException));

        var requestZero = new CreateRideTypeRequest
        {
            Name = "Zero Cap",
            Code = "ZERO",
            BaseFare = 50,
            PerKmRate = 15,
            PerMinuteRate = 2.5m,
            MinimumFare = 80,
            Capacity = 0 // Invalid: capacity < 1
        };

        await Assert.ThrowsAsync<BadRequestException>(() => service.CreateRideTypeAsync(requestZero));

        var requestExcess = new CreateRideTypeRequest
        {
            Name = "Excess Cap",
            Code = "EXCESS",
            BaseFare = 50,
            PerKmRate = 15,
            PerMinuteRate = 2.5m,
            MinimumFare = 80,
            Capacity = 25 // Invalid: capacity > 20
        };

        await Assert.ThrowsAsync<BadRequestException>(() => service.CreateRideTypeAsync(requestExcess));
    }

    [Fact]
    public async Task UpdateRideTypeAsync_Succeeds()
    {
        var (service, context) = CreateService(nameof(UpdateRideTypeAsync_Succeeds));

        context.RideTypes.Add(new RideType
        {
            Id = 1, Uuid = "uuid-update", Name = "LEXOR Go", Code = "GO", BaseFare = 50, PerKmRate = 15, PerMinuteRate = 2.5m, MinimumFare = 80, Capacity = 4, IsActive = true
        });
        await context.SaveChangesAsync();

        var request = new UpdateRideTypeRequest
        {
            Name = "LEXOR Go Prime",
            Description = "Updated hatchback experience",
            BaseFare = 55,
            PerKmRate = 16,
            PerMinuteRate = 2.6m,
            MinimumFare = 85,
            CancellationFee = 35,
            Capacity = 4,
            IconUrl = "https://cdn.lexor.mobility/icons/go-prime.png"
        };

        var result = await service.UpdateRideTypeAsync("uuid-update", request);

        Assert.Equal("LEXOR Go Prime", result.Name);
        Assert.Equal("GO", result.Code);
        Assert.Equal(55m, result.BaseFare);
    }

    [Fact]
    public async Task UpdateRideTypeAsync_DuplicateCode_ThrowsDuplicateResourceException()
    {
        var (service, context) = CreateService(nameof(UpdateRideTypeAsync_DuplicateCode_ThrowsDuplicateResourceException));

        context.RideTypes.AddRange(
            new RideType { Id = 1, Uuid = "uuid-1", Name = "LEXOR Go", Code = "GO", BaseFare = 50, PerKmRate = 15, PerMinuteRate = 2.5m, MinimumFare = 80, Capacity = 4, IsActive = true },
            new RideType { Id = 2, Uuid = "uuid-2", Name = "LEXOR XL", Code = "XL", BaseFare = 90, PerKmRate = 22, PerMinuteRate = 3.5m, MinimumFare = 140, Capacity = 6, IsActive = true }
        );
        await context.SaveChangesAsync();

        var request = new UpdateRideTypeRequest
        {
            Name = "LEXOR XL Modified",
            Code = "GO", // Duplicate of uuid-1
            BaseFare = 90,
            PerKmRate = 22,
            PerMinuteRate = 3.5m,
            MinimumFare = 140,
            Capacity = 6
        };

        await Assert.ThrowsAsync<DuplicateResourceException>(() => service.UpdateRideTypeAsync("uuid-2", request));
    }

    [Fact]
    public async Task UpdateRideTypeAsync_MissingUuid_ThrowsResourceNotFoundException()
    {
        var (service, _) = CreateService(nameof(UpdateRideTypeAsync_MissingUuid_ThrowsResourceNotFoundException));

        var request = new UpdateRideTypeRequest
        {
            Name = "Non Existent",
            BaseFare = 50,
            PerKmRate = 15,
            PerMinuteRate = 2.5m,
            MinimumFare = 80,
            Capacity = 4
        };

        await Assert.ThrowsAsync<ResourceNotFoundException>(() => service.UpdateRideTypeAsync("missing-uuid", request));
    }

    [Fact]
    public async Task UpdateRideTypeStatusAsync_DisablesCategoryWhenMultipleActive()
    {
        var (service, context) = CreateService(nameof(UpdateRideTypeStatusAsync_DisablesCategoryWhenMultipleActive));

        context.RideTypes.AddRange(
            new RideType { Id = 1, Uuid = "uuid-1", Name = "LEXOR Go", Code = "GO", BaseFare = 50, PerKmRate = 15, PerMinuteRate = 2.5m, MinimumFare = 80, Capacity = 4, IsActive = true },
            new RideType { Id = 2, Uuid = "uuid-2", Name = "LEXOR XL", Code = "XL", BaseFare = 90, PerKmRate = 22, PerMinuteRate = 3.5m, MinimumFare = 140, Capacity = 6, IsActive = true }
        );
        await context.SaveChangesAsync();

        var request = new UpdateRideTypeStatusRequest { IsActive = false, Reason = "Maintenance" };
        var result = await service.UpdateRideTypeStatusAsync("uuid-1", request);

        Assert.False(result.IsActive);
    }

    [Fact]
    public async Task UpdateRideTypeStatusAsync_PreventsDisablingLastActiveRideType()
    {
        var (service, context) = CreateService(nameof(UpdateRideTypeStatusAsync_PreventsDisablingLastActiveRideType));

        context.RideTypes.Add(new RideType
        {
            Id = 1, Uuid = "uuid-last", Name = "LEXOR Go", Code = "GO", BaseFare = 50, PerKmRate = 15, PerMinuteRate = 2.5m, MinimumFare = 80, Capacity = 4, IsActive = true
        });
        await context.SaveChangesAsync();

        var request = new UpdateRideTypeStatusRequest { IsActive = false, Reason = "Deactivating last category" };

        var ex = await Assert.ThrowsAsync<BadRequestException>(() => service.UpdateRideTypeStatusAsync("uuid-last", request));
        Assert.Contains("Cannot disable the last active ride type", ex.Message);
    }

    [Fact]
    public async Task UpdateRideTypeStatusAsync_MissingUuid_ThrowsResourceNotFoundException()
    {
        var (service, _) = CreateService(nameof(UpdateRideTypeStatusAsync_MissingUuid_ThrowsResourceNotFoundException));

        var request = new UpdateRideTypeStatusRequest { IsActive = true };
        await Assert.ThrowsAsync<ResourceNotFoundException>(() => service.UpdateRideTypeStatusAsync("missing-uuid", request));
    }

    [Fact]
    public async Task DeleteRideTypeSafelyAsync_RejectsHardDeletion()
    {
        var (service, context) = CreateService(nameof(DeleteRideTypeSafelyAsync_RejectsHardDeletion));

        context.RideTypes.Add(new RideType
        {
            Id = 1, Uuid = "uuid-delete", Name = "LEXOR Go", Code = "GO", BaseFare = 50, PerKmRate = 15, PerMinuteRate = 2.5m, MinimumFare = 80, Capacity = 4, IsActive = true
        });
        await context.SaveChangesAsync();

        var ex = await Assert.ThrowsAsync<BadRequestException>(() => service.DeleteRideTypeSafelyAsync("uuid-delete"));
        Assert.Contains("Hard deletion of ride categories is forbidden", ex.Message);
    }

    [Fact]
    public async Task DeleteRideTypeSafelyAsync_MissingUuid_ThrowsResourceNotFoundException()
    {
        var (service, _) = CreateService(nameof(DeleteRideTypeSafelyAsync_MissingUuid_ThrowsResourceNotFoundException));

        await Assert.ThrowsAsync<ResourceNotFoundException>(() => service.DeleteRideTypeSafelyAsync("missing-uuid"));
    }
}
