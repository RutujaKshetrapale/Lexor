using Microsoft.EntityFrameworkCore;
using UserService.Data;
using UserService.DTOs.Request;
using UserService.DTOs.Response;
using UserService.Entities;
using UserService.Exceptions;
using UserService.Security;

namespace UserService.Services;

public class UserService : IUserService
{
    private readonly LexorDbContext _context;
    private readonly IPasswordHasher _passwordHasher;
    private readonly ILogger<UserService> _logger;

    public UserService(LexorDbContext context, IPasswordHasher passwordHasher, ILogger<UserService> logger)
    {
        _context = context;
        _passwordHasher = passwordHasher;
        _logger = logger;
    }

    public async Task<UserResponse> GetCurrentUserProfileAsync(string currentUserUuid, CancellationToken cancellationToken = default)
    {
        var user = await FindUserByUuidOrThrowAsync(currentUserUuid, cancellationToken);
        return MapToUserResponse(user);
    }

    public async Task<UserResponse> UpdateCurrentUserProfileAsync(string currentUserUuid, UpdateProfileRequest request, CancellationToken cancellationToken = default)
    {
        var user = await FindUserByUuidOrThrowAsync(currentUserUuid, cancellationToken);
        var newPhone = request.PhoneNumber.Trim();

        if (!user.PhoneNumber.Equals(newPhone, StringComparison.OrdinalIgnoreCase))
        {
            if (await _context.Users.AnyAsync(u => u.PhoneNumber == newPhone && u.Uuid != currentUserUuid, cancellationToken))
            {
                throw new DuplicateResourceException("Phone number already registered by another user");
            }
        }

        user.FirstName = request.FirstName.Trim();
        user.LastName = request.LastName.Trim();
        user.PhoneNumber = newPhone;
        user.ProfilePictureUrl = request.ProfilePictureUrl;
        user.UpdatedAt = DateTime.UtcNow;

        await _context.SaveChangesAsync(cancellationToken);
        return MapToUserResponse(user);
    }

    public async Task<UserResponse> PatchCurrentUserProfileAsync(string currentUserUuid, PatchProfileRequest request, CancellationToken cancellationToken = default)
    {
        var user = await FindUserByUuidOrThrowAsync(currentUserUuid, cancellationToken);

        if (!string.IsNullOrWhiteSpace(request.FirstName))
        {
            user.FirstName = request.FirstName.Trim();
        }

        if (!string.IsNullOrWhiteSpace(request.LastName))
        {
            user.LastName = request.LastName.Trim();
        }

        if (!string.IsNullOrWhiteSpace(request.PhoneNumber))
        {
            var newPhone = request.PhoneNumber.Trim();
            if (!user.PhoneNumber.Equals(newPhone, StringComparison.OrdinalIgnoreCase))
            {
                if (await _context.Users.AnyAsync(u => u.PhoneNumber == newPhone && u.Uuid != currentUserUuid, cancellationToken))
                {
                    throw new DuplicateResourceException("Phone number already registered by another user");
                }
                user.PhoneNumber = newPhone;
            }
        }

        if (request.ProfilePictureUrl != null)
        {
            user.ProfilePictureUrl = request.ProfilePictureUrl;
        }

        user.UpdatedAt = DateTime.UtcNow;

        await _context.SaveChangesAsync(cancellationToken);
        return MapToUserResponse(user);
    }

    public async Task DeactivateCurrentUserAsync(string currentUserUuid, CancellationToken cancellationToken = default)
    {
        var user = await FindUserByUuidOrThrowAsync(currentUserUuid, cancellationToken);

        if (user.Role == UserRole.ADMIN)
        {
            throw new ForbiddenException("Admin accounts cannot be self-deactivated via this endpoint");
        }

        user.Status = UserStatus.DEACTIVATED;
        user.UpdatedAt = DateTime.UtcNow;

        await _context.SaveChangesAsync(cancellationToken);
        _logger.LogInformation("User [{Uuid}] self-deactivated their account", user.Uuid);
    }

