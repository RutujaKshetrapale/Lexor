using Microsoft.EntityFrameworkCore;
using DriverMatchingService.Data;

namespace DriverMatchingService.Tests;

public static class TestDbContextFactory
{
    public static LexorDbContext Create(string dbName)
    {
        var options = new DbContextOptionsBuilder<LexorDbContext>()
            .UseInMemoryDatabase(databaseName: dbName)
            .Options;

        var context = new LexorDbContext(options);
        context.Database.EnsureCreated();
        return context;
    }
}
