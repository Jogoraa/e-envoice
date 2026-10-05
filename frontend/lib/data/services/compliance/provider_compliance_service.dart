import '../../../core/networking/api_client.dart';

class ProviderDashboardSummaryDto {
  final int activeTaxpayers;
  final double annualSalesVolume;
  final int currentTierLevel;
  final String currentTierName;
  final double requiredGuaranteeBondUsd;
  final String currentBondStatus; // VERIFIED, PENDING, EXPIRED
  final int requiredEngineers;
  final int currentEngineers;
  final String staffingStatus;
  final String? thresholdAlert;

  ProviderDashboardSummaryDto({
    required this.activeTaxpayers,
    required this.annualSalesVolume,
    required this.currentTierLevel,
    required this.currentTierName,
    required this.requiredGuaranteeBondUsd,
    required this.currentBondStatus,
    required this.requiredEngineers,
    required this.currentEngineers,
    required this.staffingStatus,
    this.thresholdAlert,
  });

  factory ProviderDashboardSummaryDto.fromJson(Map<String, dynamic> json) {
    return ProviderDashboardSummaryDto(
      activeTaxpayers: (json['activeTaxpayers'] as num?)?.toInt() ?? 0,
      annualSalesVolume: (json['annualSalesVolume'] as num?)?.toDouble() ?? 0.0,
      currentTierLevel: (json['currentTierLevel'] as num?)?.toInt() ?? 1,
      currentTierName: json['currentTierName']?.toString() ?? 'Level 1 Baseline',
      requiredGuaranteeBondUsd: (json['requiredGuaranteeBondUsd'] as num?)?.toDouble() ?? 50000.0,
      currentBondStatus: json['currentBondStatus']?.toString() ?? 'VERIFIED',
      requiredEngineers: (json['requiredEngineers'] as num?)?.toInt() ?? 6,
      currentEngineers: (json['currentEngineers'] as num?)?.toInt() ?? 6,
      staffingStatus: json['staffingStatus']?.toString() ?? 'COMPLIANT',
      thresholdAlert: json['thresholdAlert']?.toString(),
    );
  }
}

class ProviderComplianceTierDto {
  final int tierLevel;
  final String name;
  final int minTaxpayers;
  final int maxTaxpayers;
  final double minAnnualVolume;
  final double maxAnnualVolume;
  final double requiredGuaranteeUsd;
  final int requiredEngineers;

  ProviderComplianceTierDto({
    required this.tierLevel,
    required this.name,
    required this.minTaxpayers,
    required this.maxTaxpayers,
    required this.minAnnualVolume,
    required this.maxAnnualVolume,
    required this.requiredGuaranteeUsd,
    required this.requiredEngineers,
  });

  factory ProviderComplianceTierDto.fromJson(Map<String, dynamic> json) {
    return ProviderComplianceTierDto(
      tierLevel: (json['tierLevel'] as num?)?.toInt() ?? 1,
      name: json['name']?.toString() ?? 'Tier 1',
      minTaxpayers: (json['minTaxpayers'] as num?)?.toInt() ?? 0,
      maxTaxpayers: (json['maxTaxpayers'] as num?)?.toInt() ?? 500,
      minAnnualVolume: (json['minAnnualVolume'] as num?)?.toDouble() ?? 0.0,
      maxAnnualVolume: (json['maxAnnualVolume'] as num?)?.toDouble() ?? 50000000.0,
      requiredGuaranteeUsd: (json['requiredGuaranteeUsd'] as num?)?.toDouble() ?? 50000.0,
      requiredEngineers: (json['requiredEngineers'] as num?)?.toInt() ?? 6,
    );
  }
}

class ProviderExitPlanDto {
  final String id;
  final DateTime plannedCessationDate;
  final DateTime sixMonthsNoticeDate;
  final bool authorityNotified;
  final int totalTenants;
  final int migratedTenants;
  final int remainingTenants;
  final double completionPercentage;
  final String status; // DRAFT, SUBMITTED, APPROVED, IN_PROGRESS, FINALIZED
  final List<String> blockingTenants;

