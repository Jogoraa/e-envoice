import 'dart:async';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:drift/drift.dart';
import 'package:flutter/foundation.dart';
import 'package:uuid/uuid.dart';
import '../../core/connectivity/connectivity_service.dart';
import '../../core/errors/app_error.dart';
import '../../core/errors/error_category.dart';
import '../../core/networking/api_client.dart';
import '../../core/networking/network_profile.dart';
import '../../core/networking/network_resilience_manager.dart';
import '../../core/storage/receipt_cache_service.dart';
import '../../domain/invoice/models/invoice_models.dart';
import '../local/database/app_database.dart';

class InvoiceRepositoryImpl {
  final ApiClient apiClient;
  final AppDatabase db;
  final ConnectivityService connectivity;
  final ReceiptCacheService receiptCache;
  final NetworkResilienceManager resilienceManager;
  static const Uuid _uuid = Uuid();

  ApiClient get _apiClient => apiClient;
  AppDatabase get _db => db;
  ReceiptCacheService get _receiptCache => receiptCache;

  InvoiceRepositoryImpl({
    required this.apiClient,
    required this.db,
    required this.connectivity,
    ReceiptCacheService? receiptCache,
    NetworkResilienceManager? resilienceManager,
  }) : receiptCache = receiptCache ?? ReceiptCacheService(),
       resilienceManager = resilienceManager ?? apiClient.resilienceManager;

  Future<List<InvoiceModel>> listInvoices({
    required String tenantId,
    required String branchId,
    String? status,
    int page = 0,
    int size = 20,
  }) async {
    // 1. Authoritative network attempt with resilience
    try {
      final queryParams = {
        'page': page,
        'size': size,
        if (status != null && status.isNotEmpty) 'status': status,
      };

      final response = await _apiClient.get(
        '/api/v1/invoices',
        queryParameters: queryParams,
        profile: NetworkProfiles.standard,
        dedupeKey: 'list_invoices:$tenantId:$branchId:$page:$size:$status',
      );

      if (response.statusCode == 200 && response.data is Map<String, dynamic>) {
        final content = response.data['content'];
        if (content is List) {
          return content
              .map(
                (item) => InvoiceModel.fromJson(item as Map<String, dynamic>),
              )
              .toList();
        }
      }
    } catch (e) {
      debugPrint(
        '[InvoiceRepository] Remote listInvoices failed ($e). Falling back to local storage.',
      );
    }

    // 2. Offline / Cached fallback: strictly scoped by tenantId & branchId
    final localRecords = await _db.getScopedInvoices(
      tenantId: tenantId,
      branchId: branchId,
      limit: size,
      offset: page * size,
      status: status,
    );

    return localRecords.map((rec) {
      return InvoiceModel(
        id: rec.serverId ?? rec.localId,
        tenantId: rec.tenantId,
        branchId: rec.branchId,
        documentNumber: rec.documentNumber,
        invoiceCounter: rec.invoiceCounter?.toInt(),
        invoiceDate: rec.invoiceDate,
        transactionType: rec.transactionType,
        paymentMode: rec.paymentMode,
        status: rec.syncStatus,
        preTaxTotal: rec.preTaxTotal,
        taxTotal: rec.taxTotal,
        grandTotal: rec.grandTotal,
        currency: rec.currency,
        irn: rec.irn,
        rrn: rec.rrn,
        signedQr: rec.signedQr,
        reprintCount: rec.reprintCount,
        buyer: rec.buyerTin != null
            ? BuyerModel(legalName: rec.buyerName ?? '', tin: rec.buyerTin!)
            : null,
      );
    }).toList();
  }

