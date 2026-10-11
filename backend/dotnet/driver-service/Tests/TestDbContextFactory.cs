using DriverService.Data;
using Microsoft.EntityFrameworkCore;

namespace DriverService.Tests;

public static class TestDbContextFactory
{
    public static LexorDbContext Create(string dbName)
    {
        var options = new DbContextOptionsBuilder<LexorDbContext>()
            .UseInMemoryDatabase(databaseName: dbName)
            .Options;

        return new LexorDbContext(options);
    }
}
