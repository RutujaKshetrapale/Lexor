using FeedbackService.Data;
using Microsoft.EntityFrameworkCore;

namespace FeedbackService.Tests;

public static class TestDbContextFactory
{
    public static FeedbackDbContext Create(string dbName)
    {
        var options = new DbContextOptionsBuilder<FeedbackDbContext>()
            .UseInMemoryDatabase(databaseName: dbName)
            .Options;

        var context = new FeedbackDbContext(options);
        context.Database.EnsureCreated();
        return context;
    }
}
