using UserService.DTOs.Request;
using UserService.DTOs.Response;
using UserService.Entities;

namespace UserService.Services;

public interface IUserService
{
    Task<UserResponse> GetCurrentUserProfileAsync(string currentUserUuid, CancellationToken cancellationToken = default);

    Task<UserResponse> UpdateCurrentUserProfileAsync(string currentUserUuid, UpdateProfileRequest request, CancellationToken cancellationToken = default);

    Task<UserResponse> PatchCurrentUserProfileAsync(string currentUserUuid, PatchProfileRequest request, CancellationToken cancellationToken = default);

    Task DeactivateCurrentUserAsync(string currentUserUuid, CancellationToken cancellationToken = default);

    Task<UserResponse> CreateUserAsync(AdminCreateUserRequest request, CancellationToken cancellationToken = default);

    Task<PagedResponse<UserResponse>> GetUsersAsync(UserRole? role, UserStatus? status, string? search, int page, int size, string sortBy, string sortDir, CancellationToken cancellationToken = default);

    Task<UserResponse> GetUserByUuidAsync(string uuid, CancellationToken cancellationToken = default);

    Task<UserResponse> UpdateUserByUuidAsync(string uuid, AdminUpdateUserRequest request, CancellationToken cancellationToken = default);

    Task<UserResponse> PatchUserByUuidAsync(string uuid, AdminPatchUserRequest request, CancellationToken cancellationToken = default);

    Task<UserResponse> UpdateUserStatusAsync(string adminUuid, string uuid, UpdateUserStatusRequest request, CancellationToken cancellationToken = default);

    Task DeactivateUserByUuidAsync(string adminUuid, string uuid, CancellationToken cancellationToken = default);
}
