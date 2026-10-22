using AdminOperationsService.Data;
using Microsoft.EntityFrameworkCore;

namespace AdminOperationsService.Tests;

public static class TestDbContextFactory
{
    public static AdminOperationsDbContext Create()
    {
        var options = new DbContextOptionsBuilder<AdminOperationsDbContext>()
            .UseInMemoryDatabase(databaseName: Guid.NewGuid().ToString())
            .Options;

        return new AdminOperationsDbContext(options);
    }
}
