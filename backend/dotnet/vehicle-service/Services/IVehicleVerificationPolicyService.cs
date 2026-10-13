using VehicleService.Entities;
using VehicleService.Enums;

namespace VehicleService.Services;

public interface IVehicleVerificationPolicyService
{
    List<VehicleDocumentType> GetRequiredDocumentTypes();
    bool HasAllRequiredDocumentsVerified(List<VehicleDocument> documents);
    List<VehicleDocumentType> GetMissingDocuments(List<VehicleDocument> documents);
}
