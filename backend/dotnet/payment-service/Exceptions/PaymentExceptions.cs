namespace PaymentService.Exceptions;

public class BadRequestException : Exception
{
    public BadRequestException(string message) : base(message) { }
}

public class ResourceNotFoundException : Exception
{
    public ResourceNotFoundException(string message) : base(message) { }
}

public class UnauthorizedException : Exception
{
    public UnauthorizedException(string message) : base(message) { }
}

public class ForbiddenException : Exception
{
    public ForbiddenException(string message) : base(message) { }
}

public class ConflictException : Exception
{
    public ConflictException(string message) : base(message) { }
}

public class PaymentGatewayException : Exception
{
    public PaymentGatewayException(string message) : base(message) { }
    public PaymentGatewayException(string message, Exception innerException) : base(message, innerException) { }
}