  ProviderExitPlanDto({
    required this.id,
    required this.plannedCessationDate,
    required this.sixMonthsNoticeDate,
    required this.authorityNotified,
    required this.totalTenants,
    required this.migratedTenants,
    required this.remainingTenants,
    required this.completionPercentage,
    required this.status,
    required this.blockingTenants,
  });

  factory ProviderExitPlanDto.fromJson(Map<String, dynamic> json) {
    final total = (json['totalTenants'] as num?)?.toInt() ?? 0;
    final migrated = (json['migratedTenants'] as num?)?.toInt() ?? 0;
    final remaining = (json['remainingTenants'] as num?)?.toInt() ?? (total - migrated);
    final pct = total > 0 ? (migrated / total) * 100 : 100.0;

    return ProviderExitPlanDto(
      id: json['id']?.toString() ?? '',
      plannedCessationDate: json['plannedCessationDate'] != null
          ? DateTime.tryParse(json['plannedCessationDate'].toString()) ?? DateTime.now().add(const Duration(days: 180))
          : DateTime.now().add(const Duration(days: 180)),
      sixMonthsNoticeDate: json['sixMonthsNoticeDate'] != null
          ? DateTime.tryParse(json['sixMonthsNoticeDate'].toString()) ?? DateTime.now()
          : DateTime.now(),
      authorityNotified: json['authorityNotified'] == true,
      totalTenants: total,
      migratedTenants: migrated,
      remainingTenants: remaining,
      completionPercentage: (json['completionPercentage'] as num?)?.toDouble() ?? pct,
      status: json['status']?.toString() ?? 'DRAFT',
      blockingTenants: (json['blockingTenants'] as List<dynamic>? ?? []).map((t) => t.toString()).toList(),
    );
  }

  bool get canFinalize => remainingTenants == 0 && status == 'IN_PROGRESS';
}

class ProviderComplianceService {
  final ApiClient apiClient;

  ProviderComplianceService(this.apiClient);

  Future<ProviderDashboardSummaryDto> getDashboardSummary() async {
    final response = await apiClient.get('/api/v1/master/provider-tiers/dashboard');
    return ProviderDashboardSummaryDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<List<ProviderComplianceTierDto>> getAllTiers() async {
    final response = await apiClient.get('/api/v1/master/provider-tiers');
    return (response.data as List<dynamic>? ?? [])
        .map((t) => ProviderComplianceTierDto.fromJson(t as Map<String, dynamic>))
        .toList();
  }

  Future<List<ProviderComplianceTierDto>> listTiers() => getAllTiers();

  Future<ProviderExitPlanDto?> getLatestExitPlan() async {
    try {
      final response = await apiClient.get('/api/v1/master/provider-exit/plans/latest');
      if (response.statusCode == 404 || response.data == null) return null;
      return ProviderExitPlanDto.fromJson(response.data as Map<String, dynamic>);
    } catch (_) {
      return null;
    }
  }

  Future<ProviderExitPlanDto?> getExitPlan(String id) => getLatestExitPlan();

  Future<ProviderExitPlanDto> createExitPlan(Map<String, dynamic> payload) async {
    final response = await apiClient.post('/api/v1/master/provider-exit/plans', data: payload);
    return ProviderExitPlanDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<ProviderExitPlanDto> confirmCessation(String planId, Map<String, dynamic> payload) async {
    final response = await apiClient.post('/api/v1/master/provider-exit/plans/$planId/confirm-cessation', data: payload);
    return ProviderExitPlanDto.fromJson(response.data as Map<String, dynamic>);
  }

  Future<ProviderExitPlanDto> finalizeExit(String planId) =>
      confirmCessation(planId, {'confirmed': true});
}
