namespace NotificationService.Exceptions;

public class NotificationException : Exception
{
    public NotificationException(string message) : base(message) { }
    public NotificationException(string message, Exception inner) : base(message, inner) { }
}

public class ResourceNotFoundException : NotificationException
{
    public ResourceNotFoundException(string message) : base(message) { }
}

public class ForbiddenException : NotificationException
{
    public ForbiddenException(string message) : base(message) { }
}

public class BadRequestException : NotificationException
{
    public BadRequestException(string message) : base(message) { }
}

public class DuplicateEventException : NotificationException
{
    public DuplicateEventException(string message) : base(message) { }
}

public class RateLimitExceededException : NotificationException
{
    public RateLimitExceededException(string message) : base(message) { }
}