  Future<InvoiceSummary> getInvoiceSummary({
    required String tenantId,
    required String branchId,
  }) async {
    // 1. Authoritative network attempt
    try {
      final response = await _apiClient.get(
        '/api/v1/invoices/summary',
        profile: NetworkProfiles.standard,
        dedupeKey: 'invoice_summary:$tenantId',
      );
      if (response.statusCode == 200 && response.data is Map<String, dynamic>) {
        return InvoiceSummary.fromJson(response.data as Map<String, dynamic>);
      }
    } catch (e) {
      debugPrint('[InvoiceRepository] Remote getInvoiceSummary failed ($e). Calculating from local database.');
    }

    // 2. Fallback: calculate strictly scoped by tenantId & branchId from local SQLite
    final localRecords = await _db.getScopedInvoices(
      tenantId: tenantId,
      branchId: branchId,
      limit: 1000,
    );

    final now = DateTime.now();
    final startOfToday = DateTime(now.year, now.month, now.day);

    int todayCount = 0;
    double todayGross = 0.0;
    double todayVat = 0.0;
    int registeredCount = 0;
    int pendingCount = 0;
    double totalGross = 0.0;
    double totalVat = 0.0;

    for (final rec in localRecords) {
      totalGross += rec.grandTotal;
      totalVat += rec.taxTotal;

      if (rec.createdAt.isAfter(startOfToday)) {
        todayCount++;
        todayGross += rec.grandTotal;
        todayVat += rec.taxTotal;
      }

      if (rec.syncStatus == 'synced') {
        registeredCount++;
      } else {
        pendingCount++;
      }
    }

    return InvoiceSummary(
      todayInvoicesCount: todayCount,
      todayGrossSales: todayGross,
      todayVatAmount: todayVat,
      totalInvoicesCount: localRecords.length,
      totalGrossSales: totalGross,
      totalVatAmount: totalVat,
      registeredCount: registeredCount,
      pendingCount: pendingCount,
    );
  }

