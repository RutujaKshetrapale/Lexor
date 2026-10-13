namespace VehicleService.Exceptions;

public class InvalidStatusTransitionException : Exception
{
    public InvalidStatusTransitionException(string message) : base(message)
    {
    }

    public InvalidStatusTransitionException(string message, Exception innerException) : base(message, innerException)
    {
    }
}
