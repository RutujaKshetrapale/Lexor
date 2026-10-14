using Microsoft.EntityFrameworkCore;
using RideTypeService.Data;

namespace RideTypeService.Tests;

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