  Future<InvoiceModel> submitOrQueueInvoice({
    required String tenantId,
    required String branchId,
    required String transactionType,
    required String paymentMode,
    required BuyerModel? buyer,
    required List<InvoiceLineDraft> items,
    String? existingIdempotencyKey,
    bool saveCustomerToMaster = false,
    String? customDocumentNumber,
  }) async {
    final localId = _uuid.v4();
    final idempotencyKey = existingIdempotencyKey ?? _uuid.v4();
    final totals = ClientTaxEstimator.estimateTotals(items);
    final now = DateTime.now();

    // Deterministic client-side temporary draft document number or manual paper receipt number
    final localDocNumber = (customDocumentNumber != null && customDocumentNumber.trim().isNotEmpty)
        ? customDocumentNumber.trim()
        : 'DRAFT-$branchId-${now.millisecondsSinceEpoch.toString().substring(7)}';

    final requestPayload = {
      'transactionType': transactionType,
      'paymentMode': paymentMode,
      'paymentTerm': 'IMMEDIATE',
      'buyer': buyer?.toJson(),
      'saveCustomerToMaster': saveCustomerToMaster,
      'items': items
          .map(
            (i) => {
              'itemCode': i.itemCode,
              'productDescription': i.productDescription,
              'natureOfSupplies': i.itemType == 'SERVICE'
                  ? 'services'
                  : 'goods',
              'itemType': i.itemType,
              'catalogItemId': i.catalogItemId,
              'unit': i.unit,
              'quantity': i.quantity,
              'unitPrice': i.unitPrice,
              'discount': i.discount,
              'taxCode': i.taxCode,
            },
          )
          .toList(),
      'customDocumentNumber': localDocNumber,
    };

    final payloadJson = jsonEncode(requestPayload);

    // 1. Try online submission with idempotency protection if connectivity is available
    if (!connectivity.isOffline) {
      try {
        final response = await _apiClient.post(
          '/api/v1/invoices',
          data: requestPayload,
          idempotencyKey: idempotencyKey,
          profile: NetworkProfiles.standard,
          dedupeKey: 'submit_invoice:$idempotencyKey',
        );

      if (response.statusCode == 201 ||
          response.statusCode == 200 ||
          response.statusCode == 202) {
        final serverInvoice = InvoiceModel.fromJson(response.data);

        // Save server-confirmed record locally
        await _db.insertScopedInvoice(
          invoice: LocalInvoicesCompanion.insert(
            localId: localId,
            tenantId: tenantId,
            branchId: branchId,
            serverId: Value(serverInvoice.id),
            documentNumber: serverInvoice.documentNumber,
            invoiceCounter: Value(
              serverInvoice.invoiceCounter != null
                  ? BigInt.from(serverInvoice.invoiceCounter!)
                  : null,
            ),
            invoiceDate: serverInvoice.invoiceDate,
            transactionType: serverInvoice.transactionType,
            paymentMode: serverInvoice.paymentMode,
            buyerTin: Value(serverInvoice.buyer?.tin),
            buyerName: Value(serverInvoice.buyer?.legalName),
            preTaxTotal: serverInvoice.preTaxTotal,
            taxTotal: serverInvoice.taxTotal,
            grandTotal: serverInvoice.grandTotal,
            syncStatus: 'synced',
            irn: Value(serverInvoice.irn),
            rrn: Value(serverInvoice.rrn),
            signedQr: Value(serverInvoice.signedQr),
            rawPayload: payloadJson,
            createdAt: now,
            syncedAt: Value(now),
          ),
          lines: items.asMap().entries.map((e) {
            final idx = e.key;
            final item = e.value;
            final linePreTax = (item.quantity * item.unitPrice) - item.discount;
            final lineTax = item.taxCode == 'VAT15' ? linePreTax * 0.15 : 0.0;
            return LocalInvoiceLinesCompanion.insert(
              lineId: _uuid.v4(),
              invoiceLocalId: localId,
              tenantId: tenantId,
              branchId: branchId,
              lineNumber: idx + 1,
              itemCode: item.itemCode,
              productDescription: item.productDescription,
              quantity: item.quantity,
              unitPrice: item.unitPrice,
              discount: Value(item.discount),
              taxCode: Value(item.taxCode),
              taxAmount: lineTax,
              totalLineAmount: linePreTax + lineTax,
            );
          }).toList(),
        );

        return serverInvoice;
      }
    } on AppError catch (appErr) {
      // Handle Conflict / Duplicate: Attempt deterministic server reconciliation
      if (appErr.status == 409 ||
          appErr.code == 'DUPLICATE_RESOURCE' ||
          appErr.code == 'IDEMPOTENCY_PAYLOAD_MISMATCH') {
        debugPrint(
          '[InvoiceRepository] Conflict/Duplicate detected (${appErr.code}). Reconciling server state...',
        );
        try {
          final serverList = await listInvoices(
            tenantId: tenantId,
            branchId: branchId,
            size: 10,
          );
          final matched = serverList.firstWhere(
            (inv) =>
                inv.documentNumber == localDocNumber ||
                (inv.grandTotal == totals.grandTotal &&
                    inv.invoiceDate.difference(now).inMinutes.abs() < 5),
            orElse: () => throw appErr,
          );

          await _db.insertScopedInvoice(
            invoice: LocalInvoicesCompanion.insert(
              localId: localId,
              tenantId: tenantId,
              branchId: branchId,
              serverId: Value(matched.id),
              documentNumber: matched.documentNumber,
              invoiceDate: matched.invoiceDate,
              transactionType: matched.transactionType,
              paymentMode: matched.paymentMode,
              buyerTin: Value(matched.buyer?.tin),
              buyerName: Value(matched.buyer?.legalName),
              preTaxTotal: matched.preTaxTotal,
              taxTotal: matched.taxTotal,
              grandTotal: matched.grandTotal,
              syncStatus: 'synced',
              irn: Value(matched.irn),
              rrn: Value(matched.rrn),
              signedQr: Value(matched.signedQr),
              rawPayload: payloadJson,
              createdAt: now,
              syncedAt: Value(now),
            ),
            lines: items.asMap().entries.map((e) {
              final idx = e.key;
              final item = e.value;
              final linePreTax =
                  (item.quantity * item.unitPrice) - item.discount;
              final lineTax =
                  item.taxCode == 'VAT15' ? linePreTax * 0.15 : 0.0;
              return LocalInvoiceLinesCompanion.insert(
                lineId: _uuid.v4(),
                invoiceLocalId: localId,
                tenantId: tenantId,
                branchId: branchId,
                lineNumber: idx + 1,
                itemCode: item.itemCode,
                productDescription: item.productDescription,
                quantity: item.quantity,
                unitPrice: item.unitPrice,
                discount: Value(item.discount),
                taxCode: Value(item.taxCode),
                taxAmount: lineTax,
                totalLineAmount: linePreTax + lineTax,
              );
            }).toList(),
          );
          return matched;
        } catch (_) {
          rethrow;
        }
      }

      // Permanent errors (validation, auth, permissions) must NOT be queued in durable outbox
      if (appErr.category == ErrorCategory.validationError ||
          appErr.status == 400 ||
          appErr.status == 401 ||
          appErr.status == 403 ||
          appErr.status == 422) {
        rethrow;
      }

      debugPrint(
        '[InvoiceRepository] Transient transport error ($appErr). Queuing in durable outbox.',
      );
    } catch (e) {
      debugPrint(
        '[InvoiceRepository] Direct submission failed ($e). Queuing in durable outbox.',
      );
    }
  }

    // 2. Offline / Outbox fallback: Atomic transaction of LocalInvoice + OutboxOperation
    final offlineSeq = BigInt.from(now.millisecondsSinceEpoch);

    await _db.insertScopedInvoice(
      invoice: LocalInvoicesCompanion.insert(
        localId: localId,
        tenantId: tenantId,
        branchId: branchId,
        documentNumber: localDocNumber,
        invoiceDate: now,
        transactionType: transactionType,
        paymentMode: paymentMode,
        buyerTin: Value(buyer?.tin),
        buyerName: Value(buyer?.legalName),
        preTaxTotal: totals.preTaxTotal,
        taxTotal: totals.taxTotal,
        grandTotal: totals.grandTotal,
        syncStatus: 'offlineDraft',
        rawPayload: payloadJson,
        createdAt: now,
      ),
      lines: items.asMap().entries.map((e) {
        final idx = e.key;
        final item = e.value;
        final linePreTax = (item.quantity * item.unitPrice) - item.discount;
        final lineTax = item.taxCode == 'VAT15' ? linePreTax * 0.15 : 0.0;
        return LocalInvoiceLinesCompanion.insert(
          lineId: _uuid.v4(),
          invoiceLocalId: localId,
          tenantId: tenantId,
          branchId: branchId,
          lineNumber: idx + 1,
          itemCode: item.itemCode,
          productDescription: item.productDescription,
          quantity: item.quantity,
          unitPrice: item.unitPrice,
          discount: Value(item.discount),
          taxCode: Value(item.taxCode),
          taxAmount: lineTax,
          totalLineAmount: linePreTax + lineTax,
        );
      }).toList(),
      outboxEntry: OutboxOperationsCompanion.insert(
        operationId: localId,
        idempotencyKey:
            idempotencyKey, // Reusable, never regenerated on timeout
        tenantId: tenantId,
        branchId: branchId,
        operationType: 'CREATE_INVOICE',
        endpoint: '/api/v1/invoices',
        payloadJson: payloadJson,
        offlineSeqNo: Value(offlineSeq),
        bufferedAt: now,
        createdAt: now,
        syncState: 'pending',
      ),
    );

    return InvoiceModel(
      id: localId,
      tenantId: tenantId,
      branchId: branchId,
      documentNumber: localDocNumber,
      invoiceDate: now,
      transactionType: transactionType,
      paymentMode: paymentMode,
      status: 'offlineDraft',
      preTaxTotal: totals.preTaxTotal,
      taxTotal: totals.taxTotal,
      grandTotal: totals.grandTotal,
      buyer: buyer,
    );
  }

