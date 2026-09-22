import 'package:drift/drift.dart';
import 'package:drift_flutter/drift_flutter.dart';

part 'app_database.g.dart';

@DataClassName('LocalInvoiceRecord')
class LocalInvoices extends Table {
  TextColumn get localId => text()();
  TextColumn get tenantId => text()();
  TextColumn get branchId => text()();
  TextColumn get serverId => text().nullable()();
  TextColumn get documentNumber => text()();
  Int64Column get invoiceCounter => int64().nullable()();
  DateTimeColumn get invoiceDate => dateTime()();
  TextColumn get transactionType => text()();
  TextColumn get paymentMode => text()();
  TextColumn get buyerTin => text().nullable()();
  TextColumn get buyerName => text().nullable()();
  RealColumn get preTaxTotal => real()();
  RealColumn get taxTotal => real()();
  RealColumn get grandTotal => real()();
  TextColumn get currency => text().withDefault(const Constant('ETB'))();
  TextColumn get syncStatus => text()(); // 'synced', 'syncing', 'offlineDraft', 'syncError'
  TextColumn get irn => text().nullable()();
  TextColumn get rrn => text().nullable()();
  TextColumn get signedQr => text().nullable()();
  IntColumn get reprintCount => integer().withDefault(const Constant(0))();
  TextColumn get rawPayload => text()();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get syncedAt => dateTime().nullable()();

  @override
  Set<Column> get primaryKey => {localId};
}

@DataClassName('LocalInvoiceLineRecord')
class LocalInvoiceLines extends Table {
  TextColumn get lineId => text()();
  TextColumn get invoiceLocalId => text()();
  TextColumn get tenantId => text()();
  TextColumn get branchId => text()();
  IntColumn get lineNumber => integer()();
  TextColumn get itemCode => text()();
  TextColumn get productDescription => text()();
  RealColumn get quantity => real()();
  TextColumn get unit => text().withDefault(const Constant('PCS'))();
  RealColumn get unitPrice => real()();
  RealColumn get discount => real().withDefault(const Constant(0.0))();
  TextColumn get taxCode => text().withDefault(const Constant('VAT15'))();
  RealColumn get taxAmount => real()();
  RealColumn get totalLineAmount => real()();

  @override
  Set<Column> get primaryKey => {lineId};
}

@DataClassName('OutboxOperationRecord')
class OutboxOperations extends Table {
  TextColumn get operationId => text()();
  TextColumn get idempotencyKey => text()();
  TextColumn get tenantId => text()();
  TextColumn get branchId => text()();
  TextColumn get operationType => text()(); // 'CREATE_INVOICE', 'SYNC_OFFLINE_BATCH'
  TextColumn get endpoint => text()();
  TextColumn get payloadJson => text()();
  TextColumn get deviceSignature => text().nullable()();
  Int64Column get offlineSeqNo => int64().nullable()();
  DateTimeColumn get bufferedAt => dateTime()();
  DateTimeColumn get createdAt => dateTime()();
  IntColumn get attemptCount => integer().withDefault(const Constant(0))();
  DateTimeColumn get nextAttemptAt => dateTime().nullable()();
  TextColumn get syncState => text()(); // 'pending', 'syncing', 'synced', 'retryableFailure', 'permanentFailure'
  TextColumn get lastError => text().nullable()();

  @override
  Set<Column> get primaryKey => {operationId};
}

@DataClassName('LocalCustomerRecord')
class LocalCustomers extends Table {
  TextColumn get id => text()();
  TextColumn get tenantId => text()();
  TextColumn get branchId => text().nullable()();
  TextColumn get tin => text().nullable()();
  TextColumn get vatNumber => text().nullable()();
  TextColumn get legalName => text()();
  TextColumn get tradeName => text().nullable()();
  TextColumn get phone => text().nullable()();
  TextColumn get email => text().nullable()();
  TextColumn get country => text().withDefault(const Constant('ET'))();
  TextColumn get region => text().nullable()();
  TextColumn get city => text().nullable()();
  TextColumn get zone => text().nullable()();
  TextColumn get woreda => text().nullable()();
  TextColumn get kebele => text().nullable()();
  TextColumn get houseNumber => text().nullable()();
  TextColumn get buyerIdType => text().withDefault(const Constant('TIN'))();
  TextColumn get buyerIdNumber => text().nullable()();
  BoolColumn get isVatRegistered => boolean().withDefault(const Constant(false))();
  TextColumn get status => text().withDefault(const Constant('ACTIVE'))();
  TextColumn get syncStatus => text().withDefault(const Constant('synced'))();
  DateTimeColumn get createdAt => dateTime()();
  DateTimeColumn get updatedAt => dateTime().nullable()();

  @override
  Set<Column> get primaryKey => {id};
}

@DataClassName('LocalProductRecord')
class LocalProducts extends Table {
  TextColumn get id => text()();
  TextColumn get tenantId => text()();
  TextColumn get branchId => text()();
  TextColumn get itemCode => text()();
  TextColumn get description => text()();
  RealColumn get unitPrice => real()();
  TextColumn get taxCode => text().withDefault(const Constant('VAT15'))();
  TextColumn get unit => text().withDefault(const Constant('PCS'))();

