/// Customer Master Domain Model for Ethiopian Electronic Invoicing Platform
library;

class CustomerModel {
  final String id;
  final String tenantId;
  final String? branchId;
  final String? tin;
  final String? vatNumber;
  final String legalName;
  final String? tradeName;
  final String? phone;
  final String? email;
  final String country;
  final String? region;
  final String? city;
  final String? zone;
  final String? woreda;
  final String? kebele;
  final String? houseNumber;
  final String buyerIdType; // 'TIN', 'KID', 'NATIONAL_ID', 'PASSPORT'
  final String? buyerIdNumber;
  final bool isVatRegistered;
  final String status; // 'ACTIVE', 'INACTIVE'
  final DateTime createdAt;
  final DateTime? updatedAt;
  final String syncStatus; // 'synced', 'pending', 'syncError'

  const CustomerModel({
    required this.id,
    required this.tenantId,
    this.branchId,
    this.tin,
    this.vatNumber,
    required this.legalName,
    this.tradeName,
    this.phone,
    this.email,
    this.country = 'ET',
    this.region,
    this.city,
    this.zone,
    this.woreda,
    this.kebele,
    this.houseNumber,
    this.buyerIdType = 'TIN',
    this.buyerIdNumber,
    this.isVatRegistered = false,
    this.status = 'ACTIVE',
    required this.createdAt,
    this.updatedAt,
    this.syncStatus = 'synced',
  });

  bool get hasValidTin => tin != null && RegExp(r'^\d{10}$').hasMatch(tin!.trim());

  String get formattedAddress {
    final parts = <String>[];
    if (city != null && city!.isNotEmpty) parts.add(city!);
    if (zone != null && zone!.isNotEmpty) parts.add(zone!);
    if (woreda != null && woreda!.isNotEmpty) parts.add('Woreda $woreda');
    if (kebele != null && kebele!.isNotEmpty) parts.add('Kebele $kebele');
    if (region != null && region!.isNotEmpty && (city == null || !city!.contains(region!))) {
      parts.add('Region $region');
    }
    return parts.isEmpty ? 'Addis Ababa, Ethiopia' : parts.join(', ');
  }

  CustomerModel copyWith({
    String? id,
    String? tenantId,
    String? branchId,
    String? tin,
    String? vatNumber,
    String? legalName,
    String? tradeName,
    String? phone,
    String? email,
    String? country,
    String? region,
    String? city,
    String? zone,
    String? woreda,
    String? kebele,
    String? houseNumber,
    String? buyerIdType,
    String? buyerIdNumber,
    bool? isVatRegistered,
    String? status,
    DateTime? createdAt,
    DateTime? updatedAt,
    String? syncStatus,
  }) {
    return CustomerModel(
      id: id ?? this.id,
      tenantId: tenantId ?? this.tenantId,
      branchId: branchId ?? this.branchId,
      tin: tin ?? this.tin,
      vatNumber: vatNumber ?? this.vatNumber,
      legalName: legalName ?? this.legalName,
      tradeName: tradeName ?? this.tradeName,
      phone: phone ?? this.phone,
      email: email ?? this.email,
      country: country ?? this.country,
      region: region ?? this.region,
      city: city ?? this.city,
      zone: zone ?? this.zone,
      woreda: woreda ?? this.woreda,
      kebele: kebele ?? this.kebele,
      houseNumber: houseNumber ?? this.houseNumber,
      buyerIdType: buyerIdType ?? this.buyerIdType,
      buyerIdNumber: buyerIdNumber ?? this.buyerIdNumber,
      isVatRegistered: isVatRegistered ?? this.isVatRegistered,
      status: status ?? this.status,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      syncStatus: syncStatus ?? this.syncStatus,
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'tenantId': tenantId,
        if (branchId != null) 'branchId': branchId,
        if (tin != null) 'tin': tin,
        if (vatNumber != null) 'vatNumber': vatNumber,
        'legalName': legalName,
        if (tradeName != null) 'tradeName': tradeName,
        if (phone != null) 'phone': phone,
        if (email != null) 'email': email,
        'country': country,
        if (region != null) 'region': region,
        if (city != null) 'city': city,
        if (zone != null) 'zone': zone,
        if (woreda != null) 'woreda': woreda,
        if (kebele != null) 'kebele': kebele,
        if (houseNumber != null) 'houseNumber': houseNumber,
        'buyerIdType': buyerIdType,
        if (buyerIdNumber != null) 'buyerIdNumber': buyerIdNumber,
        'isVatRegistered': isVatRegistered,
        'status': status,
        'createdAt': createdAt.toIso8601String(),
        if (updatedAt != null) 'updatedAt': updatedAt!.toIso8601String(),
      };

  factory CustomerModel.fromJson(Map<String, dynamic> json) => CustomerModel(
        id: json['id']?.toString() ?? '',
        tenantId: json['tenantId']?.toString() ?? '',
        branchId: json['branchId']?.toString(),
        tin: json['tin']?.toString(),
        vatNumber: json['vatNumber']?.toString(),
        legalName: json['legalName']?.toString() ?? '',
        tradeName: json['tradeName']?.toString(),
        phone: json['phone']?.toString(),
        email: json['email']?.toString(),
        country: json['country']?.toString() ?? 'ET',
        region: json['region']?.toString(),
        city: json['city']?.toString(),
        zone: json['zone']?.toString(),
        woreda: json['woreda']?.toString(),
        kebele: json['kebele']?.toString(),
        houseNumber: json['houseNumber']?.toString(),
        buyerIdType: json['buyerIdType']?.toString() ?? 'TIN',
        buyerIdNumber: json['buyerIdNumber']?.toString(),
        isVatRegistered: json['isVatRegistered'] == true,
        status: json['status']?.toString() ?? 'ACTIVE',
        createdAt: json['createdAt'] != null
            ? DateTime.tryParse(json['createdAt'].toString()) ?? DateTime.now()
            : DateTime.now(),
        updatedAt: json['updatedAt'] != null
            ? DateTime.tryParse(json['updatedAt'].toString())
            : null,
      );
}