  Future<InvoiceModel?> getInvoiceById(String id) async {
    // 1. Check local DB to resolve serverId if id is localId
    final initialRecord =
        await (_db.select(
              _db.localInvoices,
            )..where((tbl) => tbl.localId.equals(id) | tbl.serverId.equals(id)))
            .getSingleOrNull();

    final targetServerId = initialRecord?.serverId ?? id;

    // 2. Authoritative backend lookup
    try {
      final response = await _apiClient.get(
        '/api/v1/invoices/$targetServerId',
        profile: NetworkProfiles.standard,
        dedupeKey: 'get_invoice:$targetServerId',
      );
      if (response.statusCode == 200 && response.data is Map<String, dynamic>) {
        final serverInvoice = InvoiceModel.fromJson(response.data as Map<String, dynamic>);
        if (initialRecord != null) {
          await (_db.update(_db.localInvoices)
                ..where((tbl) => tbl.localId.equals(initialRecord.localId)))
              .write(
                LocalInvoicesCompanion(
                  serverId: Value(serverInvoice.id),
                  syncStatus: const Value('synced'),
                  irn: Value(serverInvoice.irn),
                  rrn: Value(serverInvoice.rrn),
                  signedQr: Value(serverInvoice.signedQr),
                ),
              );
        }
        return serverInvoice;
      }
    } catch (e) {
      debugPrint(
        '[InvoiceRepository] Server lookup for $targetServerId failed: $e. Using local DB fallback.',
      );
      if (initialRecord != null && initialRecord.irn != null && initialRecord.irn!.isNotEmpty) {
        try {
          final response = await _apiClient.get(
            '/api/v1/invoices/by-irn/${initialRecord.irn}',
            profile: NetworkProfiles.standard,
            dedupeKey: 'get_invoice_by_irn:${initialRecord.irn}',
          );
          if (response.statusCode == 200 && response.data is Map<String, dynamic>) {
            return InvoiceModel.fromJson(response.data as Map<String, dynamic>);
          }
        } catch (_) {}
      }
    }

    // 3. Offline / Cached fallback: strictly scoped to local DB by ID (localId or serverId)
    final record =
        await (_db.select(
              _db.localInvoices,
            )..where((tbl) => tbl.localId.equals(id) | tbl.serverId.equals(id)))
            .getSingleOrNull();

    if (record == null) return null;

    final lines =
        await (_db.select(_db.localInvoiceLines)
              ..where((tbl) => tbl.invoiceLocalId.equals(record.localId))
              ..orderBy([
                (tbl) => OrderingTerm(
                  expression: tbl.lineNumber,
                  mode: OrderingMode.asc,
                ),
              ]))
            .get();

    return InvoiceModel(
      id: record.serverId ?? record.localId,
      tenantId: record.tenantId,
      branchId: record.branchId,
      documentNumber: record.documentNumber,
      invoiceCounter: record.invoiceCounter?.toInt(),
      invoiceDate: record.invoiceDate,
      transactionType: record.transactionType,
      paymentMode: record.paymentMode,
      status: record.syncStatus,
      preTaxTotal: record.preTaxTotal,
      taxTotal: record.taxTotal,
      grandTotal: record.grandTotal,
      currency: record.currency,
      irn: record.irn,
      rrn: record.rrn,
      signedQr: record.signedQr,
      reprintCount: record.reprintCount,
      buyer: record.buyerTin != null
          ? BuyerModel(legalName: record.buyerName ?? '', tin: record.buyerTin!)
          : null,
      lines: lines
          .map(
            (l) => InvoiceLineModel(
              lineNumber: l.lineNumber,
              itemCode: l.itemCode,
              productDescription: l.productDescription,
              quantity: l.quantity,
              unit: l.unit,
              unitPrice: l.unitPrice,
              discount: l.discount,
              preTaxValue: (l.quantity * l.unitPrice) - l.discount,
              taxCode: l.taxCode,
              taxAmount: l.taxAmount,
              totalLineAmount: l.totalLineAmount,
            ),
          )
          .toList(),
    );
  }

