using System.ComponentModel.DataAnnotations;
using UserService.Entities;

namespace UserService.DTOs.Request;

public class UpdateUserStatusRequest
{
    [Required(ErrorMessage = "Status is required")]
    public UserStatus Status { get; set; }
}
