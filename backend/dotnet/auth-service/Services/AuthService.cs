using AuthService.Data;
using AuthService.DTOs;
using AuthService.Entities;
using AuthService.Exceptions;
using AuthService.Security;
using Microsoft.EntityFrameworkCore;

namespace AuthService.Services;

public class AuthService : IAuthService
{
    private readonly LexorDbContext _dbContext;
    private readonly IPasswordHasher _passwordHasher;
    private readonly IJwtTokenService _jwtTokenService;

    public AuthService(
        LexorDbContext dbContext,
        IPasswordHasher passwordHasher,
        IJwtTokenService jwtTokenService)
    {
        _dbContext = dbContext;
        _passwordHasher = passwordHasher;
        _jwtTokenService = jwtTokenService;
    }

    public async Task<AuthResponse> RegisterAsync(RegisterRequest request)
    {
        if (request.Role == UserRole.ADMIN)
        {
            throw new ForbiddenException("Public registration for ADMIN role is not permitted");
        }

        var normalizedEmail = request.Email.ToLowerInvariant().Trim();
        var normalizedPhone = request.PhoneNumber.Trim();

        if (await _dbContext.Users.AnyAsync(u => u.Email == normalizedEmail))
        {
            throw new DuplicateResourceException($"Email is already registered: {normalizedEmail}");
        }

        if (await _dbContext.Users.AnyAsync(u => u.PhoneNumber == normalizedPhone))
        {
            throw new DuplicateResourceException($"Phone number is already registered: {normalizedPhone}");
        }

        var user = new User
        {
            Uuid = Guid.NewGuid().ToString(),
            FirstName = request.FirstName.Trim(),
            LastName = request.LastName.Trim(),
            Email = normalizedEmail,
            PhoneNumber = normalizedPhone,
            PasswordHash = _passwordHasher.HashPassword(request.Password),
            Role = request.Role,
            Status = UserStatus.ACTIVE,
            EmailVerified = false,
            PhoneVerified = false,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _dbContext.Users.Add(user);
        await _dbContext.SaveChangesAsync();

        var token = _jwtTokenService.GenerateToken(user);

        return new AuthResponse
        {
            AccessToken = token,
            TokenType = "Bearer",
            ExpiresIn = _jwtTokenService.GetExpirationSeconds(),
            User = MapToUserResponse(user)
        };
    }

    public async Task<AuthResponse> LoginAsync(LoginRequest request)
    {
        var normalizedEmail = request.Email.ToLowerInvariant().Trim();

        var user = await _dbContext.Users.FirstOrDefaultAsync(u => u.Email == normalizedEmail);
        if (user == null)
        {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (!_passwordHasher.VerifyPassword(request.Password, user.PasswordHash))
        {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        if (user.Status != UserStatus.ACTIVE)
        {
            throw new ForbiddenException($"User account is {user.Status.ToString().ToLowerInvariant()}");
        }

        var token = _jwtTokenService.GenerateToken(user);

        return new AuthResponse
        {
            AccessToken = token,
            TokenType = "Bearer",
            ExpiresIn = _jwtTokenService.GetExpirationSeconds(),
            User = MapToUserResponse(user)
        };
    }

    public async Task<UserResponse> GetCurrentUserAsync(string userUuid)
    {
        var user = await _dbContext.Users.FirstOrDefaultAsync(u => u.Uuid == userUuid);
        if (user == null)
        {
            throw new ResourceNotFoundException($"User not found with UUID: {userUuid}");
        }

        return MapToUserResponse(user);
    }

    private static UserResponse MapToUserResponse(User user)
    {
        return new UserResponse
        {
            Id = user.Id,
            Uuid = user.Uuid,
            FirstName = user.FirstName,
            LastName = user.LastName,
            Email = user.Email,
            PhoneNumber = user.PhoneNumber,
            Role = user.Role,
            Status = user.Status,
            ProfilePictureUrl = user.ProfilePictureUrl,
            EmailVerified = user.EmailVerified,
            PhoneVerified = user.PhoneVerified,
            CreatedAt = user.CreatedAt
        };
    }
}