  /// Official Receipt HTML with offline caching and stale-while-revalidate.
  ///
  /// Flow:
  /// 1. Check local cached official receipt.
  /// 2. If cached: return immediately and refresh from server in background.
  /// 3. If not cached:
  ///    - Check if invoice is an offline draft (no server registration yet).
  ///    - Fetch authoritative document from backend with receipt profile,
  ///      resilient retry, and progress reporting.
  ///    - Cache authoritative receipt locally.
  ///    - Return HTML content.
  Future<String> getInvoiceReceiptHtml(
    String id, {
    String? tenantId,
    String? branchId,
    void Function(ResilienceProgress progress)? onProgress,
  }) async {
    final effectiveTenantId =
        tenantId ?? _apiClient.activeTenantId ?? 'default_tenant';
    final effectiveBranchId =
        branchId ?? _apiClient.activeBranchId ?? 'default_branch';

    // 1. Check local cache first
    final cachedHtml = await _receiptCache.getCachedReceiptHtml(
      tenantId: effectiveTenantId,
      branchId: effectiveBranchId,
      invoiceId: id,
    );

    if (cachedHtml != null && cachedHtml.isNotEmpty) {
      debugPrint(
        '[InvoiceRepository] Returning cached official receipt for $id (Stale-While-Revalidate)',
      );
      // Background stale-while-revalidate refresh (never block the user)
      unawaited(
        _refreshReceiptInBackground(
          id: id,
          tenantId: effectiveTenantId,
          branchId: effectiveBranchId,
        ),
      );
      return cachedHtml;
    }

    // 2. Verify invoice registration status before network call
    final localRecord =
        await (_db.select(
              _db.localInvoices,
            )..where((tbl) => tbl.localId.equals(id) | tbl.serverId.equals(id)))
            .getSingleOrNull();

    if (localRecord != null &&
        localRecord.syncStatus == 'offlineDraft' &&
        (localRecord.irn == null || localRecord.irn!.isEmpty)) {
      throw const AppError(
        code: 'OFFLINE_DRAFT_PENDING_REGISTRATION',
        message:
            'This invoice is an offline draft pending Ministry registration. Official tax receipts are only issued after government registration.',
        category: ErrorCategory.governmentPending,
      );
    }

    // 3. Resilient network request with generous timeout, backoff, and deduplication
    final targetServerId = localRecord?.serverId ?? id;
    final dedupeKey = 'receipt:$effectiveTenantId:$effectiveBranchId:$targetServerId';
    String html = '';

    try {
      final response = await _apiClient.get(
        '/api/v1/invoices/$targetServerId/document',
        options: Options(
          headers: {'Accept': 'text/html, application/xhtml+xml, */*'},
          responseType: ResponseType.plain,
        ),
        profile: NetworkProfiles.receipt,
        dedupeKey: dedupeKey,
        onProgress: onProgress,
      );
      html = response.data?.toString() ?? '';
    } on AppError catch (err) {
      if (err.status == 404) {
        // Try fallback to /receipt if /document is missing
        final response = await _apiClient.get(
          '/api/v1/invoices/$targetServerId/receipt',
          options: Options(
            headers: {'Accept': 'text/html, application/xhtml+xml, */*'},
            responseType: ResponseType.plain,
          ),
          profile: NetworkProfiles.receipt,
          dedupeKey: dedupeKey,
          onProgress: onProgress,
        );
        html = response.data?.toString() ?? '';
      } else {
        rethrow;
      }
    }

    if (html.isNotEmpty) {
      await _receiptCache.saveReceiptHtml(
        tenantId: effectiveTenantId,
        branchId: effectiveBranchId,
        invoiceId: id,
        htmlContent: html,
        documentNumber: localRecord?.documentNumber,
        irn: localRecord?.irn,
      );
      return html;
    }

    throw const AppError(
      code: 'RECEIPT_EMPTY',
      message: 'Official receipt content received from the server was empty.',
      category: ErrorCategory.serverError,
    );
  }

