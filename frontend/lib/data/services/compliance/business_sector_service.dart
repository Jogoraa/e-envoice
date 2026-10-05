import '../../../core/networking/api_client.dart';

class BusinessSectorDto {
  final String sectorCode;
  final String nameEnglish;
  final String nameAmharic;
  final bool mandatoryOfflineContinuity;
  final String? regulatoryDescription;

  BusinessSectorDto({
    required this.sectorCode,
    required this.nameEnglish,
    required this.nameAmharic,
    required this.mandatoryOfflineContinuity,
    this.regulatoryDescription,
  });

  factory BusinessSectorDto.fromJson(Map<String, dynamic> json) {
    return BusinessSectorDto(
      sectorCode: json['sectorCode']?.toString() ?? '',
      nameEnglish: json['nameEnglish']?.toString() ?? json['description']?.toString() ?? '',
      nameAmharic: json['nameAmharic']?.toString() ?? '',
      mandatoryOfflineContinuity: json['mandatoryOfflineContinuity'] == true || json['requiresOfflineBuffer'] == true,
      regulatoryDescription: json['regulatoryDescription']?.toString() ?? 'Directive No. 1142/2026 Annex 2',
    );
  }
}

class BusinessSectorService {
  final ApiClient apiClient;

  BusinessSectorService(this.apiClient);

  Future<List<BusinessSectorDto>> listSectors() async {
    final response = await apiClient.get('/api/v1/sectors');
    return (response.data as List<dynamic>? ?? [])
        .map((s) => BusinessSectorDto.fromJson(s as Map<String, dynamic>))
        .toList();
  }

  Future<List<BusinessSectorDto>> listMandatoryOfflineSectors() async {
    final response = await apiClient.get('/api/v1/sectors/mandatory-offline');
    return (response.data as List<dynamic>? ?? [])
        .map((s) => BusinessSectorDto.fromJson(s as Map<String, dynamic>))
        .toList();
  }

  Future<void> assignSector({required String tenantId, required String sectorCode}) async {
    await apiClient.post('/api/v1/sectors/assign', data: {
      'tenantId': tenantId,
      'sectorCode': sectorCode,
    });
  }
}
