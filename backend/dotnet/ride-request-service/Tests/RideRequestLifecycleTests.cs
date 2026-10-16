using RideRequestService.Enums;
using Xunit;

namespace RideRequestService.Tests;

public class RideRequestLifecycleTests
{
    [Theory]
    [InlineData(RideRequestStatus.REQUESTED, true)]
    [InlineData(RideRequestStatus.SEARCHING, true)]
    [InlineData(RideRequestStatus.MATCHED, false)]
    [InlineData(RideRequestStatus.CANCELLED, false)]
    [InlineData(RideRequestStatus.EXPIRED, false)]
    public void CanCancel_EvaluatesCorrectlyForStatus(RideRequestStatus status, bool expectedCanCancel)
    {
        Assert.Equal(expectedCanCancel, status.CanCancel());
    }

    [Theory]
    [InlineData(RideRequestStatus.REQUESTED, false)]
    [InlineData(RideRequestStatus.SEARCHING, false)]
    [InlineData(RideRequestStatus.MATCHED, true)]
    [InlineData(RideRequestStatus.CANCELLED, true)]
    [InlineData(RideRequestStatus.EXPIRED, true)]
    public void IsTerminal_EvaluatesCorrectlyForStatus(RideRequestStatus status, bool expectedIsTerminal)
    {
        Assert.Equal(expectedIsTerminal, status.IsTerminal());
    }

    [Theory]
    [InlineData(RideRequestStatus.REQUESTED, RideRequestStatus.SEARCHING, true)]
    [InlineData(RideRequestStatus.REQUESTED, RideRequestStatus.CANCELLED, true)]
    [InlineData(RideRequestStatus.REQUESTED, RideRequestStatus.MATCHED, false)]
    [InlineData(RideRequestStatus.SEARCHING, RideRequestStatus.MATCHED, true)]
    [InlineData(RideRequestStatus.SEARCHING, RideRequestStatus.CANCELLED, true)]
    [InlineData(RideRequestStatus.SEARCHING, RideRequestStatus.EXPIRED, true)]
    [InlineData(RideRequestStatus.MATCHED, RideRequestStatus.CANCELLED, false)]
    [InlineData(RideRequestStatus.CANCELLED, RideRequestStatus.REQUESTED, false)]
    [InlineData(RideRequestStatus.EXPIRED, RideRequestStatus.SEARCHING, false)]
    public void CanTransitionTo_EnforcesLifecycleRules(RideRequestStatus current, RideRequestStatus next, bool allowed)
    {
        Assert.Equal(allowed, current.CanTransitionTo(next));
    }
}
