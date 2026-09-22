import 'dart:convert';
import 'dart:io';
import 'package:flutter/foundation.dart';

/// Represents a locally cached authoritative official receipt.
class CachedOfficialReceipt {
  final String tenantId;
  final String branchId;
  final String invoiceId;
  final String? documentNumber;
  final String? irn;
  final String htmlContent;
  final Map<String, dynamic>? jsonViewModel;
  final DateTime cachedAt;

  const CachedOfficialReceipt({
    required this.tenantId,
    required this.branchId,
    required this.invoiceId,
    this.documentNumber,
    this.irn,
    required this.htmlContent,
    this.jsonViewModel,
    required this.cachedAt,
  });

  Map<String, dynamic> toJson() => {
        'tenantId': tenantId,
        'branchId': branchId,
        'invoiceId': invoiceId,
        'documentNumber': documentNumber,
        'irn': irn,
        'htmlContent': htmlContent,
        'jsonViewModel': jsonViewModel,
        'cachedAt': cachedAt.toIso8601String(),
      };

  factory CachedOfficialReceipt.fromJson(Map<String, dynamic> map) =>
      CachedOfficialReceipt(
        tenantId: map['tenantId'] as String,
        branchId: map['branchId'] as String,
        invoiceId: map['invoiceId'] as String,
        documentNumber: map['documentNumber'] as String?,
        irn: map['irn'] as String?,
        htmlContent: map['htmlContent'] as String,
        jsonViewModel: map['jsonViewModel'] as Map<String, dynamic>?,
        cachedAt: DateTime.parse(map['cachedAt'] as String),
      );
}

/// Tenant and branch scoped caching service for official tax invoices and receipts.
///
/// Ensures official receipts are cached after authoritative retrieval from the
/// backend and can be viewed or printed even during poor or offline conditions.
///
/// Enforces strict tenant isolation: receipts cached for Tenant A can NEVER be
/// retrieved by or shown to Tenant B.
class ReceiptCacheService {
  final Map<String, CachedOfficialReceipt> _memoryCache = {};
  Directory? _cacheDir;
  bool _initialized = false;

  ReceiptCacheService({Directory? customCacheDir}) {
    if (customCacheDir != null) {
      _cacheDir = customCacheDir;
      _initialized = true;
    }
  }

  Future<void> _ensureInitialized() async {
    if (_initialized) return;
    try {
      if (!kIsWeb) {
        _cacheDir = Directory('${Directory.systemTemp.path}/ut_einvoice_receipt_cache');
        if (!await _cacheDir!.exists()) {
          await _cacheDir!.create(recursive: true);
        }
      }
    } catch (e) {
      debugPrint('[ReceiptCacheService] Disk cache initialization error: $e');
    } finally {
      _initialized = true;
    }
  }

  String _buildCacheKey({
    required String tenantId,
    required String branchId,
    required String invoiceId,
  }) {
    return 'receipt_${tenantId}_${branchId}_$invoiceId';
  }

  File? _getFileForKey(String key) {
    if (_cacheDir == null) return null;
    return File('${_cacheDir!.path}/$key.json');
  }

  /// Retrieves cached HTML receipt if available and scoped to tenant & branch.
  Future<String?> getCachedReceiptHtml({
    required String tenantId,
    required String branchId,
    required String invoiceId,
  }) async {
    final cached = await getCachedReceipt(
      tenantId: tenantId,
      branchId: branchId,
      invoiceId: invoiceId,
    );
    return cached?.htmlContent;
  }

  /// Retrieves cached receipt metadata and content.
  Future<CachedOfficialReceipt?> getCachedReceipt({
    required String tenantId,
    required String branchId,
    required String invoiceId,
  }) async {
    final key = _buildCacheKey(
      tenantId: tenantId,
      branchId: branchId,
      invoiceId: invoiceId,
    );

    // 1. Check memory cache first
    if (_memoryCache.containsKey(key)) {
      final item = _memoryCache[key]!;
      if (item.tenantId == tenantId && item.branchId == branchId) {
        return item;
      }
    }

    // 2. Check disk cache
    await _ensureInitialized();
    try {
      final file = _getFileForKey(key);
      if (file != null && await file.exists()) {
        final jsonStr = await file.readAsString();
        final map = jsonDecode(jsonStr) as Map<String, dynamic>;
        final receipt = CachedOfficialReceipt.fromJson(map);

        // Strict isolation validation
        if (receipt.tenantId == tenantId && receipt.branchId == branchId) {
          _memoryCache[key] = receipt;
          return receipt;
        }
      }
    } catch (e) {
      debugPrint('[ReceiptCacheService] Disk read failed for $key: $e');
    }

    return null;
  }

  /// Checks whether a valid cached receipt exists.
  Future<bool> hasCachedReceipt({
    required String tenantId,
    required String branchId,
    required String invoiceId,
  }) async {
    final receipt = await getCachedReceipt(
      tenantId: tenantId,
      branchId: branchId,
      invoiceId: invoiceId,
    );
    return receipt != null && receipt.htmlContent.isNotEmpty;
  }

  /// Saves official receipt HTML and metadata into scoped cache.
  Future<void> saveReceiptHtml({
    required String tenantId,
    required String branchId,
    required String invoiceId,
    required String htmlContent,
    String? documentNumber,
    String? irn,
    Map<String, dynamic>? jsonViewModel,
  }) async {
    if (htmlContent.isEmpty) return;

    final receipt = CachedOfficialReceipt(
      tenantId: tenantId,
      branchId: branchId,
      invoiceId: invoiceId,
      documentNumber: documentNumber,
      irn: irn,
      htmlContent: htmlContent,
      jsonViewModel: jsonViewModel,
      cachedAt: DateTime.now(),
    );

    final key = _buildCacheKey(
      tenantId: tenantId,
      branchId: branchId,
      invoiceId: invoiceId,
    );

    _memoryCache[key] = receipt;

    await _ensureInitialized();
    try {
      final file = _getFileForKey(key);
      if (file != null) {
        await file.writeAsString(jsonEncode(receipt.toJson()));
      }
    } catch (e) {
      debugPrint('[ReceiptCacheService] Disk write failed for $key: $e');
    }
  }

  /// Purges all cached receipts for a specific tenant upon logout or tenant switch.
  Future<void> clearTenantCache(String tenantId) async {
    _memoryCache.removeWhere((key, val) => val.tenantId == tenantId);

    await _ensureInitialized();
    try {
      if (_cacheDir != null && await _cacheDir!.exists()) {
        final prefix = 'receipt_${tenantId}_';
        final files = _cacheDir!.listSync();
        for (final entity in files) {
          if (entity is File && entity.path.contains(prefix)) {
            await entity.delete();
          }
        }
      }
    } catch (e) {
      debugPrint('[ReceiptCacheService] Error clearing tenant cache for $tenantId: $e');
    }
  }

  /// Purges all cached items in memory and on disk.
  Future<void> clearAll() async {
    _memoryCache.clear();
    await _ensureInitialized();
    try {
      if (_cacheDir != null && await _cacheDir!.exists()) {
        final files = _cacheDir!.listSync();
        for (final entity in files) {
          if (entity is File) {
            await entity.delete();
          }
        }
      }
    } catch (e) {
      debugPrint('[ReceiptCacheService] Error clearing cache: $e');
    }
  }
}
