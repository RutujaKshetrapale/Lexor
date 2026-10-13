using Microsoft.Extensions.Options;
using VehicleService.Configuration;
using VehicleService.Entities;
using VehicleService.Enums;

namespace VehicleService.Services;

public class VehicleVerificationPolicyService : IVehicleVerificationPolicyService
{
    private readonly List<VehicleDocumentType> _requiredDocuments;

    public VehicleVerificationPolicyService(IOptions<VehicleVerificationPolicySettings> policyOptions)
    {
        var settings = policyOptions.Value;
        _requiredDocuments = settings.RequiredDocuments
            .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
            .Select(Enum.Parse<VehicleDocumentType>)
            .ToList();
    }

    public List<VehicleDocumentType> GetRequiredDocumentTypes()
    {
        return _requiredDocuments;
    }

    public bool HasAllRequiredDocumentsVerified(List<VehicleDocument> documents)
    {
        var verifiedTypes = documents
            .Where(d => d.IsCurrent && d.VerificationStatus == DocumentVerificationStatus.VERIFIED)
            .Select(d => d.DocumentType)
            .ToHashSet();

        return _requiredDocuments.All(required => verifiedTypes.Contains(required));
    }

    public List<VehicleDocumentType> GetMissingDocuments(List<VehicleDocument> documents)
    {
        var presentTypes = documents
            .Where(d => d.IsCurrent)
            .Select(d => d.DocumentType)
            .ToHashSet();

        return _requiredDocuments
            .Where(required => !presentTypes.Contains(required))
            .ToList();
    }
}