  @override
  Set<Column> get primaryKey => {id};
}

@DriftDatabase(tables: [
  LocalInvoices,
  LocalInvoiceLines,
  OutboxOperations,
  LocalCustomers,
  LocalProducts,
])
class AppDatabase extends _$AppDatabase {
  AppDatabase([QueryExecutor? e]) : super(e ?? _openConnection());

  @override
  int get schemaVersion => 2;

  @override
  MigrationStrategy get migration => MigrationStrategy(
        onCreate: (Migrator m) async {
          await m.createAll();
        },
        onUpgrade: (Migrator m, int from, int to) async {
          if (from < 2) {
            try {
              await m.createTable(localCustomers);
            } catch (_) {}
            try {
              await m.createTable(localProducts);
            } catch (_) {}
            await _ensureCustomerColumns();
          }
        },
        beforeOpen: (details) async {
          await customStatement('PRAGMA foreign_keys = ON');
          await _ensureCustomerColumns();
        },
      );

  Future<void> _ensureCustomerColumns() async {
    final alterQueries = [
      'ALTER TABLE local_customers ADD COLUMN branch_id TEXT;',
      'ALTER TABLE local_customers ADD COLUMN tin TEXT;',
      'ALTER TABLE local_customers ADD COLUMN vat_number TEXT;',
      'ALTER TABLE local_customers ADD COLUMN legal_name TEXT NOT NULL DEFAULT "";',
      'ALTER TABLE local_customers ADD COLUMN trade_name TEXT;',
      'ALTER TABLE local_customers ADD COLUMN phone TEXT;',
      'ALTER TABLE local_customers ADD COLUMN email TEXT;',
      'ALTER TABLE local_customers ADD COLUMN country TEXT NOT NULL DEFAULT "ET";',
      'ALTER TABLE local_customers ADD COLUMN region TEXT;',
      'ALTER TABLE local_customers ADD COLUMN city TEXT;',
      'ALTER TABLE local_customers ADD COLUMN zone TEXT;',
      'ALTER TABLE local_customers ADD COLUMN woreda TEXT;',
      'ALTER TABLE local_customers ADD COLUMN kebele TEXT;',
      'ALTER TABLE local_customers ADD COLUMN house_number TEXT;',
      'ALTER TABLE local_customers ADD COLUMN buyer_id_type TEXT NOT NULL DEFAULT "TIN";',
      'ALTER TABLE local_customers ADD COLUMN buyer_id_number TEXT;',
      'ALTER TABLE local_customers ADD COLUMN is_vat_registered INTEGER NOT NULL DEFAULT 0;',
      'ALTER TABLE local_customers ADD COLUMN status TEXT NOT NULL DEFAULT "ACTIVE";',
      'ALTER TABLE local_customers ADD COLUMN sync_status TEXT NOT NULL DEFAULT "synced";',
      'ALTER TABLE local_customers ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0;',
      'ALTER TABLE local_customers ADD COLUMN updated_at INTEGER;',
    ];
    for (final q in alterQueries) {
      try {
        await customStatement(q);
      } catch (_) {
        // Ignored if column already exists in SQLite
      }
    }
  }

  static QueryExecutor _openConnection() {
    return driftDatabase(name: 'ut_einvoice_client_db');
  }

  // ==========================================
  // Strictly Scoped Invoices DAO Methods
  // ==========================================
  Future<List<LocalInvoiceRecord>> getScopedInvoices({
    required String tenantId,
    required String branchId,
    int limit = 50,
    int offset = 0,
    String? status,
  }) {
    final query = select(localInvoices)
      ..where((tbl) => tbl.tenantId.equals(tenantId));

    if (branchId.isNotEmpty && branchId != 'ALL') {
      query.where((tbl) => tbl.branchId.equals(branchId));
    }

    if (status != null && status.isNotEmpty) {
      query.where((tbl) => tbl.syncStatus.equals(status));
    }

    query.orderBy([
      (tbl) => OrderingTerm(expression: tbl.createdAt, mode: OrderingMode.desc)
    ]);
    query.limit(limit, offset: offset);

    return query.get();
  }

  Future<LocalInvoiceRecord?> getScopedInvoiceById({
    required String tenantId,
    required String branchId,
    required String localId,
  }) {
    return (select(localInvoices)
          ..where((tbl) =>
              tbl.tenantId.equals(tenantId) &
              tbl.branchId.equals(branchId) &
              tbl.localId.equals(localId)))
        .getSingleOrNull();
  }

  Future<void> insertScopedInvoice({
    required LocalInvoicesCompanion invoice,
    required List<LocalInvoiceLinesCompanion> lines,
    OutboxOperationsCompanion? outboxEntry,
  }) {
    return transaction(() async {
      await into(localInvoices).insert(invoice);
      for (final line in lines) {
        await into(localInvoiceLines).insert(line);
      }
      if (outboxEntry != null) {
        await into(outboxOperations).insert(outboxEntry);
      }
    });
  }

