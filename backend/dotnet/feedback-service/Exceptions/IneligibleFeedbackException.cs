namespace FeedbackService.Exceptions;

public class IneligibleFeedbackException : Exception
{
    public IneligibleFeedbackException(string message) : base(message) { }
}
