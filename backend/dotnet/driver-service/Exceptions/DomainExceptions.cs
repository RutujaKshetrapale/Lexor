namespace DriverService.Exceptions;

public class ResourceNotFoundException : Exception
{
    public ResourceNotFoundException(string message) : base(message)
    {
    }
}

public class BadRequestException : Exception
{
    public BadRequestException(string message) : base(message)
    {
    }
}

public class DuplicateResourceException : Exception
{
    public DuplicateResourceException(string message) : base(message)
    {
    }
}

public class ForbiddenException : Exception
{
    public ForbiddenException(string message) : base(message)
    {
    }
}

public class FileStorageException : Exception
{
    public FileStorageException(string message) : base(message)
    {
    }

    public FileStorageException(string message, Exception innerException) : base(message, innerException)
    {
    }
}