  /// Background refresh for stale-while-revalidate pattern.
  Future<void> _refreshReceiptInBackground({
    required String id,
    required String tenantId,
    required String branchId,
  }) async {
    try {
      final response = await _apiClient.get(
        '/api/v1/invoices/$id/document',
        options: Options(
          headers: {'Accept': 'text/html, application/xhtml+xml, */*'},
          responseType: ResponseType.plain,
        ),
        profile: NetworkProfiles.background,
        dedupeKey: 'bg_receipt_refresh:$tenantId:$branchId:$id',
      );
      final newHtml = response.data?.toString() ?? '';
      if (newHtml.isNotEmpty) {
        await _receiptCache.saveReceiptHtml(
          tenantId: tenantId,
          branchId: branchId,
          invoiceId: id,
          htmlContent: newHtml,
        );
      }
    } catch (e) {
      debugPrint(
        '[InvoiceRepository] Background receipt refresh silent fail (retaining cache): $e',
      );
    }
  }

  Future<List<int>> downloadInvoicePdf(
    String id, {
    String? tenantId,
    String? branchId,
    void Function(ResilienceProgress progress)? onProgress,
  }) async {
    final effectiveTenantId =
        tenantId ?? _apiClient.activeTenantId ?? 'default_tenant';
    final effectiveBranchId =
        branchId ?? _apiClient.activeBranchId ?? 'default_branch';

    // Verify draft status
    final localRecord =
        await (_db.select(
              _db.localInvoices,
            )..where((tbl) => tbl.localId.equals(id) | tbl.serverId.equals(id)))
            .getSingleOrNull();

    if (localRecord != null &&
        localRecord.syncStatus == 'offlineDraft' &&
        (localRecord.irn == null || localRecord.irn!.isEmpty)) {
      throw const AppError(
        code: 'OFFLINE_DRAFT_PENDING_REGISTRATION',
        message:
            'This invoice is an offline draft. PDF download is only available after government registration.',
        category: ErrorCategory.governmentPending,
      );
    }

    final targetServerId = localRecord?.serverId ?? id;

    return await _apiClient.downloadBytes(
      '/api/v1/invoices/$targetServerId/pdf',
      options: Options(
        headers: {'Accept': 'application/pdf, application/octet-stream, */*'},
      ),
      profile: NetworkProfiles.document,
      dedupeKey: 'pdf:$effectiveTenantId:$effectiveBranchId:$targetServerId',
      onProgress: onProgress,
    );
  }