    public async Task<UserResponse> CreateUserAsync(AdminCreateUserRequest request, CancellationToken cancellationToken = default)
    {
        var email = request.Email.Trim().ToLowerInvariant();
        var phone = request.PhoneNumber.Trim();

        if (await _context.Users.AnyAsync(u => u.Email == email, cancellationToken))
        {
            throw new DuplicateResourceException("Email address already registered");
        }

        if (await _context.Users.AnyAsync(u => u.PhoneNumber == phone, cancellationToken))
        {
            throw new DuplicateResourceException("Phone number already registered");
        }

        var initialStatus = request.Status ?? UserStatus.ACTIVE;

        var user = new User
        {
            Uuid = Guid.NewGuid().ToString(),
            FirstName = request.FirstName.Trim(),
            LastName = request.LastName.Trim(),
            Email = email,
            PhoneNumber = phone,
            PasswordHash = _passwordHasher.HashPassword(request.Password),
            Role = request.Role,
            Status = initialStatus,
            ProfilePictureUrl = request.ProfilePictureUrl,
            EmailVerified = false,
            PhoneVerified = false,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        _context.Users.Add(user);
        await _context.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("Admin created new user with UUID [{Uuid}] and role [{Role}]", user.Uuid, user.Role);
        return MapToUserResponse(user);
    }

    public async Task<PagedResponse<UserResponse>> GetUsersAsync(
        UserRole? role,
        UserStatus? status,
        string? search,
        int page,
        int size,
        string sortBy,
        string sortDir,
        CancellationToken cancellationToken = default)
    {
        int clampedSize = Math.Clamp(size, 1, 100);
        int clampedPage = Math.Max(0, page);

        var query = _context.Users.AsNoTracking();

        if (role.HasValue)
        {
            query = query.Where(u => u.Role == role.Value);
        }

        if (status.HasValue)
        {
            query = query.Where(u => u.Status == status.Value);
        }

        if (!string.IsNullOrWhiteSpace(search))
        {
            var searchTerm = search.Trim().ToLowerInvariant();
            query = query.Where(u =>
                u.FirstName.ToLower().Contains(searchTerm) ||
                u.LastName.ToLower().Contains(searchTerm) ||
                u.Email.ToLower().Contains(searchTerm));
        }

        bool isAsc = string.Equals(sortDir, "asc", StringComparison.OrdinalIgnoreCase);

        query = sortBy?.ToLowerInvariant() switch
        {
            "id" => isAsc ? query.OrderBy(u => u.Id) : query.OrderByDescending(u => u.Id),
            "uuid" => isAsc ? query.OrderBy(u => u.Uuid) : query.OrderByDescending(u => u.Uuid),
            "firstname" => isAsc ? query.OrderBy(u => u.FirstName) : query.OrderByDescending(u => u.FirstName),
            "lastname" => isAsc ? query.OrderBy(u => u.LastName) : query.OrderByDescending(u => u.LastName),
            "email" => isAsc ? query.OrderBy(u => u.Email) : query.OrderByDescending(u => u.Email),
            "role" => isAsc ? query.OrderBy(u => u.Role) : query.OrderByDescending(u => u.Role),
            "status" => isAsc ? query.OrderBy(u => u.Status) : query.OrderByDescending(u => u.Status),
            "updatedat" => isAsc ? query.OrderBy(u => u.UpdatedAt) : query.OrderByDescending(u => u.UpdatedAt),
            _ => isAsc ? query.OrderBy(u => u.CreatedAt) : query.OrderByDescending(u => u.CreatedAt)
        };

        var totalElements = await query.CountAsync(cancellationToken);

        var items = await query
            .Skip(clampedPage * clampedSize)
            .Take(clampedSize)
            .ToListAsync(cancellationToken);

        var content = items.Select(MapToUserResponse).ToList();
        var totalPages = (int)Math.Ceiling(totalElements / (double)clampedSize);
        bool isLast = (clampedPage + 1) >= totalPages || totalPages == 0;

        return new PagedResponse<UserResponse>
        {
            Content = content,
            Page = clampedPage,
            Size = clampedSize,
            TotalElements = totalElements,
            TotalPages = totalPages,
            Last = isLast
        };
    }

    public async Task<UserResponse> GetUserByUuidAsync(string uuid, CancellationToken cancellationToken = default)
    {
        var user = await FindUserByUuidOrThrowAsync(uuid, cancellationToken);
        return MapToUserResponse(user);
    }

    public async Task<UserResponse> UpdateUserByUuidAsync(string uuid, AdminUpdateUserRequest request, CancellationToken cancellationToken = default)
    {
        var user = await FindUserByUuidOrThrowAsync(uuid, cancellationToken);
        var newEmail = request.Email.Trim().ToLowerInvariant();
        var newPhone = request.PhoneNumber.Trim();

        if (!user.Email.Equals(newEmail, StringComparison.OrdinalIgnoreCase))
        {
            if (await _context.Users.AnyAsync(u => u.Email == newEmail && u.Uuid != uuid, cancellationToken))
            {
                throw new DuplicateResourceException("Email address already registered by another user");
            }
        }

        if (!user.PhoneNumber.Equals(newPhone, StringComparison.OrdinalIgnoreCase))
        {
            if (await _context.Users.AnyAsync(u => u.PhoneNumber == newPhone && u.Uuid != uuid, cancellationToken))
            {
                throw new DuplicateResourceException("Phone number already registered by another user");
            }
        }

        user.FirstName = request.FirstName.Trim();
        user.LastName = request.LastName.Trim();
        user.Email = newEmail;
        user.PhoneNumber = newPhone;
        user.ProfilePictureUrl = request.ProfilePictureUrl;
        user.UpdatedAt = DateTime.UtcNow;

        await _context.SaveChangesAsync(cancellationToken);
        _logger.LogInformation("Admin updated user profile for UUID [{Uuid}]", uuid);

        return MapToUserResponse(user);
    }

    public async Task<UserResponse> PatchUserByUuidAsync(string uuid, AdminPatchUserRequest request, CancellationToken cancellationToken = default)
    {
        var user = await FindUserByUuidOrThrowAsync(uuid, cancellationToken);

        if (!string.IsNullOrWhiteSpace(request.FirstName))
        {
            user.FirstName = request.FirstName.Trim();
        }

        if (!string.IsNullOrWhiteSpace(request.LastName))
        {
            user.LastName = request.LastName.Trim();
        }

        if (!string.IsNullOrWhiteSpace(request.Email))
        {
            var newEmail = request.Email.Trim().ToLowerInvariant();
            if (!user.Email.Equals(newEmail, StringComparison.OrdinalIgnoreCase))
            {
                if (await _context.Users.AnyAsync(u => u.Email == newEmail && u.Uuid != uuid, cancellationToken))
                {
                    throw new DuplicateResourceException("Email address already registered by another user");
                }
                user.Email = newEmail;
            }
        }

        if (!string.IsNullOrWhiteSpace(request.PhoneNumber))
        {
            var newPhone = request.PhoneNumber.Trim();
            if (!user.PhoneNumber.Equals(newPhone, StringComparison.OrdinalIgnoreCase))
            {
                if (await _context.Users.AnyAsync(u => u.PhoneNumber == newPhone && u.Uuid != uuid, cancellationToken))
                {
                    throw new DuplicateResourceException("Phone number already registered by another user");
                }
                user.PhoneNumber = newPhone;
            }
        }

        if (request.ProfilePictureUrl != null)
        {
            user.ProfilePictureUrl = request.ProfilePictureUrl;
        }

        user.UpdatedAt = DateTime.UtcNow;

        await _context.SaveChangesAsync(cancellationToken);
        _logger.LogInformation("Admin patched user profile for UUID [{Uuid}]", uuid);

        return MapToUserResponse(user);
    }

    public async Task<UserResponse> UpdateUserStatusAsync(string adminUuid, string uuid, UpdateUserStatusRequest request, CancellationToken cancellationToken = default)
    {
        var targetUser = await FindUserByUuidOrThrowAsync(uuid, cancellationToken);

        var currentStatus = targetUser.Status;
        var newStatus = request.Status;

        if (currentStatus == newStatus)
        {
            return MapToUserResponse(targetUser);
        }

        ValidateStatusTransition(currentStatus, newStatus);

        if (adminUuid.Equals(uuid, StringComparison.OrdinalIgnoreCase) && (newStatus == UserStatus.DEACTIVATED || newStatus == UserStatus.SUSPENDED))
        {
            throw new BadRequestException($"Administrators cannot change their own account status to {newStatus}");
        }

        if (targetUser.Role == UserRole.ADMIN && currentStatus == UserStatus.ACTIVE &&
            (newStatus == UserStatus.DEACTIVATED || newStatus == UserStatus.SUSPENDED))
        {
            var activeAdminCount = await _context.Users.CountAsync(u => u.Role == UserRole.ADMIN && u.Status == UserStatus.ACTIVE, cancellationToken);
            if (activeAdminCount <= 1)
            {
                throw new ForbiddenException("Cannot deactivate or suspend the last active administrator");
            }
        }

        targetUser.Status = newStatus;
        targetUser.UpdatedAt = DateTime.UtcNow;

        await _context.SaveChangesAsync(cancellationToken);
        _logger.LogInformation("Admin [{AdminUuid}] changed status of user [{TargetUuid}] from [{CurrentStatus}] to [{NewStatus}]", adminUuid, uuid, currentStatus, newStatus);

        return MapToUserResponse(targetUser);
    }

    public async Task DeactivateUserByUuidAsync(string adminUuid, string uuid, CancellationToken cancellationToken = default)
    {
        var targetUser = await FindUserByUuidOrThrowAsync(uuid, cancellationToken);

        if (adminUuid.Equals(uuid, StringComparison.OrdinalIgnoreCase))
        {
            throw new BadRequestException("Administrators cannot deactivate their own account via administrative deletion");
        }

        if (targetUser.Role == UserRole.ADMIN && targetUser.Status == UserStatus.ACTIVE)
        {
            var activeAdminCount = await _context.Users.CountAsync(u => u.Role == UserRole.ADMIN && u.Status == UserStatus.ACTIVE, cancellationToken);
            if (activeAdminCount <= 1)
            {
                throw new ForbiddenException("Cannot deactivate the last active administrator");
            }
        }

        targetUser.Status = UserStatus.DEACTIVATED;
        targetUser.UpdatedAt = DateTime.UtcNow;

        await _context.SaveChangesAsync(cancellationToken);
        _logger.LogInformation("Admin [{AdminUuid}] soft-deactivated user [{TargetUuid}]", adminUuid, uuid);
    }

    private async Task<User> FindUserByUuidOrThrowAsync(string uuid, CancellationToken cancellationToken)
    {
        var user = await _context.Users.FirstOrDefaultAsync(u => u.Uuid == uuid, cancellationToken);
        if (user == null)
        {
            throw new ResourceNotFoundException($"User not found with UUID: {uuid}");
        }
        return user;
    }

    private static void ValidateStatusTransition(UserStatus current, UserStatus target)
    {
        bool valid = current switch
        {
            UserStatus.PENDING => target == UserStatus.ACTIVE || target == UserStatus.DEACTIVATED,
            UserStatus.ACTIVE => target == UserStatus.SUSPENDED || target == UserStatus.DEACTIVATED,
            UserStatus.SUSPENDED => target == UserStatus.ACTIVE || target == UserStatus.DEACTIVATED,
            UserStatus.DEACTIVATED => target == UserStatus.ACTIVE,
            _ => false
        };

        if (!valid)
        {
            throw new InvalidStatusTransitionException($"Invalid status transition from {current} to {target}");
        }
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
            CreatedAt = user.CreatedAt,
            UpdatedAt = user.UpdatedAt
        };
    }
}
