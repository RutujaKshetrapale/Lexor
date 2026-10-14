using System.IO;
using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Http;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging.Abstractions;
using RideTypeService.DTOs.Response;
using RideTypeService.Entities;
using RideTypeService.Enums;
using RideTypeService.Exceptions;
using RideTypeService.Middleware;
using Xunit;

namespace RideTypeService.Tests;

public class MiddlewareTests
{
    [Fact]
    public async Task GlobalExceptionMiddleware_DbUpdateException_Returns409Conflict()
    {
        var middleware = new GlobalExceptionMiddleware(
            _ => throw new DbUpdateException("Duplicate entry in MySQL table"),
            NullLogger<GlobalExceptionMiddleware>.Instance
        );

        var context = new DefaultHttpContext();
        context.Response.Body = new MemoryStream();

        await middleware.InvokeAsync(context);

        Assert.Equal(StatusCodes.Status409Conflict, context.Response.StatusCode);

        context.Response.Body.Seek(0, SeekOrigin.Begin);
        var response = await JsonSerializer.DeserializeAsync<ErrorResponse>(context.Response.Body, new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        });

        Assert.NotNull(response);
        Assert.Equal("DATA_INTEGRITY_VIOLATION", response.Error);
        Assert.Equal(409, response.Status);
    }

    [Fact]
    public async Task GlobalExceptionMiddleware_ResourceNotFoundException_Returns404NotFound()
    {
        var middleware = new GlobalExceptionMiddleware(
            _ => throw new ResourceNotFoundException("Ride type not found with UUID: 123"),
            NullLogger<GlobalExceptionMiddleware>.Instance
        );

        var context = new DefaultHttpContext();
        context.Response.Body = new MemoryStream();

        await middleware.InvokeAsync(context);

        Assert.Equal(StatusCodes.Status404NotFound, context.Response.StatusCode);

        context.Response.Body.Seek(0, SeekOrigin.Begin);
        var response = await JsonSerializer.DeserializeAsync<ErrorResponse>(context.Response.Body, new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        });

        Assert.NotNull(response);
        Assert.Equal("RESOURCE_NOT_FOUND", response.Error);
        Assert.Equal(404, response.Status);
    }

    [Fact]
    public async Task GlobalExceptionMiddleware_BadRequestException_Returns400BadRequest()
    {
        var middleware = new GlobalExceptionMiddleware(
            _ => throw new BadRequestException("Minimum fare cannot be less than base fare"),
            NullLogger<GlobalExceptionMiddleware>.Instance
        );

        var context = new DefaultHttpContext();
        context.Response.Body = new MemoryStream();

        await middleware.InvokeAsync(context);

        Assert.Equal(StatusCodes.Status400BadRequest, context.Response.StatusCode);

        context.Response.Body.Seek(0, SeekOrigin.Begin);
        var response = await JsonSerializer.DeserializeAsync<ErrorResponse>(context.Response.Body, new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        });

        Assert.NotNull(response);
        Assert.Equal("BAD_REQUEST", response.Error);
        Assert.Equal(400, response.Status);
    }

    [Fact]
    public async Task GlobalExceptionMiddleware_DuplicateResourceException_Returns409Conflict()
    {
        var middleware = new GlobalExceptionMiddleware(
            _ => throw new DuplicateResourceException("Ride type code already exists: GO"),
            NullLogger<GlobalExceptionMiddleware>.Instance
        );

        var context = new DefaultHttpContext();
        context.Response.Body = new MemoryStream();

        await middleware.InvokeAsync(context);

        Assert.Equal(StatusCodes.Status409Conflict, context.Response.StatusCode);

        context.Response.Body.Seek(0, SeekOrigin.Begin);
        var response = await JsonSerializer.DeserializeAsync<ErrorResponse>(context.Response.Body, new JsonSerializerOptions
        {
            PropertyNameCaseInsensitive = true
        });

        Assert.NotNull(response);
        Assert.Equal("DUPLICATE_RESOURCE", response.Error);
        Assert.Equal(409, response.Status);
    }

    [Fact]
    public async Task UserStatusMiddleware_SuspendedUser_Returns401Unauthorized()
    {
        var dbContext = TestDbContextFactory.Create(nameof(UserStatusMiddleware_SuspendedUser_Returns401Unauthorized));
        dbContext.Users.Add(new User
        {
            Id = 1,
            Uuid = "suspended-user-uuid",
            FirstName = "Bad",
            LastName = "Actor",
            Email = "bad@lexor.mobility",
            Role = Role.RIDER,
            Status = UserStatus.SUSPENDED
        });
        await dbContext.SaveChangesAsync();

        var middleware = new UserStatusMiddleware(
            _ => Task.CompletedTask,
            NullLogger<UserStatusMiddleware>.Instance
        );

        var context = new DefaultHttpContext();
        context.Response.Body = new MemoryStream();
        context.User = new ClaimsPrincipal(new ClaimsIdentity(new[]
        {
            new Claim("sub", "suspended-user-uuid"),
            new Claim(ClaimTypes.Role, "ROLE_RIDER")
        }, "TestAuth"));

        await middleware.InvokeAsync(context, dbContext);

        Assert.Equal(StatusCodes.Status401Unauthorized, context.Response.StatusCode);
    }
}