  Future<String> getInvoiceDocument(String invoiceId) async {
    final response = await _apiClient.get(
      '/api/v1/invoices/$invoiceId/document',
      options: Options(
        headers: {'Accept': 'text/html, application/xhtml+xml, */*'},
        responseType: ResponseType.plain,
      ),
      profile: NetworkProfiles.receipt,
    );
    return response.data?.toString() ?? '';
  }

  Future<String> reprintInvoice(
    String invoiceId, {
    String? tenantId,
    String? branchId,
  }) async {
    final effectiveTenantId =
        tenantId ?? _apiClient.activeTenantId ?? 'default_tenant';
    final effectiveBranchId =
        branchId ?? _apiClient.activeBranchId ?? 'default_branch';

    final response = await _apiClient.post(
      '/api/v1/invoices/$invoiceId/reprint',
      options: Options(
        headers: {'Accept': 'text/html, application/xhtml+xml, */*'},
        responseType: ResponseType.plain,
      ),
      profile: NetworkProfiles.receipt,
      dedupeKey: 'reprint:$effectiveTenantId:$effectiveBranchId:$invoiceId',
    );
    final html = response.data?.toString() ?? '';
    if (html.isNotEmpty) {
      await _receiptCache.saveReceiptHtml(
        tenantId: effectiveTenantId,
        branchId: effectiveBranchId,
        invoiceId: invoiceId,
        htmlContent: html,
      );
    }
    return html;
  }
}
