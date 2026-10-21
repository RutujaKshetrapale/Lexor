using Microsoft.EntityFrameworkCore;
using NotificationService.Data;

namespace NotificationService.Tests;

public static class TestDbContextFactory
{
    public static NotificationDbContext Create(string dbName)
    {
        var options = new DbContextOptionsBuilder<NotificationDbContext>()
            .UseInMemoryDatabase(databaseName: dbName)
            .Options;

        var context = new NotificationDbContext(options);
        context.Database.EnsureCreated();
        return context;
    }
}
