using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace FeedbackService.Domain.Entities;

[Table("driver_rating_summaries")]
public class DriverRatingSummary
{
    [Key]
    [Column("id")]
    public long Id { get; set; }

    [Required]
    [MaxLength(36)]
    [Column("driver_uuid")]
    public string DriverUuid { get; set; } = string.Empty;

    [Column("total_ratings")]
    public int TotalRatings { get; set; } = 0;

    [Column("average_rating", TypeName = "decimal(3,2)")]
    public decimal AverageRating { get; set; } = 5.00m;

    [Column("five_star_count")]
    public int FiveStarCount { get; set; } = 0;

    [Column("four_star_count")]
    public int FourStarCount { get; set; } = 0;

    [Column("three_star_count")]
    public int ThreeStarCount { get; set; } = 0;

    [Column("two_star_count")]
    public int TwoStarCount { get; set; } = 0;

    [Column("one_star_count")]
    public int OneStarCount { get; set; } = 0;

    [Column("excluded_ratings_count")]
    public int ExcludedRatingsCount { get; set; } = 0;

    [Column("total_completed_rides")]
    public int TotalCompletedRides { get; set; } = 0;

    [Column("last_recalculated_at")]
    public DateTime LastRecalculatedAt { get; set; } = DateTime.UtcNow;
}
