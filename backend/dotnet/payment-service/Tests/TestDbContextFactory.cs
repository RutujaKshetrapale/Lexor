using Microsoft.EntityFrameworkCore;
using PaymentService.Data;

namespace PaymentService.Tests;

public static class TestDbContextFactory
{
    public static PaymentDbContext Create(string dbName)
    {
        var options = new DbContextOptionsBuilder<PaymentDbContext>()
            .UseInMemoryDatabase(databaseName: dbName)
            .Options;

        var context = new PaymentDbContext(options);
        context.Database.EnsureCreated();
        return context;
    }
}