  Future<void> updateInvoiceStatus({
    required String tenantId,
    required String branchId,
    required String localId,
    required String status,
    String? serverId,
    String? irn,
    String? rrn,
    String? signedQr,
  }) {
    return (update(localInvoices)
          ..where((tbl) =>
              tbl.tenantId.equals(tenantId) &
              tbl.branchId.equals(branchId) &
              tbl.localId.equals(localId)))
        .write(LocalInvoicesCompanion(
      syncStatus: Value(status),
      serverId: serverId != null ? Value(serverId) : const Value.absent(),
      irn: irn != null ? Value(irn) : const Value.absent(),
      rrn: rrn != null ? Value(rrn) : const Value.absent(),
      signedQr: signedQr != null ? Value(signedQr) : const Value.absent(),
      syncedAt: status == 'synced' ? Value(DateTime.now()) : const Value.absent(),
    ));
  }

  // ==========================================
  // Strictly Scoped Outbox DAO Methods
  // ==========================================
  Future<List<OutboxOperationRecord>> getPendingOutboxOperations({
    required String tenantId,
    required String branchId,
    int limit = 50,
  }) {
    return (select(outboxOperations)
          ..where((tbl) =>
              tbl.tenantId.equals(tenantId) &
              tbl.branchId.equals(branchId) &
              tbl.syncState.isIn(['pending', 'retryableFailure']))
          ..orderBy([(tbl) => OrderingTerm(expression: tbl.createdAt, mode: OrderingMode.asc)])
          ..limit(limit))
        .get();
  }

  Future<List<OutboxOperationRecord>> getPendingOutbox({
    required String tenantId,
    required String branchId,
    int limit = 50,
  }) =>
      getPendingOutboxOperations(tenantId: tenantId, branchId: branchId, limit: limit);

  Future<void> updateOutboxState({
    required String operationId,
    required String syncState,
    String? lastError,
    DateTime? nextAttemptAt,
  }) {
    return (update(outboxOperations)..where((tbl) => tbl.operationId.equals(operationId)))
        .write(OutboxOperationsCompanion(
      syncState: Value(syncState),
      lastError: lastError != null ? Value(lastError) : const Value.absent(),
      nextAttemptAt: nextAttemptAt != null ? Value(nextAttemptAt) : const Value.absent(),
    ));
  }

  Future<void> deleteCompletedOutboxOperation(String operationId) {
    return (delete(outboxOperations)..where((tbl) => tbl.operationId.equals(operationId))).go();
  }

  // ==========================================
  // Bounded Data Retention Cleaner
  // ==========================================
  Future<int> purgeSyncedInvoicesOlderThan({
    required String tenantId,
    required DateTime cutoff,
  }) {
    // Strictly purges ONLY server-confirmed (synced) invoices older than retention window.
    // Never purges unsynced offline drafts or pending outbox items.
    return (delete(localInvoices)
          ..where((tbl) =>
              tbl.tenantId.equals(tenantId) &
              tbl.syncStatus.equals('synced') &
              tbl.syncedAt.isSmallerThanValue(cutoff)))
        .go();
  }

  // ==========================================
  // Strictly Scoped Customers DAO Methods
  // ==========================================
  Future<List<LocalCustomerRecord>> searchScopedCustomers({
    required String tenantId,
    String? query,
    int limit = 50,
  }) {
    final q = select(localCustomers)
      ..where((tbl) => tbl.tenantId.equals(tenantId) & tbl.status.equals('ACTIVE'));

    if (query != null && query.trim().isNotEmpty) {
      final clean = '%${query.trim()}%';
      q.where((tbl) =>
          tbl.legalName.like(clean) |
          tbl.tin.like(clean) |
          tbl.phone.like(clean) |
          tbl.tradeName.like(clean));
    }

    q.orderBy([(tbl) => OrderingTerm(expression: tbl.legalName, mode: OrderingMode.asc)]);
    q.limit(limit);
    return q.get();
  }

  Stream<List<LocalCustomerRecord>> watchScopedCustomers({
    required String tenantId,
  }) {
    return (select(localCustomers)
          ..where((tbl) => tbl.tenantId.equals(tenantId) & tbl.status.equals('ACTIVE'))
          ..orderBy([(tbl) => OrderingTerm(expression: tbl.legalName, mode: OrderingMode.asc)]))
        .watch();
  }

  Future<LocalCustomerRecord?> getCustomerById(String id, String tenantId) {
    return (select(localCustomers)
          ..where((tbl) => tbl.id.equals(id) & tbl.tenantId.equals(tenantId))
          ..limit(1))
        .getSingleOrNull();
  }

  Future<LocalCustomerRecord?> getCustomerByTin(String tin, String tenantId) {
    return (select(localCustomers)
          ..where((tbl) => tbl.tin.equals(tin) & tbl.tenantId.equals(tenantId))
          ..limit(1))
        .getSingleOrNull();
  }

  Future<void> upsertLocalCustomer(LocalCustomersCompanion customer) {
    return into(localCustomers).insertOnConflictUpdate(customer);
  }
}
