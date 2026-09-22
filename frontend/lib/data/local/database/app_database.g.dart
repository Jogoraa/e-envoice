// GENERATED CODE - DO NOT MODIFY BY HAND

part of 'app_database.dart';

// ignore_for_file: type=lint
class $LocalInvoicesTable extends LocalInvoices
    with TableInfo<$LocalInvoicesTable, LocalInvoiceRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LocalInvoicesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _localIdMeta = const VerificationMeta(
    'localId',
  );
  @override
  late final GeneratedColumn<String> localId = GeneratedColumn<String>(
    'local_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tenantIdMeta = const VerificationMeta(
    'tenantId',
  );
  @override
  late final GeneratedColumn<String> tenantId = GeneratedColumn<String>(
    'tenant_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _branchIdMeta = const VerificationMeta(
    'branchId',
  );
  @override
  late final GeneratedColumn<String> branchId = GeneratedColumn<String>(
    'branch_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _serverIdMeta = const VerificationMeta(
    'serverId',
  );
  @override
  late final GeneratedColumn<String> serverId = GeneratedColumn<String>(
    'server_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _documentNumberMeta = const VerificationMeta(
    'documentNumber',
  );
  @override
  late final GeneratedColumn<String> documentNumber = GeneratedColumn<String>(
    'document_number',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _invoiceCounterMeta = const VerificationMeta(
    'invoiceCounter',
  );
  @override
  late final GeneratedColumn<BigInt> invoiceCounter = GeneratedColumn<BigInt>(
    'invoice_counter',
    aliasedName,
    true,
    type: DriftSqlType.bigInt,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _invoiceDateMeta = const VerificationMeta(
    'invoiceDate',
  );
  @override
  late final GeneratedColumn<DateTime> invoiceDate = GeneratedColumn<DateTime>(
    'invoice_date',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _transactionTypeMeta = const VerificationMeta(
    'transactionType',
  );
  @override
  late final GeneratedColumn<String> transactionType = GeneratedColumn<String>(
    'transaction_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _paymentModeMeta = const VerificationMeta(
    'paymentMode',
  );
  @override
  late final GeneratedColumn<String> paymentMode = GeneratedColumn<String>(
    'payment_mode',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _buyerTinMeta = const VerificationMeta(
    'buyerTin',
  );
  @override
  late final GeneratedColumn<String> buyerTin = GeneratedColumn<String>(
    'buyer_tin',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _buyerNameMeta = const VerificationMeta(
    'buyerName',
  );
  @override
  late final GeneratedColumn<String> buyerName = GeneratedColumn<String>(
    'buyer_name',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _preTaxTotalMeta = const VerificationMeta(
    'preTaxTotal',
  );
  @override
  late final GeneratedColumn<double> preTaxTotal = GeneratedColumn<double>(
    'pre_tax_total',
    aliasedName,
    false,
    type: DriftSqlType.double,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _taxTotalMeta = const VerificationMeta(
    'taxTotal',
  );
  @override
  late final GeneratedColumn<double> taxTotal = GeneratedColumn<double>(
    'tax_total',
    aliasedName,
    false,
    type: DriftSqlType.double,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _grandTotalMeta = const VerificationMeta(
    'grandTotal',
  );
  @override
  late final GeneratedColumn<double> grandTotal = GeneratedColumn<double>(
    'grand_total',
    aliasedName,
    false,
    type: DriftSqlType.double,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _currencyMeta = const VerificationMeta(
    'currency',
  );
  @override
  late final GeneratedColumn<String> currency = GeneratedColumn<String>(
    'currency',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('ETB'),
  );
  static const VerificationMeta _syncStatusMeta = const VerificationMeta(
    'syncStatus',
  );
  @override
  late final GeneratedColumn<String> syncStatus = GeneratedColumn<String>(
    'sync_status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _irnMeta = const VerificationMeta('irn');
  @override
  late final GeneratedColumn<String> irn = GeneratedColumn<String>(
    'irn',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _rrnMeta = const VerificationMeta('rrn');
  @override
  late final GeneratedColumn<String> rrn = GeneratedColumn<String>(
    'rrn',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _signedQrMeta = const VerificationMeta(
    'signedQr',
  );
  @override
  late final GeneratedColumn<String> signedQr = GeneratedColumn<String>(
    'signed_qr',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _reprintCountMeta = const VerificationMeta(
    'reprintCount',
  );
  @override
  late final GeneratedColumn<int> reprintCount = GeneratedColumn<int>(
    'reprint_count',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _rawPayloadMeta = const VerificationMeta(
    'rawPayload',
  );
  @override
  late final GeneratedColumn<String> rawPayload = GeneratedColumn<String>(
    'raw_payload',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _syncedAtMeta = const VerificationMeta(
    'syncedAt',
  );
  @override
  late final GeneratedColumn<DateTime> syncedAt = GeneratedColumn<DateTime>(
    'synced_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    localId,
    tenantId,
    branchId,
    serverId,
    documentNumber,
    invoiceCounter,
    invoiceDate,
    transactionType,
    paymentMode,
    buyerTin,
    buyerName,
    preTaxTotal,
    taxTotal,
    grandTotal,
    currency,
    syncStatus,
    irn,
    rrn,
    signedQr,
    reprintCount,
    rawPayload,
    createdAt,
    syncedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'local_invoices';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalInvoiceRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('local_id')) {
      context.handle(
        _localIdMeta,
        localId.isAcceptableOrUnknown(data['local_id']!, _localIdMeta),
      );
    } else if (isInserting) {
      context.missing(_localIdMeta);
    }
    if (data.containsKey('tenant_id')) {
      context.handle(
        _tenantIdMeta,
        tenantId.isAcceptableOrUnknown(data['tenant_id']!, _tenantIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tenantIdMeta);
    }
    if (data.containsKey('branch_id')) {
      context.handle(
        _branchIdMeta,
        branchId.isAcceptableOrUnknown(data['branch_id']!, _branchIdMeta),
      );
    } else if (isInserting) {
      context.missing(_branchIdMeta);
    }
    if (data.containsKey('server_id')) {
      context.handle(
        _serverIdMeta,
        serverId.isAcceptableOrUnknown(data['server_id']!, _serverIdMeta),
      );
    }
    if (data.containsKey('document_number')) {
      context.handle(
        _documentNumberMeta,
        documentNumber.isAcceptableOrUnknown(
          data['document_number']!,
          _documentNumberMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_documentNumberMeta);
    }
    if (data.containsKey('invoice_counter')) {
      context.handle(
        _invoiceCounterMeta,
        invoiceCounter.isAcceptableOrUnknown(
          data['invoice_counter']!,
          _invoiceCounterMeta,
        ),
      );
    }
    if (data.containsKey('invoice_date')) {
      context.handle(
        _invoiceDateMeta,
        invoiceDate.isAcceptableOrUnknown(
          data['invoice_date']!,
          _invoiceDateMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_invoiceDateMeta);
    }
    if (data.containsKey('transaction_type')) {
      context.handle(
        _transactionTypeMeta,
        transactionType.isAcceptableOrUnknown(
          data['transaction_type']!,
          _transactionTypeMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_transactionTypeMeta);
    }
    if (data.containsKey('payment_mode')) {
      context.handle(
        _paymentModeMeta,
        paymentMode.isAcceptableOrUnknown(
          data['payment_mode']!,
          _paymentModeMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_paymentModeMeta);
    }
    if (data.containsKey('buyer_tin')) {
      context.handle(
        _buyerTinMeta,
        buyerTin.isAcceptableOrUnknown(data['buyer_tin']!, _buyerTinMeta),
      );
    }
    if (data.containsKey('buyer_name')) {
      context.handle(
        _buyerNameMeta,
        buyerName.isAcceptableOrUnknown(data['buyer_name']!, _buyerNameMeta),
      );
    }
    if (data.containsKey('pre_tax_total')) {
      context.handle(
        _preTaxTotalMeta,
        preTaxTotal.isAcceptableOrUnknown(
          data['pre_tax_total']!,
          _preTaxTotalMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_preTaxTotalMeta);
    }
    if (data.containsKey('tax_total')) {
      context.handle(
        _taxTotalMeta,
        taxTotal.isAcceptableOrUnknown(data['tax_total']!, _taxTotalMeta),
      );
    } else if (isInserting) {
      context.missing(_taxTotalMeta);
    }
    if (data.containsKey('grand_total')) {
      context.handle(
        _grandTotalMeta,
        grandTotal.isAcceptableOrUnknown(data['grand_total']!, _grandTotalMeta),
      );
    } else if (isInserting) {
      context.missing(_grandTotalMeta);
    }
    if (data.containsKey('currency')) {
      context.handle(
        _currencyMeta,
        currency.isAcceptableOrUnknown(data['currency']!, _currencyMeta),
      );
    }
    if (data.containsKey('sync_status')) {
      context.handle(
        _syncStatusMeta,
        syncStatus.isAcceptableOrUnknown(data['sync_status']!, _syncStatusMeta),
      );
    } else if (isInserting) {
      context.missing(_syncStatusMeta);
    }
    if (data.containsKey('irn')) {
      context.handle(
        _irnMeta,
        irn.isAcceptableOrUnknown(data['irn']!, _irnMeta),
      );
    }
    if (data.containsKey('rrn')) {
      context.handle(
        _rrnMeta,
        rrn.isAcceptableOrUnknown(data['rrn']!, _rrnMeta),
      );
    }
    if (data.containsKey('signed_qr')) {
      context.handle(
        _signedQrMeta,
        signedQr.isAcceptableOrUnknown(data['signed_qr']!, _signedQrMeta),
      );
    }
    if (data.containsKey('reprint_count')) {
      context.handle(
        _reprintCountMeta,
        reprintCount.isAcceptableOrUnknown(
          data['reprint_count']!,
          _reprintCountMeta,
        ),
      );
    }
    if (data.containsKey('raw_payload')) {
      context.handle(
        _rawPayloadMeta,
        rawPayload.isAcceptableOrUnknown(data['raw_payload']!, _rawPayloadMeta),
      );
    } else if (isInserting) {
      context.missing(_rawPayloadMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('synced_at')) {
      context.handle(
        _syncedAtMeta,
        syncedAt.isAcceptableOrUnknown(data['synced_at']!, _syncedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {localId};
  @override
  LocalInvoiceRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalInvoiceRecord(
      localId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}local_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      branchId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}branch_id'],
      )!,
      serverId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}server_id'],
      ),
      documentNumber: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}document_number'],
      )!,
      invoiceCounter: attachedDatabase.typeMapping.read(
        DriftSqlType.bigInt,
        data['${effectivePrefix}invoice_counter'],
      ),
      invoiceDate: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}invoice_date'],
      )!,
      transactionType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}transaction_type'],
      )!,
      paymentMode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}payment_mode'],
      )!,
      buyerTin: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}buyer_tin'],
      ),
      buyerName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}buyer_name'],
      ),
      preTaxTotal: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}pre_tax_total'],
      )!,
      taxTotal: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}tax_total'],
      )!,
      grandTotal: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}grand_total'],
      )!,
      currency: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}currency'],
      )!,
      syncStatus: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}sync_status'],
      )!,
      irn: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}irn'],
      ),
      rrn: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}rrn'],
      ),
      signedQr: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}signed_qr'],
      ),
      reprintCount: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}reprint_count'],
      )!,
      rawPayload: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}raw_payload'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      syncedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}synced_at'],
      ),
    );
  }

  @override
  $LocalInvoicesTable createAlias(String alias) {
    return $LocalInvoicesTable(attachedDatabase, alias);
  }
}

class LocalInvoiceRecord extends DataClass
    implements Insertable<LocalInvoiceRecord> {
  final String localId;
  final String tenantId;
  final String branchId;
  final String? serverId;
  final String documentNumber;
  final BigInt? invoiceCounter;
  final DateTime invoiceDate;
  final String transactionType;
  final String paymentMode;
  final String? buyerTin;
  final String? buyerName;
  final double preTaxTotal;
  final double taxTotal;
  final double grandTotal;
  final String currency;
  final String syncStatus;
  final String? irn;
  final String? rrn;
  final String? signedQr;
  final int reprintCount;
  final String rawPayload;
  final DateTime createdAt;
  final DateTime? syncedAt;
  const LocalInvoiceRecord({
    required this.localId,
    required this.tenantId,
    required this.branchId,
    this.serverId,
    required this.documentNumber,
    this.invoiceCounter,
    required this.invoiceDate,
    required this.transactionType,
    required this.paymentMode,
    this.buyerTin,
    this.buyerName,
    required this.preTaxTotal,
    required this.taxTotal,
    required this.grandTotal,
    required this.currency,
    required this.syncStatus,
    this.irn,
    this.rrn,
    this.signedQr,
    required this.reprintCount,
    required this.rawPayload,
    required this.createdAt,
    this.syncedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['local_id'] = Variable<String>(localId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['branch_id'] = Variable<String>(branchId);
    if (!nullToAbsent || serverId != null) {
      map['server_id'] = Variable<String>(serverId);
    }
    map['document_number'] = Variable<String>(documentNumber);
    if (!nullToAbsent || invoiceCounter != null) {
      map['invoice_counter'] = Variable<BigInt>(invoiceCounter);
    }
    map['invoice_date'] = Variable<DateTime>(invoiceDate);
    map['transaction_type'] = Variable<String>(transactionType);
    map['payment_mode'] = Variable<String>(paymentMode);
    if (!nullToAbsent || buyerTin != null) {
      map['buyer_tin'] = Variable<String>(buyerTin);
    }
    if (!nullToAbsent || buyerName != null) {
      map['buyer_name'] = Variable<String>(buyerName);
    }
    map['pre_tax_total'] = Variable<double>(preTaxTotal);
    map['tax_total'] = Variable<double>(taxTotal);
    map['grand_total'] = Variable<double>(grandTotal);
    map['currency'] = Variable<String>(currency);
    map['sync_status'] = Variable<String>(syncStatus);
    if (!nullToAbsent || irn != null) {
      map['irn'] = Variable<String>(irn);
    }
    if (!nullToAbsent || rrn != null) {
      map['rrn'] = Variable<String>(rrn);
    }
    if (!nullToAbsent || signedQr != null) {
      map['signed_qr'] = Variable<String>(signedQr);
    }
    map['reprint_count'] = Variable<int>(reprintCount);
    map['raw_payload'] = Variable<String>(rawPayload);
    map['created_at'] = Variable<DateTime>(createdAt);
    if (!nullToAbsent || syncedAt != null) {
      map['synced_at'] = Variable<DateTime>(syncedAt);
    }
    return map;
  }

  LocalInvoicesCompanion toCompanion(bool nullToAbsent) {
    return LocalInvoicesCompanion(
      localId: Value(localId),
      tenantId: Value(tenantId),
      branchId: Value(branchId),
      serverId: serverId == null && nullToAbsent
          ? const Value.absent()
          : Value(serverId),
      documentNumber: Value(documentNumber),
      invoiceCounter: invoiceCounter == null && nullToAbsent
          ? const Value.absent()
          : Value(invoiceCounter),
      invoiceDate: Value(invoiceDate),
      transactionType: Value(transactionType),
      paymentMode: Value(paymentMode),
      buyerTin: buyerTin == null && nullToAbsent
          ? const Value.absent()
          : Value(buyerTin),
      buyerName: buyerName == null && nullToAbsent
          ? const Value.absent()
          : Value(buyerName),
      preTaxTotal: Value(preTaxTotal),
      taxTotal: Value(taxTotal),
      grandTotal: Value(grandTotal),
      currency: Value(currency),
      syncStatus: Value(syncStatus),
      irn: irn == null && nullToAbsent ? const Value.absent() : Value(irn),
      rrn: rrn == null && nullToAbsent ? const Value.absent() : Value(rrn),
      signedQr: signedQr == null && nullToAbsent
          ? const Value.absent()
          : Value(signedQr),
      reprintCount: Value(reprintCount),
      rawPayload: Value(rawPayload),
      createdAt: Value(createdAt),
      syncedAt: syncedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(syncedAt),
    );
  }

  factory LocalInvoiceRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalInvoiceRecord(
      localId: serializer.fromJson<String>(json['localId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      branchId: serializer.fromJson<String>(json['branchId']),
      serverId: serializer.fromJson<String?>(json['serverId']),
      documentNumber: serializer.fromJson<String>(json['documentNumber']),
      invoiceCounter: serializer.fromJson<BigInt?>(json['invoiceCounter']),
      invoiceDate: serializer.fromJson<DateTime>(json['invoiceDate']),
      transactionType: serializer.fromJson<String>(json['transactionType']),
      paymentMode: serializer.fromJson<String>(json['paymentMode']),
      buyerTin: serializer.fromJson<String?>(json['buyerTin']),
      buyerName: serializer.fromJson<String?>(json['buyerName']),
      preTaxTotal: serializer.fromJson<double>(json['preTaxTotal']),
      taxTotal: serializer.fromJson<double>(json['taxTotal']),
      grandTotal: serializer.fromJson<double>(json['grandTotal']),
      currency: serializer.fromJson<String>(json['currency']),
      syncStatus: serializer.fromJson<String>(json['syncStatus']),
      irn: serializer.fromJson<String?>(json['irn']),
      rrn: serializer.fromJson<String?>(json['rrn']),
      signedQr: serializer.fromJson<String?>(json['signedQr']),
      reprintCount: serializer.fromJson<int>(json['reprintCount']),
      rawPayload: serializer.fromJson<String>(json['rawPayload']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      syncedAt: serializer.fromJson<DateTime?>(json['syncedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'localId': serializer.toJson<String>(localId),
      'tenantId': serializer.toJson<String>(tenantId),
      'branchId': serializer.toJson<String>(branchId),
      'serverId': serializer.toJson<String?>(serverId),
      'documentNumber': serializer.toJson<String>(documentNumber),
      'invoiceCounter': serializer.toJson<BigInt?>(invoiceCounter),
      'invoiceDate': serializer.toJson<DateTime>(invoiceDate),
      'transactionType': serializer.toJson<String>(transactionType),
      'paymentMode': serializer.toJson<String>(paymentMode),
      'buyerTin': serializer.toJson<String?>(buyerTin),
      'buyerName': serializer.toJson<String?>(buyerName),
      'preTaxTotal': serializer.toJson<double>(preTaxTotal),
      'taxTotal': serializer.toJson<double>(taxTotal),
      'grandTotal': serializer.toJson<double>(grandTotal),
      'currency': serializer.toJson<String>(currency),
      'syncStatus': serializer.toJson<String>(syncStatus),
      'irn': serializer.toJson<String?>(irn),
      'rrn': serializer.toJson<String?>(rrn),
      'signedQr': serializer.toJson<String?>(signedQr),
      'reprintCount': serializer.toJson<int>(reprintCount),
      'rawPayload': serializer.toJson<String>(rawPayload),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'syncedAt': serializer.toJson<DateTime?>(syncedAt),
    };
  }

  LocalInvoiceRecord copyWith({
    String? localId,
    String? tenantId,
    String? branchId,
    Value<String?> serverId = const Value.absent(),
    String? documentNumber,
    Value<BigInt?> invoiceCounter = const Value.absent(),
    DateTime? invoiceDate,
    String? transactionType,
    String? paymentMode,
    Value<String?> buyerTin = const Value.absent(),
    Value<String?> buyerName = const Value.absent(),
    double? preTaxTotal,
    double? taxTotal,
    double? grandTotal,
    String? currency,
    String? syncStatus,
    Value<String?> irn = const Value.absent(),
    Value<String?> rrn = const Value.absent(),
    Value<String?> signedQr = const Value.absent(),
    int? reprintCount,
    String? rawPayload,
    DateTime? createdAt,
    Value<DateTime?> syncedAt = const Value.absent(),
  }) => LocalInvoiceRecord(
    localId: localId ?? this.localId,
    tenantId: tenantId ?? this.tenantId,
    branchId: branchId ?? this.branchId,
    serverId: serverId.present ? serverId.value : this.serverId,
    documentNumber: documentNumber ?? this.documentNumber,
    invoiceCounter: invoiceCounter.present
        ? invoiceCounter.value
        : this.invoiceCounter,
    invoiceDate: invoiceDate ?? this.invoiceDate,
    transactionType: transactionType ?? this.transactionType,
    paymentMode: paymentMode ?? this.paymentMode,
    buyerTin: buyerTin.present ? buyerTin.value : this.buyerTin,
    buyerName: buyerName.present ? buyerName.value : this.buyerName,
    preTaxTotal: preTaxTotal ?? this.preTaxTotal,
    taxTotal: taxTotal ?? this.taxTotal,
    grandTotal: grandTotal ?? this.grandTotal,
    currency: currency ?? this.currency,
    syncStatus: syncStatus ?? this.syncStatus,
    irn: irn.present ? irn.value : this.irn,
    rrn: rrn.present ? rrn.value : this.rrn,
    signedQr: signedQr.present ? signedQr.value : this.signedQr,
    reprintCount: reprintCount ?? this.reprintCount,
    rawPayload: rawPayload ?? this.rawPayload,
    createdAt: createdAt ?? this.createdAt,
    syncedAt: syncedAt.present ? syncedAt.value : this.syncedAt,
  );
  LocalInvoiceRecord copyWithCompanion(LocalInvoicesCompanion data) {
    return LocalInvoiceRecord(
      localId: data.localId.present ? data.localId.value : this.localId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      branchId: data.branchId.present ? data.branchId.value : this.branchId,
      serverId: data.serverId.present ? data.serverId.value : this.serverId,
      documentNumber: data.documentNumber.present
          ? data.documentNumber.value
          : this.documentNumber,
      invoiceCounter: data.invoiceCounter.present
          ? data.invoiceCounter.value
          : this.invoiceCounter,
      invoiceDate: data.invoiceDate.present
          ? data.invoiceDate.value
          : this.invoiceDate,
      transactionType: data.transactionType.present
          ? data.transactionType.value
          : this.transactionType,
      paymentMode: data.paymentMode.present
          ? data.paymentMode.value
          : this.paymentMode,
      buyerTin: data.buyerTin.present ? data.buyerTin.value : this.buyerTin,
      buyerName: data.buyerName.present ? data.buyerName.value : this.buyerName,
      preTaxTotal: data.preTaxTotal.present
          ? data.preTaxTotal.value
          : this.preTaxTotal,
      taxTotal: data.taxTotal.present ? data.taxTotal.value : this.taxTotal,
      grandTotal: data.grandTotal.present
          ? data.grandTotal.value
          : this.grandTotal,
      currency: data.currency.present ? data.currency.value : this.currency,
      syncStatus: data.syncStatus.present
          ? data.syncStatus.value
          : this.syncStatus,
      irn: data.irn.present ? data.irn.value : this.irn,
      rrn: data.rrn.present ? data.rrn.value : this.rrn,
      signedQr: data.signedQr.present ? data.signedQr.value : this.signedQr,
      reprintCount: data.reprintCount.present
          ? data.reprintCount.value
          : this.reprintCount,
      rawPayload: data.rawPayload.present
          ? data.rawPayload.value
          : this.rawPayload,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      syncedAt: data.syncedAt.present ? data.syncedAt.value : this.syncedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalInvoiceRecord(')
          ..write('localId: $localId, ')
          ..write('tenantId: $tenantId, ')
          ..write('branchId: $branchId, ')
          ..write('serverId: $serverId, ')
          ..write('documentNumber: $documentNumber, ')
          ..write('invoiceCounter: $invoiceCounter, ')
          ..write('invoiceDate: $invoiceDate, ')
          ..write('transactionType: $transactionType, ')
          ..write('paymentMode: $paymentMode, ')
          ..write('buyerTin: $buyerTin, ')
          ..write('buyerName: $buyerName, ')
          ..write('preTaxTotal: $preTaxTotal, ')
          ..write('taxTotal: $taxTotal, ')
          ..write('grandTotal: $grandTotal, ')
          ..write('currency: $currency, ')
          ..write('syncStatus: $syncStatus, ')
          ..write('irn: $irn, ')
          ..write('rrn: $rrn, ')
          ..write('signedQr: $signedQr, ')
          ..write('reprintCount: $reprintCount, ')
          ..write('rawPayload: $rawPayload, ')
          ..write('createdAt: $createdAt, ')
          ..write('syncedAt: $syncedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hashAll([
    localId,
    tenantId,
    branchId,
    serverId,
    documentNumber,
    invoiceCounter,
    invoiceDate,
    transactionType,
    paymentMode,
    buyerTin,
    buyerName,
    preTaxTotal,
    taxTotal,
    grandTotal,
    currency,
    syncStatus,
    irn,
    rrn,
    signedQr,
    reprintCount,
    rawPayload,
    createdAt,
    syncedAt,
  ]);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalInvoiceRecord &&
          other.localId == this.localId &&
          other.tenantId == this.tenantId &&
          other.branchId == this.branchId &&
          other.serverId == this.serverId &&
          other.documentNumber == this.documentNumber &&
          other.invoiceCounter == this.invoiceCounter &&
          other.invoiceDate == this.invoiceDate &&
          other.transactionType == this.transactionType &&
          other.paymentMode == this.paymentMode &&
          other.buyerTin == this.buyerTin &&
          other.buyerName == this.buyerName &&
          other.preTaxTotal == this.preTaxTotal &&
          other.taxTotal == this.taxTotal &&
          other.grandTotal == this.grandTotal &&
          other.currency == this.currency &&
          other.syncStatus == this.syncStatus &&
          other.irn == this.irn &&
          other.rrn == this.rrn &&
          other.signedQr == this.signedQr &&
          other.reprintCount == this.reprintCount &&
          other.rawPayload == this.rawPayload &&
          other.createdAt == this.createdAt &&
          other.syncedAt == this.syncedAt);
}

class LocalInvoicesCompanion extends UpdateCompanion<LocalInvoiceRecord> {
  final Value<String> localId;
  final Value<String> tenantId;
  final Value<String> branchId;
  final Value<String?> serverId;
  final Value<String> documentNumber;
  final Value<BigInt?> invoiceCounter;
  final Value<DateTime> invoiceDate;
  final Value<String> transactionType;
  final Value<String> paymentMode;
  final Value<String?> buyerTin;
  final Value<String?> buyerName;
  final Value<double> preTaxTotal;
  final Value<double> taxTotal;
  final Value<double> grandTotal;
  final Value<String> currency;
  final Value<String> syncStatus;
  final Value<String?> irn;
  final Value<String?> rrn;
  final Value<String?> signedQr;
  final Value<int> reprintCount;
  final Value<String> rawPayload;
  final Value<DateTime> createdAt;
  final Value<DateTime?> syncedAt;
  final Value<int> rowid;
  const LocalInvoicesCompanion({
    this.localId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.branchId = const Value.absent(),
    this.serverId = const Value.absent(),
    this.documentNumber = const Value.absent(),
    this.invoiceCounter = const Value.absent(),
    this.invoiceDate = const Value.absent(),
    this.transactionType = const Value.absent(),
    this.paymentMode = const Value.absent(),
    this.buyerTin = const Value.absent(),
    this.buyerName = const Value.absent(),
    this.preTaxTotal = const Value.absent(),
    this.taxTotal = const Value.absent(),
    this.grandTotal = const Value.absent(),
    this.currency = const Value.absent(),
    this.syncStatus = const Value.absent(),
    this.irn = const Value.absent(),
    this.rrn = const Value.absent(),
    this.signedQr = const Value.absent(),
    this.reprintCount = const Value.absent(),
    this.rawPayload = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.syncedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  LocalInvoicesCompanion.insert({
    required String localId,
    required String tenantId,
    required String branchId,
    this.serverId = const Value.absent(),
    required String documentNumber,
    this.invoiceCounter = const Value.absent(),
    required DateTime invoiceDate,
    required String transactionType,
    required String paymentMode,
    this.buyerTin = const Value.absent(),
    this.buyerName = const Value.absent(),
    required double preTaxTotal,
    required double taxTotal,
    required double grandTotal,
    this.currency = const Value.absent(),
    required String syncStatus,
    this.irn = const Value.absent(),
    this.rrn = const Value.absent(),
    this.signedQr = const Value.absent(),
    this.reprintCount = const Value.absent(),
    required String rawPayload,
    required DateTime createdAt,
    this.syncedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : localId = Value(localId),
       tenantId = Value(tenantId),
       branchId = Value(branchId),
       documentNumber = Value(documentNumber),
       invoiceDate = Value(invoiceDate),
       transactionType = Value(transactionType),
       paymentMode = Value(paymentMode),
       preTaxTotal = Value(preTaxTotal),
       taxTotal = Value(taxTotal),
       grandTotal = Value(grandTotal),
       syncStatus = Value(syncStatus),
       rawPayload = Value(rawPayload),
       createdAt = Value(createdAt);
  static Insertable<LocalInvoiceRecord> custom({
    Expression<String>? localId,
    Expression<String>? tenantId,
    Expression<String>? branchId,
    Expression<String>? serverId,
    Expression<String>? documentNumber,
    Expression<BigInt>? invoiceCounter,
    Expression<DateTime>? invoiceDate,
    Expression<String>? transactionType,
    Expression<String>? paymentMode,
    Expression<String>? buyerTin,
    Expression<String>? buyerName,
    Expression<double>? preTaxTotal,
    Expression<double>? taxTotal,
    Expression<double>? grandTotal,
    Expression<String>? currency,
    Expression<String>? syncStatus,
    Expression<String>? irn,
    Expression<String>? rrn,
    Expression<String>? signedQr,
    Expression<int>? reprintCount,
    Expression<String>? rawPayload,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? syncedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (localId != null) 'local_id': localId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (branchId != null) 'branch_id': branchId,
      if (serverId != null) 'server_id': serverId,
      if (documentNumber != null) 'document_number': documentNumber,
      if (invoiceCounter != null) 'invoice_counter': invoiceCounter,
      if (invoiceDate != null) 'invoice_date': invoiceDate,
      if (transactionType != null) 'transaction_type': transactionType,
      if (paymentMode != null) 'payment_mode': paymentMode,
      if (buyerTin != null) 'buyer_tin': buyerTin,
      if (buyerName != null) 'buyer_name': buyerName,
      if (preTaxTotal != null) 'pre_tax_total': preTaxTotal,
      if (taxTotal != null) 'tax_total': taxTotal,
      if (grandTotal != null) 'grand_total': grandTotal,
      if (currency != null) 'currency': currency,
      if (syncStatus != null) 'sync_status': syncStatus,
      if (irn != null) 'irn': irn,
      if (rrn != null) 'rrn': rrn,
      if (signedQr != null) 'signed_qr': signedQr,
      if (reprintCount != null) 'reprint_count': reprintCount,
      if (rawPayload != null) 'raw_payload': rawPayload,
      if (createdAt != null) 'created_at': createdAt,
      if (syncedAt != null) 'synced_at': syncedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  LocalInvoicesCompanion copyWith({
    Value<String>? localId,
    Value<String>? tenantId,
    Value<String>? branchId,
    Value<String?>? serverId,
    Value<String>? documentNumber,
    Value<BigInt?>? invoiceCounter,
    Value<DateTime>? invoiceDate,
    Value<String>? transactionType,
    Value<String>? paymentMode,
    Value<String?>? buyerTin,
    Value<String?>? buyerName,
    Value<double>? preTaxTotal,
    Value<double>? taxTotal,
    Value<double>? grandTotal,
    Value<String>? currency,
    Value<String>? syncStatus,
    Value<String?>? irn,
    Value<String?>? rrn,
    Value<String?>? signedQr,
    Value<int>? reprintCount,
    Value<String>? rawPayload,
    Value<DateTime>? createdAt,
    Value<DateTime?>? syncedAt,
    Value<int>? rowid,
  }) {
    return LocalInvoicesCompanion(
      localId: localId ?? this.localId,
      tenantId: tenantId ?? this.tenantId,
      branchId: branchId ?? this.branchId,
      serverId: serverId ?? this.serverId,
      documentNumber: documentNumber ?? this.documentNumber,
      invoiceCounter: invoiceCounter ?? this.invoiceCounter,
      invoiceDate: invoiceDate ?? this.invoiceDate,
      transactionType: transactionType ?? this.transactionType,
      paymentMode: paymentMode ?? this.paymentMode,
      buyerTin: buyerTin ?? this.buyerTin,
      buyerName: buyerName ?? this.buyerName,
      preTaxTotal: preTaxTotal ?? this.preTaxTotal,
      taxTotal: taxTotal ?? this.taxTotal,
      grandTotal: grandTotal ?? this.grandTotal,
      currency: currency ?? this.currency,
      syncStatus: syncStatus ?? this.syncStatus,
      irn: irn ?? this.irn,
      rrn: rrn ?? this.rrn,
      signedQr: signedQr ?? this.signedQr,
      reprintCount: reprintCount ?? this.reprintCount,
      rawPayload: rawPayload ?? this.rawPayload,
      createdAt: createdAt ?? this.createdAt,
      syncedAt: syncedAt ?? this.syncedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (localId.present) {
      map['local_id'] = Variable<String>(localId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (branchId.present) {
      map['branch_id'] = Variable<String>(branchId.value);
    }
    if (serverId.present) {
      map['server_id'] = Variable<String>(serverId.value);
    }
    if (documentNumber.present) {
      map['document_number'] = Variable<String>(documentNumber.value);
    }
    if (invoiceCounter.present) {
      map['invoice_counter'] = Variable<BigInt>(invoiceCounter.value);
    }
    if (invoiceDate.present) {
      map['invoice_date'] = Variable<DateTime>(invoiceDate.value);
    }
    if (transactionType.present) {
      map['transaction_type'] = Variable<String>(transactionType.value);
    }
    if (paymentMode.present) {
      map['payment_mode'] = Variable<String>(paymentMode.value);
    }
    if (buyerTin.present) {
      map['buyer_tin'] = Variable<String>(buyerTin.value);
    }
    if (buyerName.present) {
      map['buyer_name'] = Variable<String>(buyerName.value);
    }
    if (preTaxTotal.present) {
      map['pre_tax_total'] = Variable<double>(preTaxTotal.value);
    }
    if (taxTotal.present) {
      map['tax_total'] = Variable<double>(taxTotal.value);
    }
    if (grandTotal.present) {
      map['grand_total'] = Variable<double>(grandTotal.value);
    }
    if (currency.present) {
      map['currency'] = Variable<String>(currency.value);
    }
    if (syncStatus.present) {
      map['sync_status'] = Variable<String>(syncStatus.value);
    }
    if (irn.present) {
      map['irn'] = Variable<String>(irn.value);
    }
    if (rrn.present) {
      map['rrn'] = Variable<String>(rrn.value);
    }
    if (signedQr.present) {
      map['signed_qr'] = Variable<String>(signedQr.value);
    }
    if (reprintCount.present) {
      map['reprint_count'] = Variable<int>(reprintCount.value);
    }
    if (rawPayload.present) {
      map['raw_payload'] = Variable<String>(rawPayload.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (syncedAt.present) {
      map['synced_at'] = Variable<DateTime>(syncedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LocalInvoicesCompanion(')
          ..write('localId: $localId, ')
          ..write('tenantId: $tenantId, ')
          ..write('branchId: $branchId, ')
          ..write('serverId: $serverId, ')
          ..write('documentNumber: $documentNumber, ')
          ..write('invoiceCounter: $invoiceCounter, ')
          ..write('invoiceDate: $invoiceDate, ')
          ..write('transactionType: $transactionType, ')
          ..write('paymentMode: $paymentMode, ')
          ..write('buyerTin: $buyerTin, ')
          ..write('buyerName: $buyerName, ')
          ..write('preTaxTotal: $preTaxTotal, ')
          ..write('taxTotal: $taxTotal, ')
          ..write('grandTotal: $grandTotal, ')
          ..write('currency: $currency, ')
          ..write('syncStatus: $syncStatus, ')
          ..write('irn: $irn, ')
          ..write('rrn: $rrn, ')
          ..write('signedQr: $signedQr, ')
          ..write('reprintCount: $reprintCount, ')
          ..write('rawPayload: $rawPayload, ')
          ..write('createdAt: $createdAt, ')
          ..write('syncedAt: $syncedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $LocalInvoiceLinesTable extends LocalInvoiceLines
    with TableInfo<$LocalInvoiceLinesTable, LocalInvoiceLineRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LocalInvoiceLinesTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _lineIdMeta = const VerificationMeta('lineId');
  @override
  late final GeneratedColumn<String> lineId = GeneratedColumn<String>(
    'line_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _invoiceLocalIdMeta = const VerificationMeta(
    'invoiceLocalId',
  );
  @override
  late final GeneratedColumn<String> invoiceLocalId = GeneratedColumn<String>(
    'invoice_local_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tenantIdMeta = const VerificationMeta(
    'tenantId',
  );
  @override
  late final GeneratedColumn<String> tenantId = GeneratedColumn<String>(
    'tenant_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _branchIdMeta = const VerificationMeta(
    'branchId',
  );
  @override
  late final GeneratedColumn<String> branchId = GeneratedColumn<String>(
    'branch_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _lineNumberMeta = const VerificationMeta(
    'lineNumber',
  );
  @override
  late final GeneratedColumn<int> lineNumber = GeneratedColumn<int>(
    'line_number',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _itemCodeMeta = const VerificationMeta(
    'itemCode',
  );
  @override
  late final GeneratedColumn<String> itemCode = GeneratedColumn<String>(
    'item_code',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _productDescriptionMeta =
      const VerificationMeta('productDescription');
  @override
  late final GeneratedColumn<String> productDescription =
      GeneratedColumn<String>(
        'product_description',
        aliasedName,
        false,
        type: DriftSqlType.string,
        requiredDuringInsert: true,
      );
  static const VerificationMeta _quantityMeta = const VerificationMeta(
    'quantity',
  );
  @override
  late final GeneratedColumn<double> quantity = GeneratedColumn<double>(
    'quantity',
    aliasedName,
    false,
    type: DriftSqlType.double,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _unitMeta = const VerificationMeta('unit');
  @override
  late final GeneratedColumn<String> unit = GeneratedColumn<String>(
    'unit',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('PCS'),
  );
  static const VerificationMeta _unitPriceMeta = const VerificationMeta(
    'unitPrice',
  );
  @override
  late final GeneratedColumn<double> unitPrice = GeneratedColumn<double>(
    'unit_price',
    aliasedName,
    false,
    type: DriftSqlType.double,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _discountMeta = const VerificationMeta(
    'discount',
  );
  @override
  late final GeneratedColumn<double> discount = GeneratedColumn<double>(
    'discount',
    aliasedName,
    false,
    type: DriftSqlType.double,
    requiredDuringInsert: false,
    defaultValue: const Constant(0.0),
  );
  static const VerificationMeta _taxCodeMeta = const VerificationMeta(
    'taxCode',
  );
  @override
  late final GeneratedColumn<String> taxCode = GeneratedColumn<String>(
    'tax_code',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('VAT15'),
  );
  static const VerificationMeta _taxAmountMeta = const VerificationMeta(
    'taxAmount',
  );
  @override
  late final GeneratedColumn<double> taxAmount = GeneratedColumn<double>(
    'tax_amount',
    aliasedName,
    false,
    type: DriftSqlType.double,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _totalLineAmountMeta = const VerificationMeta(
    'totalLineAmount',
  );
  @override
  late final GeneratedColumn<double> totalLineAmount = GeneratedColumn<double>(
    'total_line_amount',
    aliasedName,
    false,
    type: DriftSqlType.double,
    requiredDuringInsert: true,
  );
  @override
  List<GeneratedColumn> get $columns => [
    lineId,
    invoiceLocalId,
    tenantId,
    branchId,
    lineNumber,
    itemCode,
    productDescription,
    quantity,
    unit,
    unitPrice,
    discount,
    taxCode,
    taxAmount,
    totalLineAmount,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'local_invoice_lines';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalInvoiceLineRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('line_id')) {
      context.handle(
        _lineIdMeta,
        lineId.isAcceptableOrUnknown(data['line_id']!, _lineIdMeta),
      );
    } else if (isInserting) {
      context.missing(_lineIdMeta);
    }
    if (data.containsKey('invoice_local_id')) {
      context.handle(
        _invoiceLocalIdMeta,
        invoiceLocalId.isAcceptableOrUnknown(
          data['invoice_local_id']!,
          _invoiceLocalIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_invoiceLocalIdMeta);
    }
    if (data.containsKey('tenant_id')) {
      context.handle(
        _tenantIdMeta,
        tenantId.isAcceptableOrUnknown(data['tenant_id']!, _tenantIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tenantIdMeta);
    }
    if (data.containsKey('branch_id')) {
      context.handle(
        _branchIdMeta,
        branchId.isAcceptableOrUnknown(data['branch_id']!, _branchIdMeta),
      );
    } else if (isInserting) {
      context.missing(_branchIdMeta);
    }
    if (data.containsKey('line_number')) {
      context.handle(
        _lineNumberMeta,
        lineNumber.isAcceptableOrUnknown(data['line_number']!, _lineNumberMeta),
      );
    } else if (isInserting) {
      context.missing(_lineNumberMeta);
    }
    if (data.containsKey('item_code')) {
      context.handle(
        _itemCodeMeta,
        itemCode.isAcceptableOrUnknown(data['item_code']!, _itemCodeMeta),
      );
    } else if (isInserting) {
      context.missing(_itemCodeMeta);
    }
    if (data.containsKey('product_description')) {
      context.handle(
        _productDescriptionMeta,
        productDescription.isAcceptableOrUnknown(
          data['product_description']!,
          _productDescriptionMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_productDescriptionMeta);
    }
    if (data.containsKey('quantity')) {
      context.handle(
        _quantityMeta,
        quantity.isAcceptableOrUnknown(data['quantity']!, _quantityMeta),
      );
    } else if (isInserting) {
      context.missing(_quantityMeta);
    }
    if (data.containsKey('unit')) {
      context.handle(
        _unitMeta,
        unit.isAcceptableOrUnknown(data['unit']!, _unitMeta),
      );
    }
    if (data.containsKey('unit_price')) {
      context.handle(
        _unitPriceMeta,
        unitPrice.isAcceptableOrUnknown(data['unit_price']!, _unitPriceMeta),
      );
    } else if (isInserting) {
      context.missing(_unitPriceMeta);
    }
    if (data.containsKey('discount')) {
      context.handle(
        _discountMeta,
        discount.isAcceptableOrUnknown(data['discount']!, _discountMeta),
      );
    }
    if (data.containsKey('tax_code')) {
      context.handle(
        _taxCodeMeta,
        taxCode.isAcceptableOrUnknown(data['tax_code']!, _taxCodeMeta),
      );
    }
    if (data.containsKey('tax_amount')) {
      context.handle(
        _taxAmountMeta,
        taxAmount.isAcceptableOrUnknown(data['tax_amount']!, _taxAmountMeta),
      );
    } else if (isInserting) {
      context.missing(_taxAmountMeta);
    }
    if (data.containsKey('total_line_amount')) {
      context.handle(
        _totalLineAmountMeta,
        totalLineAmount.isAcceptableOrUnknown(
          data['total_line_amount']!,
          _totalLineAmountMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_totalLineAmountMeta);
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {lineId};
  @override
  LocalInvoiceLineRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalInvoiceLineRecord(
      lineId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}line_id'],
      )!,
      invoiceLocalId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}invoice_local_id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      branchId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}branch_id'],
      )!,
      lineNumber: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}line_number'],
      )!,
      itemCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}item_code'],
      )!,
      productDescription: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}product_description'],
      )!,
      quantity: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}quantity'],
      )!,
      unit: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}unit'],
      )!,
      unitPrice: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}unit_price'],
      )!,
      discount: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}discount'],
      )!,
      taxCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tax_code'],
      )!,
      taxAmount: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}tax_amount'],
      )!,
      totalLineAmount: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}total_line_amount'],
      )!,
    );
  }

  @override
  $LocalInvoiceLinesTable createAlias(String alias) {
    return $LocalInvoiceLinesTable(attachedDatabase, alias);
  }
}

class LocalInvoiceLineRecord extends DataClass
    implements Insertable<LocalInvoiceLineRecord> {
  final String lineId;
  final String invoiceLocalId;
  final String tenantId;
  final String branchId;
  final int lineNumber;
  final String itemCode;
  final String productDescription;
  final double quantity;
  final String unit;
  final double unitPrice;
  final double discount;
  final String taxCode;
  final double taxAmount;
  final double totalLineAmount;
  const LocalInvoiceLineRecord({
    required this.lineId,
    required this.invoiceLocalId,
    required this.tenantId,
    required this.branchId,
    required this.lineNumber,
    required this.itemCode,
    required this.productDescription,
    required this.quantity,
    required this.unit,
    required this.unitPrice,
    required this.discount,
    required this.taxCode,
    required this.taxAmount,
    required this.totalLineAmount,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['line_id'] = Variable<String>(lineId);
    map['invoice_local_id'] = Variable<String>(invoiceLocalId);
    map['tenant_id'] = Variable<String>(tenantId);
    map['branch_id'] = Variable<String>(branchId);
    map['line_number'] = Variable<int>(lineNumber);
    map['item_code'] = Variable<String>(itemCode);
    map['product_description'] = Variable<String>(productDescription);
    map['quantity'] = Variable<double>(quantity);
    map['unit'] = Variable<String>(unit);
    map['unit_price'] = Variable<double>(unitPrice);
    map['discount'] = Variable<double>(discount);
    map['tax_code'] = Variable<String>(taxCode);
    map['tax_amount'] = Variable<double>(taxAmount);
    map['total_line_amount'] = Variable<double>(totalLineAmount);
    return map;
  }

  LocalInvoiceLinesCompanion toCompanion(bool nullToAbsent) {
    return LocalInvoiceLinesCompanion(
      lineId: Value(lineId),
      invoiceLocalId: Value(invoiceLocalId),
      tenantId: Value(tenantId),
      branchId: Value(branchId),
      lineNumber: Value(lineNumber),
      itemCode: Value(itemCode),
      productDescription: Value(productDescription),
      quantity: Value(quantity),
      unit: Value(unit),
      unitPrice: Value(unitPrice),
      discount: Value(discount),
      taxCode: Value(taxCode),
      taxAmount: Value(taxAmount),
      totalLineAmount: Value(totalLineAmount),
    );
  }

  factory LocalInvoiceLineRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalInvoiceLineRecord(
      lineId: serializer.fromJson<String>(json['lineId']),
      invoiceLocalId: serializer.fromJson<String>(json['invoiceLocalId']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      branchId: serializer.fromJson<String>(json['branchId']),
      lineNumber: serializer.fromJson<int>(json['lineNumber']),
      itemCode: serializer.fromJson<String>(json['itemCode']),
      productDescription: serializer.fromJson<String>(
        json['productDescription'],
      ),
      quantity: serializer.fromJson<double>(json['quantity']),
      unit: serializer.fromJson<String>(json['unit']),
      unitPrice: serializer.fromJson<double>(json['unitPrice']),
      discount: serializer.fromJson<double>(json['discount']),
      taxCode: serializer.fromJson<String>(json['taxCode']),
      taxAmount: serializer.fromJson<double>(json['taxAmount']),
      totalLineAmount: serializer.fromJson<double>(json['totalLineAmount']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'lineId': serializer.toJson<String>(lineId),
      'invoiceLocalId': serializer.toJson<String>(invoiceLocalId),
      'tenantId': serializer.toJson<String>(tenantId),
      'branchId': serializer.toJson<String>(branchId),
      'lineNumber': serializer.toJson<int>(lineNumber),
      'itemCode': serializer.toJson<String>(itemCode),
      'productDescription': serializer.toJson<String>(productDescription),
      'quantity': serializer.toJson<double>(quantity),
      'unit': serializer.toJson<String>(unit),
      'unitPrice': serializer.toJson<double>(unitPrice),
      'discount': serializer.toJson<double>(discount),
      'taxCode': serializer.toJson<String>(taxCode),
      'taxAmount': serializer.toJson<double>(taxAmount),
      'totalLineAmount': serializer.toJson<double>(totalLineAmount),
    };
  }

  LocalInvoiceLineRecord copyWith({
    String? lineId,
    String? invoiceLocalId,
    String? tenantId,
    String? branchId,
    int? lineNumber,
    String? itemCode,
    String? productDescription,
    double? quantity,
    String? unit,
    double? unitPrice,
    double? discount,
    String? taxCode,
    double? taxAmount,
    double? totalLineAmount,
  }) => LocalInvoiceLineRecord(
    lineId: lineId ?? this.lineId,
    invoiceLocalId: invoiceLocalId ?? this.invoiceLocalId,
    tenantId: tenantId ?? this.tenantId,
    branchId: branchId ?? this.branchId,
    lineNumber: lineNumber ?? this.lineNumber,
    itemCode: itemCode ?? this.itemCode,
    productDescription: productDescription ?? this.productDescription,
    quantity: quantity ?? this.quantity,
    unit: unit ?? this.unit,
    unitPrice: unitPrice ?? this.unitPrice,
    discount: discount ?? this.discount,
    taxCode: taxCode ?? this.taxCode,
    taxAmount: taxAmount ?? this.taxAmount,
    totalLineAmount: totalLineAmount ?? this.totalLineAmount,
  );
  LocalInvoiceLineRecord copyWithCompanion(LocalInvoiceLinesCompanion data) {
    return LocalInvoiceLineRecord(
      lineId: data.lineId.present ? data.lineId.value : this.lineId,
      invoiceLocalId: data.invoiceLocalId.present
          ? data.invoiceLocalId.value
          : this.invoiceLocalId,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      branchId: data.branchId.present ? data.branchId.value : this.branchId,
      lineNumber: data.lineNumber.present
          ? data.lineNumber.value
          : this.lineNumber,
      itemCode: data.itemCode.present ? data.itemCode.value : this.itemCode,
      productDescription: data.productDescription.present
          ? data.productDescription.value
          : this.productDescription,
      quantity: data.quantity.present ? data.quantity.value : this.quantity,
      unit: data.unit.present ? data.unit.value : this.unit,
      unitPrice: data.unitPrice.present ? data.unitPrice.value : this.unitPrice,
      discount: data.discount.present ? data.discount.value : this.discount,
      taxCode: data.taxCode.present ? data.taxCode.value : this.taxCode,
      taxAmount: data.taxAmount.present ? data.taxAmount.value : this.taxAmount,
      totalLineAmount: data.totalLineAmount.present
          ? data.totalLineAmount.value
          : this.totalLineAmount,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalInvoiceLineRecord(')
          ..write('lineId: $lineId, ')
          ..write('invoiceLocalId: $invoiceLocalId, ')
          ..write('tenantId: $tenantId, ')
          ..write('branchId: $branchId, ')
          ..write('lineNumber: $lineNumber, ')
          ..write('itemCode: $itemCode, ')
          ..write('productDescription: $productDescription, ')
          ..write('quantity: $quantity, ')
          ..write('unit: $unit, ')
          ..write('unitPrice: $unitPrice, ')
          ..write('discount: $discount, ')
          ..write('taxCode: $taxCode, ')
          ..write('taxAmount: $taxAmount, ')
          ..write('totalLineAmount: $totalLineAmount')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    lineId,
    invoiceLocalId,
    tenantId,
    branchId,
    lineNumber,
    itemCode,
    productDescription,
    quantity,
    unit,
    unitPrice,
    discount,
    taxCode,
    taxAmount,
    totalLineAmount,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalInvoiceLineRecord &&
          other.lineId == this.lineId &&
          other.invoiceLocalId == this.invoiceLocalId &&
          other.tenantId == this.tenantId &&
          other.branchId == this.branchId &&
          other.lineNumber == this.lineNumber &&
          other.itemCode == this.itemCode &&
          other.productDescription == this.productDescription &&
          other.quantity == this.quantity &&
          other.unit == this.unit &&
          other.unitPrice == this.unitPrice &&
          other.discount == this.discount &&
          other.taxCode == this.taxCode &&
          other.taxAmount == this.taxAmount &&
          other.totalLineAmount == this.totalLineAmount);
}

class LocalInvoiceLinesCompanion
    extends UpdateCompanion<LocalInvoiceLineRecord> {
  final Value<String> lineId;
  final Value<String> invoiceLocalId;
  final Value<String> tenantId;
  final Value<String> branchId;
  final Value<int> lineNumber;
  final Value<String> itemCode;
  final Value<String> productDescription;
  final Value<double> quantity;
  final Value<String> unit;
  final Value<double> unitPrice;
  final Value<double> discount;
  final Value<String> taxCode;
  final Value<double> taxAmount;
  final Value<double> totalLineAmount;
  final Value<int> rowid;
  const LocalInvoiceLinesCompanion({
    this.lineId = const Value.absent(),
    this.invoiceLocalId = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.branchId = const Value.absent(),
    this.lineNumber = const Value.absent(),
    this.itemCode = const Value.absent(),
    this.productDescription = const Value.absent(),
    this.quantity = const Value.absent(),
    this.unit = const Value.absent(),
    this.unitPrice = const Value.absent(),
    this.discount = const Value.absent(),
    this.taxCode = const Value.absent(),
    this.taxAmount = const Value.absent(),
    this.totalLineAmount = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  LocalInvoiceLinesCompanion.insert({
    required String lineId,
    required String invoiceLocalId,
    required String tenantId,
    required String branchId,
    required int lineNumber,
    required String itemCode,
    required String productDescription,
    required double quantity,
    this.unit = const Value.absent(),
    required double unitPrice,
    this.discount = const Value.absent(),
    this.taxCode = const Value.absent(),
    required double taxAmount,
    required double totalLineAmount,
    this.rowid = const Value.absent(),
  }) : lineId = Value(lineId),
       invoiceLocalId = Value(invoiceLocalId),
       tenantId = Value(tenantId),
       branchId = Value(branchId),
       lineNumber = Value(lineNumber),
       itemCode = Value(itemCode),
       productDescription = Value(productDescription),
       quantity = Value(quantity),
       unitPrice = Value(unitPrice),
       taxAmount = Value(taxAmount),
       totalLineAmount = Value(totalLineAmount);
  static Insertable<LocalInvoiceLineRecord> custom({
    Expression<String>? lineId,
    Expression<String>? invoiceLocalId,
    Expression<String>? tenantId,
    Expression<String>? branchId,
    Expression<int>? lineNumber,
    Expression<String>? itemCode,
    Expression<String>? productDescription,
    Expression<double>? quantity,
    Expression<String>? unit,
    Expression<double>? unitPrice,
    Expression<double>? discount,
    Expression<String>? taxCode,
    Expression<double>? taxAmount,
    Expression<double>? totalLineAmount,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (lineId != null) 'line_id': lineId,
      if (invoiceLocalId != null) 'invoice_local_id': invoiceLocalId,
      if (tenantId != null) 'tenant_id': tenantId,
      if (branchId != null) 'branch_id': branchId,
      if (lineNumber != null) 'line_number': lineNumber,
      if (itemCode != null) 'item_code': itemCode,
      if (productDescription != null) 'product_description': productDescription,
      if (quantity != null) 'quantity': quantity,
      if (unit != null) 'unit': unit,
      if (unitPrice != null) 'unit_price': unitPrice,
      if (discount != null) 'discount': discount,
      if (taxCode != null) 'tax_code': taxCode,
      if (taxAmount != null) 'tax_amount': taxAmount,
      if (totalLineAmount != null) 'total_line_amount': totalLineAmount,
      if (rowid != null) 'rowid': rowid,
    });
  }

  LocalInvoiceLinesCompanion copyWith({
    Value<String>? lineId,
    Value<String>? invoiceLocalId,
    Value<String>? tenantId,
    Value<String>? branchId,
    Value<int>? lineNumber,
    Value<String>? itemCode,
    Value<String>? productDescription,
    Value<double>? quantity,
    Value<String>? unit,
    Value<double>? unitPrice,
    Value<double>? discount,
    Value<String>? taxCode,
    Value<double>? taxAmount,
    Value<double>? totalLineAmount,
    Value<int>? rowid,
  }) {
    return LocalInvoiceLinesCompanion(
      lineId: lineId ?? this.lineId,
      invoiceLocalId: invoiceLocalId ?? this.invoiceLocalId,
      tenantId: tenantId ?? this.tenantId,
      branchId: branchId ?? this.branchId,
      lineNumber: lineNumber ?? this.lineNumber,
      itemCode: itemCode ?? this.itemCode,
      productDescription: productDescription ?? this.productDescription,
      quantity: quantity ?? this.quantity,
      unit: unit ?? this.unit,
      unitPrice: unitPrice ?? this.unitPrice,
      discount: discount ?? this.discount,
      taxCode: taxCode ?? this.taxCode,
      taxAmount: taxAmount ?? this.taxAmount,
      totalLineAmount: totalLineAmount ?? this.totalLineAmount,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (lineId.present) {
      map['line_id'] = Variable<String>(lineId.value);
    }
    if (invoiceLocalId.present) {
      map['invoice_local_id'] = Variable<String>(invoiceLocalId.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (branchId.present) {
      map['branch_id'] = Variable<String>(branchId.value);
    }
    if (lineNumber.present) {
      map['line_number'] = Variable<int>(lineNumber.value);
    }
    if (itemCode.present) {
      map['item_code'] = Variable<String>(itemCode.value);
    }
    if (productDescription.present) {
      map['product_description'] = Variable<String>(productDescription.value);
    }
    if (quantity.present) {
      map['quantity'] = Variable<double>(quantity.value);
    }
    if (unit.present) {
      map['unit'] = Variable<String>(unit.value);
    }
    if (unitPrice.present) {
      map['unit_price'] = Variable<double>(unitPrice.value);
    }
    if (discount.present) {
      map['discount'] = Variable<double>(discount.value);
    }
    if (taxCode.present) {
      map['tax_code'] = Variable<String>(taxCode.value);
    }
    if (taxAmount.present) {
      map['tax_amount'] = Variable<double>(taxAmount.value);
    }
    if (totalLineAmount.present) {
      map['total_line_amount'] = Variable<double>(totalLineAmount.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LocalInvoiceLinesCompanion(')
          ..write('lineId: $lineId, ')
          ..write('invoiceLocalId: $invoiceLocalId, ')
          ..write('tenantId: $tenantId, ')
          ..write('branchId: $branchId, ')
          ..write('lineNumber: $lineNumber, ')
          ..write('itemCode: $itemCode, ')
          ..write('productDescription: $productDescription, ')
          ..write('quantity: $quantity, ')
          ..write('unit: $unit, ')
          ..write('unitPrice: $unitPrice, ')
          ..write('discount: $discount, ')
          ..write('taxCode: $taxCode, ')
          ..write('taxAmount: $taxAmount, ')
          ..write('totalLineAmount: $totalLineAmount, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $OutboxOperationsTable extends OutboxOperations
    with TableInfo<$OutboxOperationsTable, OutboxOperationRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $OutboxOperationsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _operationIdMeta = const VerificationMeta(
    'operationId',
  );
  @override
  late final GeneratedColumn<String> operationId = GeneratedColumn<String>(
    'operation_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _idempotencyKeyMeta = const VerificationMeta(
    'idempotencyKey',
  );
  @override
  late final GeneratedColumn<String> idempotencyKey = GeneratedColumn<String>(
    'idempotency_key',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tenantIdMeta = const VerificationMeta(
    'tenantId',
  );
  @override
  late final GeneratedColumn<String> tenantId = GeneratedColumn<String>(
    'tenant_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _branchIdMeta = const VerificationMeta(
    'branchId',
  );
  @override
  late final GeneratedColumn<String> branchId = GeneratedColumn<String>(
    'branch_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _operationTypeMeta = const VerificationMeta(
    'operationType',
  );
  @override
  late final GeneratedColumn<String> operationType = GeneratedColumn<String>(
    'operation_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _endpointMeta = const VerificationMeta(
    'endpoint',
  );
  @override
  late final GeneratedColumn<String> endpoint = GeneratedColumn<String>(
    'endpoint',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _payloadJsonMeta = const VerificationMeta(
    'payloadJson',
  );
  @override
  late final GeneratedColumn<String> payloadJson = GeneratedColumn<String>(
    'payload_json',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _deviceSignatureMeta = const VerificationMeta(
    'deviceSignature',
  );
  @override
  late final GeneratedColumn<String> deviceSignature = GeneratedColumn<String>(
    'device_signature',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _offlineSeqNoMeta = const VerificationMeta(
    'offlineSeqNo',
  );
  @override
  late final GeneratedColumn<BigInt> offlineSeqNo = GeneratedColumn<BigInt>(
    'offline_seq_no',
    aliasedName,
    true,
    type: DriftSqlType.bigInt,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _bufferedAtMeta = const VerificationMeta(
    'bufferedAt',
  );
  @override
  late final GeneratedColumn<DateTime> bufferedAt = GeneratedColumn<DateTime>(
    'buffered_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _attemptCountMeta = const VerificationMeta(
    'attemptCount',
  );
  @override
  late final GeneratedColumn<int> attemptCount = GeneratedColumn<int>(
    'attempt_count',
    aliasedName,
    false,
    type: DriftSqlType.int,
    requiredDuringInsert: false,
    defaultValue: const Constant(0),
  );
  static const VerificationMeta _nextAttemptAtMeta = const VerificationMeta(
    'nextAttemptAt',
  );
  @override
  late final GeneratedColumn<DateTime> nextAttemptAt =
      GeneratedColumn<DateTime>(
        'next_attempt_at',
        aliasedName,
        true,
        type: DriftSqlType.dateTime,
        requiredDuringInsert: false,
      );
  static const VerificationMeta _syncStateMeta = const VerificationMeta(
    'syncState',
  );
  @override
  late final GeneratedColumn<String> syncState = GeneratedColumn<String>(
    'sync_state',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _lastErrorMeta = const VerificationMeta(
    'lastError',
  );
  @override
  late final GeneratedColumn<String> lastError = GeneratedColumn<String>(
    'last_error',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    operationId,
    idempotencyKey,
    tenantId,
    branchId,
    operationType,
    endpoint,
    payloadJson,
    deviceSignature,
    offlineSeqNo,
    bufferedAt,
    createdAt,
    attemptCount,
    nextAttemptAt,
    syncState,
    lastError,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'outbox_operations';
  @override
  VerificationContext validateIntegrity(
    Insertable<OutboxOperationRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('operation_id')) {
      context.handle(
        _operationIdMeta,
        operationId.isAcceptableOrUnknown(
          data['operation_id']!,
          _operationIdMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_operationIdMeta);
    }
    if (data.containsKey('idempotency_key')) {
      context.handle(
        _idempotencyKeyMeta,
        idempotencyKey.isAcceptableOrUnknown(
          data['idempotency_key']!,
          _idempotencyKeyMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_idempotencyKeyMeta);
    }
    if (data.containsKey('tenant_id')) {
      context.handle(
        _tenantIdMeta,
        tenantId.isAcceptableOrUnknown(data['tenant_id']!, _tenantIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tenantIdMeta);
    }
    if (data.containsKey('branch_id')) {
      context.handle(
        _branchIdMeta,
        branchId.isAcceptableOrUnknown(data['branch_id']!, _branchIdMeta),
      );
    } else if (isInserting) {
      context.missing(_branchIdMeta);
    }
    if (data.containsKey('operation_type')) {
      context.handle(
        _operationTypeMeta,
        operationType.isAcceptableOrUnknown(
          data['operation_type']!,
          _operationTypeMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_operationTypeMeta);
    }
    if (data.containsKey('endpoint')) {
      context.handle(
        _endpointMeta,
        endpoint.isAcceptableOrUnknown(data['endpoint']!, _endpointMeta),
      );
    } else if (isInserting) {
      context.missing(_endpointMeta);
    }
    if (data.containsKey('payload_json')) {
      context.handle(
        _payloadJsonMeta,
        payloadJson.isAcceptableOrUnknown(
          data['payload_json']!,
          _payloadJsonMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_payloadJsonMeta);
    }
    if (data.containsKey('device_signature')) {
      context.handle(
        _deviceSignatureMeta,
        deviceSignature.isAcceptableOrUnknown(
          data['device_signature']!,
          _deviceSignatureMeta,
        ),
      );
    }
    if (data.containsKey('offline_seq_no')) {
      context.handle(
        _offlineSeqNoMeta,
        offlineSeqNo.isAcceptableOrUnknown(
          data['offline_seq_no']!,
          _offlineSeqNoMeta,
        ),
      );
    }
    if (data.containsKey('buffered_at')) {
      context.handle(
        _bufferedAtMeta,
        bufferedAt.isAcceptableOrUnknown(data['buffered_at']!, _bufferedAtMeta),
      );
    } else if (isInserting) {
      context.missing(_bufferedAtMeta);
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('attempt_count')) {
      context.handle(
        _attemptCountMeta,
        attemptCount.isAcceptableOrUnknown(
          data['attempt_count']!,
          _attemptCountMeta,
        ),
      );
    }
    if (data.containsKey('next_attempt_at')) {
      context.handle(
        _nextAttemptAtMeta,
        nextAttemptAt.isAcceptableOrUnknown(
          data['next_attempt_at']!,
          _nextAttemptAtMeta,
        ),
      );
    }
    if (data.containsKey('sync_state')) {
      context.handle(
        _syncStateMeta,
        syncState.isAcceptableOrUnknown(data['sync_state']!, _syncStateMeta),
      );
    } else if (isInserting) {
      context.missing(_syncStateMeta);
    }
    if (data.containsKey('last_error')) {
      context.handle(
        _lastErrorMeta,
        lastError.isAcceptableOrUnknown(data['last_error']!, _lastErrorMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {operationId};
  @override
  OutboxOperationRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return OutboxOperationRecord(
      operationId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}operation_id'],
      )!,
      idempotencyKey: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}idempotency_key'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      branchId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}branch_id'],
      )!,
      operationType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}operation_type'],
      )!,
      endpoint: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}endpoint'],
      )!,
      payloadJson: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}payload_json'],
      )!,
      deviceSignature: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}device_signature'],
      ),
      offlineSeqNo: attachedDatabase.typeMapping.read(
        DriftSqlType.bigInt,
        data['${effectivePrefix}offline_seq_no'],
      ),
      bufferedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}buffered_at'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      attemptCount: attachedDatabase.typeMapping.read(
        DriftSqlType.int,
        data['${effectivePrefix}attempt_count'],
      )!,
      nextAttemptAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}next_attempt_at'],
      ),
      syncState: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}sync_state'],
      )!,
      lastError: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}last_error'],
      ),
    );
  }

  @override
  $OutboxOperationsTable createAlias(String alias) {
    return $OutboxOperationsTable(attachedDatabase, alias);
  }
}

class OutboxOperationRecord extends DataClass
    implements Insertable<OutboxOperationRecord> {
  final String operationId;
  final String idempotencyKey;
  final String tenantId;
  final String branchId;
  final String operationType;
  final String endpoint;
  final String payloadJson;
  final String? deviceSignature;
  final BigInt? offlineSeqNo;
  final DateTime bufferedAt;
  final DateTime createdAt;
  final int attemptCount;
  final DateTime? nextAttemptAt;
  final String syncState;
  final String? lastError;
  const OutboxOperationRecord({
    required this.operationId,
    required this.idempotencyKey,
    required this.tenantId,
    required this.branchId,
    required this.operationType,
    required this.endpoint,
    required this.payloadJson,
    this.deviceSignature,
    this.offlineSeqNo,
    required this.bufferedAt,
    required this.createdAt,
    required this.attemptCount,
    this.nextAttemptAt,
    required this.syncState,
    this.lastError,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['operation_id'] = Variable<String>(operationId);
    map['idempotency_key'] = Variable<String>(idempotencyKey);
    map['tenant_id'] = Variable<String>(tenantId);
    map['branch_id'] = Variable<String>(branchId);
    map['operation_type'] = Variable<String>(operationType);
    map['endpoint'] = Variable<String>(endpoint);
    map['payload_json'] = Variable<String>(payloadJson);
    if (!nullToAbsent || deviceSignature != null) {
      map['device_signature'] = Variable<String>(deviceSignature);
    }
    if (!nullToAbsent || offlineSeqNo != null) {
      map['offline_seq_no'] = Variable<BigInt>(offlineSeqNo);
    }
    map['buffered_at'] = Variable<DateTime>(bufferedAt);
    map['created_at'] = Variable<DateTime>(createdAt);
    map['attempt_count'] = Variable<int>(attemptCount);
    if (!nullToAbsent || nextAttemptAt != null) {
      map['next_attempt_at'] = Variable<DateTime>(nextAttemptAt);
    }
    map['sync_state'] = Variable<String>(syncState);
    if (!nullToAbsent || lastError != null) {
      map['last_error'] = Variable<String>(lastError);
    }
    return map;
  }

  OutboxOperationsCompanion toCompanion(bool nullToAbsent) {
    return OutboxOperationsCompanion(
      operationId: Value(operationId),
      idempotencyKey: Value(idempotencyKey),
      tenantId: Value(tenantId),
      branchId: Value(branchId),
      operationType: Value(operationType),
      endpoint: Value(endpoint),
      payloadJson: Value(payloadJson),
      deviceSignature: deviceSignature == null && nullToAbsent
          ? const Value.absent()
          : Value(deviceSignature),
      offlineSeqNo: offlineSeqNo == null && nullToAbsent
          ? const Value.absent()
          : Value(offlineSeqNo),
      bufferedAt: Value(bufferedAt),
      createdAt: Value(createdAt),
      attemptCount: Value(attemptCount),
      nextAttemptAt: nextAttemptAt == null && nullToAbsent
          ? const Value.absent()
          : Value(nextAttemptAt),
      syncState: Value(syncState),
      lastError: lastError == null && nullToAbsent
          ? const Value.absent()
          : Value(lastError),
    );
  }

  factory OutboxOperationRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return OutboxOperationRecord(
      operationId: serializer.fromJson<String>(json['operationId']),
      idempotencyKey: serializer.fromJson<String>(json['idempotencyKey']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      branchId: serializer.fromJson<String>(json['branchId']),
      operationType: serializer.fromJson<String>(json['operationType']),
      endpoint: serializer.fromJson<String>(json['endpoint']),
      payloadJson: serializer.fromJson<String>(json['payloadJson']),
      deviceSignature: serializer.fromJson<String?>(json['deviceSignature']),
      offlineSeqNo: serializer.fromJson<BigInt?>(json['offlineSeqNo']),
      bufferedAt: serializer.fromJson<DateTime>(json['bufferedAt']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      attemptCount: serializer.fromJson<int>(json['attemptCount']),
      nextAttemptAt: serializer.fromJson<DateTime?>(json['nextAttemptAt']),
      syncState: serializer.fromJson<String>(json['syncState']),
      lastError: serializer.fromJson<String?>(json['lastError']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'operationId': serializer.toJson<String>(operationId),
      'idempotencyKey': serializer.toJson<String>(idempotencyKey),
      'tenantId': serializer.toJson<String>(tenantId),
      'branchId': serializer.toJson<String>(branchId),
      'operationType': serializer.toJson<String>(operationType),
      'endpoint': serializer.toJson<String>(endpoint),
      'payloadJson': serializer.toJson<String>(payloadJson),
      'deviceSignature': serializer.toJson<String?>(deviceSignature),
      'offlineSeqNo': serializer.toJson<BigInt?>(offlineSeqNo),
      'bufferedAt': serializer.toJson<DateTime>(bufferedAt),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'attemptCount': serializer.toJson<int>(attemptCount),
      'nextAttemptAt': serializer.toJson<DateTime?>(nextAttemptAt),
      'syncState': serializer.toJson<String>(syncState),
      'lastError': serializer.toJson<String?>(lastError),
    };
  }

  OutboxOperationRecord copyWith({
    String? operationId,
    String? idempotencyKey,
    String? tenantId,
    String? branchId,
    String? operationType,
    String? endpoint,
    String? payloadJson,
    Value<String?> deviceSignature = const Value.absent(),
    Value<BigInt?> offlineSeqNo = const Value.absent(),
    DateTime? bufferedAt,
    DateTime? createdAt,
    int? attemptCount,
    Value<DateTime?> nextAttemptAt = const Value.absent(),
    String? syncState,
    Value<String?> lastError = const Value.absent(),
  }) => OutboxOperationRecord(
    operationId: operationId ?? this.operationId,
    idempotencyKey: idempotencyKey ?? this.idempotencyKey,
    tenantId: tenantId ?? this.tenantId,
    branchId: branchId ?? this.branchId,
    operationType: operationType ?? this.operationType,
    endpoint: endpoint ?? this.endpoint,
    payloadJson: payloadJson ?? this.payloadJson,
    deviceSignature: deviceSignature.present
        ? deviceSignature.value
        : this.deviceSignature,
    offlineSeqNo: offlineSeqNo.present ? offlineSeqNo.value : this.offlineSeqNo,
    bufferedAt: bufferedAt ?? this.bufferedAt,
    createdAt: createdAt ?? this.createdAt,
    attemptCount: attemptCount ?? this.attemptCount,
    nextAttemptAt: nextAttemptAt.present
        ? nextAttemptAt.value
        : this.nextAttemptAt,
    syncState: syncState ?? this.syncState,
    lastError: lastError.present ? lastError.value : this.lastError,
  );
  OutboxOperationRecord copyWithCompanion(OutboxOperationsCompanion data) {
    return OutboxOperationRecord(
      operationId: data.operationId.present
          ? data.operationId.value
          : this.operationId,
      idempotencyKey: data.idempotencyKey.present
          ? data.idempotencyKey.value
          : this.idempotencyKey,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      branchId: data.branchId.present ? data.branchId.value : this.branchId,
      operationType: data.operationType.present
          ? data.operationType.value
          : this.operationType,
      endpoint: data.endpoint.present ? data.endpoint.value : this.endpoint,
      payloadJson: data.payloadJson.present
          ? data.payloadJson.value
          : this.payloadJson,
      deviceSignature: data.deviceSignature.present
          ? data.deviceSignature.value
          : this.deviceSignature,
      offlineSeqNo: data.offlineSeqNo.present
          ? data.offlineSeqNo.value
          : this.offlineSeqNo,
      bufferedAt: data.bufferedAt.present
          ? data.bufferedAt.value
          : this.bufferedAt,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      attemptCount: data.attemptCount.present
          ? data.attemptCount.value
          : this.attemptCount,
      nextAttemptAt: data.nextAttemptAt.present
          ? data.nextAttemptAt.value
          : this.nextAttemptAt,
      syncState: data.syncState.present ? data.syncState.value : this.syncState,
      lastError: data.lastError.present ? data.lastError.value : this.lastError,
    );
  }

  @override
  String toString() {
    return (StringBuffer('OutboxOperationRecord(')
          ..write('operationId: $operationId, ')
          ..write('idempotencyKey: $idempotencyKey, ')
          ..write('tenantId: $tenantId, ')
          ..write('branchId: $branchId, ')
          ..write('operationType: $operationType, ')
          ..write('endpoint: $endpoint, ')
          ..write('payloadJson: $payloadJson, ')
          ..write('deviceSignature: $deviceSignature, ')
          ..write('offlineSeqNo: $offlineSeqNo, ')
          ..write('bufferedAt: $bufferedAt, ')
          ..write('createdAt: $createdAt, ')
          ..write('attemptCount: $attemptCount, ')
          ..write('nextAttemptAt: $nextAttemptAt, ')
          ..write('syncState: $syncState, ')
          ..write('lastError: $lastError')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    operationId,
    idempotencyKey,
    tenantId,
    branchId,
    operationType,
    endpoint,
    payloadJson,
    deviceSignature,
    offlineSeqNo,
    bufferedAt,
    createdAt,
    attemptCount,
    nextAttemptAt,
    syncState,
    lastError,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is OutboxOperationRecord &&
          other.operationId == this.operationId &&
          other.idempotencyKey == this.idempotencyKey &&
          other.tenantId == this.tenantId &&
          other.branchId == this.branchId &&
          other.operationType == this.operationType &&
          other.endpoint == this.endpoint &&
          other.payloadJson == this.payloadJson &&
          other.deviceSignature == this.deviceSignature &&
          other.offlineSeqNo == this.offlineSeqNo &&
          other.bufferedAt == this.bufferedAt &&
          other.createdAt == this.createdAt &&
          other.attemptCount == this.attemptCount &&
          other.nextAttemptAt == this.nextAttemptAt &&
          other.syncState == this.syncState &&
          other.lastError == this.lastError);
}

class OutboxOperationsCompanion extends UpdateCompanion<OutboxOperationRecord> {
  final Value<String> operationId;
  final Value<String> idempotencyKey;
  final Value<String> tenantId;
  final Value<String> branchId;
  final Value<String> operationType;
  final Value<String> endpoint;
  final Value<String> payloadJson;
  final Value<String?> deviceSignature;
  final Value<BigInt?> offlineSeqNo;
  final Value<DateTime> bufferedAt;
  final Value<DateTime> createdAt;
  final Value<int> attemptCount;
  final Value<DateTime?> nextAttemptAt;
  final Value<String> syncState;
  final Value<String?> lastError;
  final Value<int> rowid;
  const OutboxOperationsCompanion({
    this.operationId = const Value.absent(),
    this.idempotencyKey = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.branchId = const Value.absent(),
    this.operationType = const Value.absent(),
    this.endpoint = const Value.absent(),
    this.payloadJson = const Value.absent(),
    this.deviceSignature = const Value.absent(),
    this.offlineSeqNo = const Value.absent(),
    this.bufferedAt = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.attemptCount = const Value.absent(),
    this.nextAttemptAt = const Value.absent(),
    this.syncState = const Value.absent(),
    this.lastError = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  OutboxOperationsCompanion.insert({
    required String operationId,
    required String idempotencyKey,
    required String tenantId,
    required String branchId,
    required String operationType,
    required String endpoint,
    required String payloadJson,
    this.deviceSignature = const Value.absent(),
    this.offlineSeqNo = const Value.absent(),
    required DateTime bufferedAt,
    required DateTime createdAt,
    this.attemptCount = const Value.absent(),
    this.nextAttemptAt = const Value.absent(),
    required String syncState,
    this.lastError = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : operationId = Value(operationId),
       idempotencyKey = Value(idempotencyKey),
       tenantId = Value(tenantId),
       branchId = Value(branchId),
       operationType = Value(operationType),
       endpoint = Value(endpoint),
       payloadJson = Value(payloadJson),
       bufferedAt = Value(bufferedAt),
       createdAt = Value(createdAt),
       syncState = Value(syncState);
  static Insertable<OutboxOperationRecord> custom({
    Expression<String>? operationId,
    Expression<String>? idempotencyKey,
    Expression<String>? tenantId,
    Expression<String>? branchId,
    Expression<String>? operationType,
    Expression<String>? endpoint,
    Expression<String>? payloadJson,
    Expression<String>? deviceSignature,
    Expression<BigInt>? offlineSeqNo,
    Expression<DateTime>? bufferedAt,
    Expression<DateTime>? createdAt,
    Expression<int>? attemptCount,
    Expression<DateTime>? nextAttemptAt,
    Expression<String>? syncState,
    Expression<String>? lastError,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (operationId != null) 'operation_id': operationId,
      if (idempotencyKey != null) 'idempotency_key': idempotencyKey,
      if (tenantId != null) 'tenant_id': tenantId,
      if (branchId != null) 'branch_id': branchId,
      if (operationType != null) 'operation_type': operationType,
      if (endpoint != null) 'endpoint': endpoint,
      if (payloadJson != null) 'payload_json': payloadJson,
      if (deviceSignature != null) 'device_signature': deviceSignature,
      if (offlineSeqNo != null) 'offline_seq_no': offlineSeqNo,
      if (bufferedAt != null) 'buffered_at': bufferedAt,
      if (createdAt != null) 'created_at': createdAt,
      if (attemptCount != null) 'attempt_count': attemptCount,
      if (nextAttemptAt != null) 'next_attempt_at': nextAttemptAt,
      if (syncState != null) 'sync_state': syncState,
      if (lastError != null) 'last_error': lastError,
      if (rowid != null) 'rowid': rowid,
    });
  }

  OutboxOperationsCompanion copyWith({
    Value<String>? operationId,
    Value<String>? idempotencyKey,
    Value<String>? tenantId,
    Value<String>? branchId,
    Value<String>? operationType,
    Value<String>? endpoint,
    Value<String>? payloadJson,
    Value<String?>? deviceSignature,
    Value<BigInt?>? offlineSeqNo,
    Value<DateTime>? bufferedAt,
    Value<DateTime>? createdAt,
    Value<int>? attemptCount,
    Value<DateTime?>? nextAttemptAt,
    Value<String>? syncState,
    Value<String?>? lastError,
    Value<int>? rowid,
  }) {
    return OutboxOperationsCompanion(
      operationId: operationId ?? this.operationId,
      idempotencyKey: idempotencyKey ?? this.idempotencyKey,
      tenantId: tenantId ?? this.tenantId,
      branchId: branchId ?? this.branchId,
      operationType: operationType ?? this.operationType,
      endpoint: endpoint ?? this.endpoint,
      payloadJson: payloadJson ?? this.payloadJson,
      deviceSignature: deviceSignature ?? this.deviceSignature,
      offlineSeqNo: offlineSeqNo ?? this.offlineSeqNo,
      bufferedAt: bufferedAt ?? this.bufferedAt,
      createdAt: createdAt ?? this.createdAt,
      attemptCount: attemptCount ?? this.attemptCount,
      nextAttemptAt: nextAttemptAt ?? this.nextAttemptAt,
      syncState: syncState ?? this.syncState,
      lastError: lastError ?? this.lastError,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (operationId.present) {
      map['operation_id'] = Variable<String>(operationId.value);
    }
    if (idempotencyKey.present) {
      map['idempotency_key'] = Variable<String>(idempotencyKey.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (branchId.present) {
      map['branch_id'] = Variable<String>(branchId.value);
    }
    if (operationType.present) {
      map['operation_type'] = Variable<String>(operationType.value);
    }
    if (endpoint.present) {
      map['endpoint'] = Variable<String>(endpoint.value);
    }
    if (payloadJson.present) {
      map['payload_json'] = Variable<String>(payloadJson.value);
    }
    if (deviceSignature.present) {
      map['device_signature'] = Variable<String>(deviceSignature.value);
    }
    if (offlineSeqNo.present) {
      map['offline_seq_no'] = Variable<BigInt>(offlineSeqNo.value);
    }
    if (bufferedAt.present) {
      map['buffered_at'] = Variable<DateTime>(bufferedAt.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (attemptCount.present) {
      map['attempt_count'] = Variable<int>(attemptCount.value);
    }
    if (nextAttemptAt.present) {
      map['next_attempt_at'] = Variable<DateTime>(nextAttemptAt.value);
    }
    if (syncState.present) {
      map['sync_state'] = Variable<String>(syncState.value);
    }
    if (lastError.present) {
      map['last_error'] = Variable<String>(lastError.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('OutboxOperationsCompanion(')
          ..write('operationId: $operationId, ')
          ..write('idempotencyKey: $idempotencyKey, ')
          ..write('tenantId: $tenantId, ')
          ..write('branchId: $branchId, ')
          ..write('operationType: $operationType, ')
          ..write('endpoint: $endpoint, ')
          ..write('payloadJson: $payloadJson, ')
          ..write('deviceSignature: $deviceSignature, ')
          ..write('offlineSeqNo: $offlineSeqNo, ')
          ..write('bufferedAt: $bufferedAt, ')
          ..write('createdAt: $createdAt, ')
          ..write('attemptCount: $attemptCount, ')
          ..write('nextAttemptAt: $nextAttemptAt, ')
          ..write('syncState: $syncState, ')
          ..write('lastError: $lastError, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $LocalCustomersTable extends LocalCustomers
    with TableInfo<$LocalCustomersTable, LocalCustomerRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LocalCustomersTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tenantIdMeta = const VerificationMeta(
    'tenantId',
  );
  @override
  late final GeneratedColumn<String> tenantId = GeneratedColumn<String>(
    'tenant_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _branchIdMeta = const VerificationMeta(
    'branchId',
  );
  @override
  late final GeneratedColumn<String> branchId = GeneratedColumn<String>(
    'branch_id',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _tinMeta = const VerificationMeta('tin');
  @override
  late final GeneratedColumn<String> tin = GeneratedColumn<String>(
    'tin',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _vatNumberMeta = const VerificationMeta(
    'vatNumber',
  );
  @override
  late final GeneratedColumn<String> vatNumber = GeneratedColumn<String>(
    'vat_number',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _legalNameMeta = const VerificationMeta(
    'legalName',
  );
  @override
  late final GeneratedColumn<String> legalName = GeneratedColumn<String>(
    'legal_name',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tradeNameMeta = const VerificationMeta(
    'tradeName',
  );
  @override
  late final GeneratedColumn<String> tradeName = GeneratedColumn<String>(
    'trade_name',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _phoneMeta = const VerificationMeta('phone');
  @override
  late final GeneratedColumn<String> phone = GeneratedColumn<String>(
    'phone',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _emailMeta = const VerificationMeta('email');
  @override
  late final GeneratedColumn<String> email = GeneratedColumn<String>(
    'email',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _countryMeta = const VerificationMeta(
    'country',
  );
  @override
  late final GeneratedColumn<String> country = GeneratedColumn<String>(
    'country',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('ET'),
  );
  static const VerificationMeta _regionMeta = const VerificationMeta('region');
  @override
  late final GeneratedColumn<String> region = GeneratedColumn<String>(
    'region',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _cityMeta = const VerificationMeta('city');
  @override
  late final GeneratedColumn<String> city = GeneratedColumn<String>(
    'city',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _zoneMeta = const VerificationMeta('zone');
  @override
  late final GeneratedColumn<String> zone = GeneratedColumn<String>(
    'zone',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _woredaMeta = const VerificationMeta('woreda');
  @override
  late final GeneratedColumn<String> woreda = GeneratedColumn<String>(
    'woreda',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _kebeleMeta = const VerificationMeta('kebele');
  @override
  late final GeneratedColumn<String> kebele = GeneratedColumn<String>(
    'kebele',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _houseNumberMeta = const VerificationMeta(
    'houseNumber',
  );
  @override
  late final GeneratedColumn<String> houseNumber = GeneratedColumn<String>(
    'house_number',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _buyerIdTypeMeta = const VerificationMeta(
    'buyerIdType',
  );
  @override
  late final GeneratedColumn<String> buyerIdType = GeneratedColumn<String>(
    'buyer_id_type',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('TIN'),
  );
  static const VerificationMeta _buyerIdNumberMeta = const VerificationMeta(
    'buyerIdNumber',
  );
  @override
  late final GeneratedColumn<String> buyerIdNumber = GeneratedColumn<String>(
    'buyer_id_number',
    aliasedName,
    true,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
  );
  static const VerificationMeta _isVatRegisteredMeta = const VerificationMeta(
    'isVatRegistered',
  );
  @override
  late final GeneratedColumn<bool> isVatRegistered = GeneratedColumn<bool>(
    'is_vat_registered',
    aliasedName,
    false,
    type: DriftSqlType.bool,
    requiredDuringInsert: false,
    defaultConstraints: GeneratedColumn.constraintIsAlways(
      'CHECK ("is_vat_registered" IN (0, 1))',
    ),
    defaultValue: const Constant(false),
  );
  static const VerificationMeta _statusMeta = const VerificationMeta('status');
  @override
  late final GeneratedColumn<String> status = GeneratedColumn<String>(
    'status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('ACTIVE'),
  );
  static const VerificationMeta _syncStatusMeta = const VerificationMeta(
    'syncStatus',
  );
  @override
  late final GeneratedColumn<String> syncStatus = GeneratedColumn<String>(
    'sync_status',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('synced'),
  );
  static const VerificationMeta _createdAtMeta = const VerificationMeta(
    'createdAt',
  );
  @override
  late final GeneratedColumn<DateTime> createdAt = GeneratedColumn<DateTime>(
    'created_at',
    aliasedName,
    false,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _updatedAtMeta = const VerificationMeta(
    'updatedAt',
  );
  @override
  late final GeneratedColumn<DateTime> updatedAt = GeneratedColumn<DateTime>(
    'updated_at',
    aliasedName,
    true,
    type: DriftSqlType.dateTime,
    requiredDuringInsert: false,
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    tenantId,
    branchId,
    tin,
    vatNumber,
    legalName,
    tradeName,
    phone,
    email,
    country,
    region,
    city,
    zone,
    woreda,
    kebele,
    houseNumber,
    buyerIdType,
    buyerIdNumber,
    isVatRegistered,
    status,
    syncStatus,
    createdAt,
    updatedAt,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'local_customers';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalCustomerRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('tenant_id')) {
      context.handle(
        _tenantIdMeta,
        tenantId.isAcceptableOrUnknown(data['tenant_id']!, _tenantIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tenantIdMeta);
    }
    if (data.containsKey('branch_id')) {
      context.handle(
        _branchIdMeta,
        branchId.isAcceptableOrUnknown(data['branch_id']!, _branchIdMeta),
      );
    }
    if (data.containsKey('tin')) {
      context.handle(
        _tinMeta,
        tin.isAcceptableOrUnknown(data['tin']!, _tinMeta),
      );
    }
    if (data.containsKey('vat_number')) {
      context.handle(
        _vatNumberMeta,
        vatNumber.isAcceptableOrUnknown(data['vat_number']!, _vatNumberMeta),
      );
    }
    if (data.containsKey('legal_name')) {
      context.handle(
        _legalNameMeta,
        legalName.isAcceptableOrUnknown(data['legal_name']!, _legalNameMeta),
      );
    } else if (isInserting) {
      context.missing(_legalNameMeta);
    }
    if (data.containsKey('trade_name')) {
      context.handle(
        _tradeNameMeta,
        tradeName.isAcceptableOrUnknown(data['trade_name']!, _tradeNameMeta),
      );
    }
    if (data.containsKey('phone')) {
      context.handle(
        _phoneMeta,
        phone.isAcceptableOrUnknown(data['phone']!, _phoneMeta),
      );
    }
    if (data.containsKey('email')) {
      context.handle(
        _emailMeta,
        email.isAcceptableOrUnknown(data['email']!, _emailMeta),
      );
    }
    if (data.containsKey('country')) {
      context.handle(
        _countryMeta,
        country.isAcceptableOrUnknown(data['country']!, _countryMeta),
      );
    }
    if (data.containsKey('region')) {
      context.handle(
        _regionMeta,
        region.isAcceptableOrUnknown(data['region']!, _regionMeta),
      );
    }
    if (data.containsKey('city')) {
      context.handle(
        _cityMeta,
        city.isAcceptableOrUnknown(data['city']!, _cityMeta),
      );
    }
    if (data.containsKey('zone')) {
      context.handle(
        _zoneMeta,
        zone.isAcceptableOrUnknown(data['zone']!, _zoneMeta),
      );
    }
    if (data.containsKey('woreda')) {
      context.handle(
        _woredaMeta,
        woreda.isAcceptableOrUnknown(data['woreda']!, _woredaMeta),
      );
    }
    if (data.containsKey('kebele')) {
      context.handle(
        _kebeleMeta,
        kebele.isAcceptableOrUnknown(data['kebele']!, _kebeleMeta),
      );
    }
    if (data.containsKey('house_number')) {
      context.handle(
        _houseNumberMeta,
        houseNumber.isAcceptableOrUnknown(
          data['house_number']!,
          _houseNumberMeta,
        ),
      );
    }
    if (data.containsKey('buyer_id_type')) {
      context.handle(
        _buyerIdTypeMeta,
        buyerIdType.isAcceptableOrUnknown(
          data['buyer_id_type']!,
          _buyerIdTypeMeta,
        ),
      );
    }
    if (data.containsKey('buyer_id_number')) {
      context.handle(
        _buyerIdNumberMeta,
        buyerIdNumber.isAcceptableOrUnknown(
          data['buyer_id_number']!,
          _buyerIdNumberMeta,
        ),
      );
    }
    if (data.containsKey('is_vat_registered')) {
      context.handle(
        _isVatRegisteredMeta,
        isVatRegistered.isAcceptableOrUnknown(
          data['is_vat_registered']!,
          _isVatRegisteredMeta,
        ),
      );
    }
    if (data.containsKey('status')) {
      context.handle(
        _statusMeta,
        status.isAcceptableOrUnknown(data['status']!, _statusMeta),
      );
    }
    if (data.containsKey('sync_status')) {
      context.handle(
        _syncStatusMeta,
        syncStatus.isAcceptableOrUnknown(data['sync_status']!, _syncStatusMeta),
      );
    }
    if (data.containsKey('created_at')) {
      context.handle(
        _createdAtMeta,
        createdAt.isAcceptableOrUnknown(data['created_at']!, _createdAtMeta),
      );
    } else if (isInserting) {
      context.missing(_createdAtMeta);
    }
    if (data.containsKey('updated_at')) {
      context.handle(
        _updatedAtMeta,
        updatedAt.isAcceptableOrUnknown(data['updated_at']!, _updatedAtMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalCustomerRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalCustomerRecord(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      branchId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}branch_id'],
      ),
      tin: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tin'],
      ),
      vatNumber: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}vat_number'],
      ),
      legalName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}legal_name'],
      )!,
      tradeName: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}trade_name'],
      ),
      phone: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}phone'],
      ),
      email: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}email'],
      ),
      country: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}country'],
      )!,
      region: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}region'],
      ),
      city: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}city'],
      ),
      zone: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}zone'],
      ),
      woreda: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}woreda'],
      ),
      kebele: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}kebele'],
      ),
      houseNumber: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}house_number'],
      ),
      buyerIdType: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}buyer_id_type'],
      )!,
      buyerIdNumber: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}buyer_id_number'],
      ),
      isVatRegistered: attachedDatabase.typeMapping.read(
        DriftSqlType.bool,
        data['${effectivePrefix}is_vat_registered'],
      )!,
      status: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}status'],
      )!,
      syncStatus: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}sync_status'],
      )!,
      createdAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}created_at'],
      )!,
      updatedAt: attachedDatabase.typeMapping.read(
        DriftSqlType.dateTime,
        data['${effectivePrefix}updated_at'],
      ),
    );
  }

  @override
  $LocalCustomersTable createAlias(String alias) {
    return $LocalCustomersTable(attachedDatabase, alias);
  }
}

class LocalCustomerRecord extends DataClass
    implements Insertable<LocalCustomerRecord> {
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
  final String buyerIdType;
  final String? buyerIdNumber;
  final bool isVatRegistered;
  final String status;
  final String syncStatus;
  final DateTime createdAt;
  final DateTime? updatedAt;
  const LocalCustomerRecord({
    required this.id,
    required this.tenantId,
    this.branchId,
    this.tin,
    this.vatNumber,
    required this.legalName,
    this.tradeName,
    this.phone,
    this.email,
    required this.country,
    this.region,
    this.city,
    this.zone,
    this.woreda,
    this.kebele,
    this.houseNumber,
    required this.buyerIdType,
    this.buyerIdNumber,
    required this.isVatRegistered,
    required this.status,
    required this.syncStatus,
    required this.createdAt,
    this.updatedAt,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['tenant_id'] = Variable<String>(tenantId);
    if (!nullToAbsent || branchId != null) {
      map['branch_id'] = Variable<String>(branchId);
    }
    if (!nullToAbsent || tin != null) {
      map['tin'] = Variable<String>(tin);
    }
    if (!nullToAbsent || vatNumber != null) {
      map['vat_number'] = Variable<String>(vatNumber);
    }
    map['legal_name'] = Variable<String>(legalName);
    if (!nullToAbsent || tradeName != null) {
      map['trade_name'] = Variable<String>(tradeName);
    }
    if (!nullToAbsent || phone != null) {
      map['phone'] = Variable<String>(phone);
    }
    if (!nullToAbsent || email != null) {
      map['email'] = Variable<String>(email);
    }
    map['country'] = Variable<String>(country);
    if (!nullToAbsent || region != null) {
      map['region'] = Variable<String>(region);
    }
    if (!nullToAbsent || city != null) {
      map['city'] = Variable<String>(city);
    }
    if (!nullToAbsent || zone != null) {
      map['zone'] = Variable<String>(zone);
    }
    if (!nullToAbsent || woreda != null) {
      map['woreda'] = Variable<String>(woreda);
    }
    if (!nullToAbsent || kebele != null) {
      map['kebele'] = Variable<String>(kebele);
    }
    if (!nullToAbsent || houseNumber != null) {
      map['house_number'] = Variable<String>(houseNumber);
    }
    map['buyer_id_type'] = Variable<String>(buyerIdType);
    if (!nullToAbsent || buyerIdNumber != null) {
      map['buyer_id_number'] = Variable<String>(buyerIdNumber);
    }
    map['is_vat_registered'] = Variable<bool>(isVatRegistered);
    map['status'] = Variable<String>(status);
    map['sync_status'] = Variable<String>(syncStatus);
    map['created_at'] = Variable<DateTime>(createdAt);
    if (!nullToAbsent || updatedAt != null) {
      map['updated_at'] = Variable<DateTime>(updatedAt);
    }
    return map;
  }

  LocalCustomersCompanion toCompanion(bool nullToAbsent) {
    return LocalCustomersCompanion(
      id: Value(id),
      tenantId: Value(tenantId),
      branchId: branchId == null && nullToAbsent
          ? const Value.absent()
          : Value(branchId),
      tin: tin == null && nullToAbsent ? const Value.absent() : Value(tin),
      vatNumber: vatNumber == null && nullToAbsent
          ? const Value.absent()
          : Value(vatNumber),
      legalName: Value(legalName),
      tradeName: tradeName == null && nullToAbsent
          ? const Value.absent()
          : Value(tradeName),
      phone: phone == null && nullToAbsent
          ? const Value.absent()
          : Value(phone),
      email: email == null && nullToAbsent
          ? const Value.absent()
          : Value(email),
      country: Value(country),
      region: region == null && nullToAbsent
          ? const Value.absent()
          : Value(region),
      city: city == null && nullToAbsent ? const Value.absent() : Value(city),
      zone: zone == null && nullToAbsent ? const Value.absent() : Value(zone),
      woreda: woreda == null && nullToAbsent
          ? const Value.absent()
          : Value(woreda),
      kebele: kebele == null && nullToAbsent
          ? const Value.absent()
          : Value(kebele),
      houseNumber: houseNumber == null && nullToAbsent
          ? const Value.absent()
          : Value(houseNumber),
      buyerIdType: Value(buyerIdType),
      buyerIdNumber: buyerIdNumber == null && nullToAbsent
          ? const Value.absent()
          : Value(buyerIdNumber),
      isVatRegistered: Value(isVatRegistered),
      status: Value(status),
      syncStatus: Value(syncStatus),
      createdAt: Value(createdAt),
      updatedAt: updatedAt == null && nullToAbsent
          ? const Value.absent()
          : Value(updatedAt),
    );
  }

  factory LocalCustomerRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalCustomerRecord(
      id: serializer.fromJson<String>(json['id']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      branchId: serializer.fromJson<String?>(json['branchId']),
      tin: serializer.fromJson<String?>(json['tin']),
      vatNumber: serializer.fromJson<String?>(json['vatNumber']),
      legalName: serializer.fromJson<String>(json['legalName']),
      tradeName: serializer.fromJson<String?>(json['tradeName']),
      phone: serializer.fromJson<String?>(json['phone']),
      email: serializer.fromJson<String?>(json['email']),
      country: serializer.fromJson<String>(json['country']),
      region: serializer.fromJson<String?>(json['region']),
      city: serializer.fromJson<String?>(json['city']),
      zone: serializer.fromJson<String?>(json['zone']),
      woreda: serializer.fromJson<String?>(json['woreda']),
      kebele: serializer.fromJson<String?>(json['kebele']),
      houseNumber: serializer.fromJson<String?>(json['houseNumber']),
      buyerIdType: serializer.fromJson<String>(json['buyerIdType']),
      buyerIdNumber: serializer.fromJson<String?>(json['buyerIdNumber']),
      isVatRegistered: serializer.fromJson<bool>(json['isVatRegistered']),
      status: serializer.fromJson<String>(json['status']),
      syncStatus: serializer.fromJson<String>(json['syncStatus']),
      createdAt: serializer.fromJson<DateTime>(json['createdAt']),
      updatedAt: serializer.fromJson<DateTime?>(json['updatedAt']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'tenantId': serializer.toJson<String>(tenantId),
      'branchId': serializer.toJson<String?>(branchId),
      'tin': serializer.toJson<String?>(tin),
      'vatNumber': serializer.toJson<String?>(vatNumber),
      'legalName': serializer.toJson<String>(legalName),
      'tradeName': serializer.toJson<String?>(tradeName),
      'phone': serializer.toJson<String?>(phone),
      'email': serializer.toJson<String?>(email),
      'country': serializer.toJson<String>(country),
      'region': serializer.toJson<String?>(region),
      'city': serializer.toJson<String?>(city),
      'zone': serializer.toJson<String?>(zone),
      'woreda': serializer.toJson<String?>(woreda),
      'kebele': serializer.toJson<String?>(kebele),
      'houseNumber': serializer.toJson<String?>(houseNumber),
      'buyerIdType': serializer.toJson<String>(buyerIdType),
      'buyerIdNumber': serializer.toJson<String?>(buyerIdNumber),
      'isVatRegistered': serializer.toJson<bool>(isVatRegistered),
      'status': serializer.toJson<String>(status),
      'syncStatus': serializer.toJson<String>(syncStatus),
      'createdAt': serializer.toJson<DateTime>(createdAt),
      'updatedAt': serializer.toJson<DateTime?>(updatedAt),
    };
  }

  LocalCustomerRecord copyWith({
    String? id,
    String? tenantId,
    Value<String?> branchId = const Value.absent(),
    Value<String?> tin = const Value.absent(),
    Value<String?> vatNumber = const Value.absent(),
    String? legalName,
    Value<String?> tradeName = const Value.absent(),
    Value<String?> phone = const Value.absent(),
    Value<String?> email = const Value.absent(),
    String? country,
    Value<String?> region = const Value.absent(),
    Value<String?> city = const Value.absent(),
    Value<String?> zone = const Value.absent(),
    Value<String?> woreda = const Value.absent(),
    Value<String?> kebele = const Value.absent(),
    Value<String?> houseNumber = const Value.absent(),
    String? buyerIdType,
    Value<String?> buyerIdNumber = const Value.absent(),
    bool? isVatRegistered,
    String? status,
    String? syncStatus,
    DateTime? createdAt,
    Value<DateTime?> updatedAt = const Value.absent(),
  }) => LocalCustomerRecord(
    id: id ?? this.id,
    tenantId: tenantId ?? this.tenantId,
    branchId: branchId.present ? branchId.value : this.branchId,
    tin: tin.present ? tin.value : this.tin,
    vatNumber: vatNumber.present ? vatNumber.value : this.vatNumber,
    legalName: legalName ?? this.legalName,
    tradeName: tradeName.present ? tradeName.value : this.tradeName,
    phone: phone.present ? phone.value : this.phone,
    email: email.present ? email.value : this.email,
    country: country ?? this.country,
    region: region.present ? region.value : this.region,
    city: city.present ? city.value : this.city,
    zone: zone.present ? zone.value : this.zone,
    woreda: woreda.present ? woreda.value : this.woreda,
    kebele: kebele.present ? kebele.value : this.kebele,
    houseNumber: houseNumber.present ? houseNumber.value : this.houseNumber,
    buyerIdType: buyerIdType ?? this.buyerIdType,
    buyerIdNumber: buyerIdNumber.present
        ? buyerIdNumber.value
        : this.buyerIdNumber,
    isVatRegistered: isVatRegistered ?? this.isVatRegistered,
    status: status ?? this.status,
    syncStatus: syncStatus ?? this.syncStatus,
    createdAt: createdAt ?? this.createdAt,
    updatedAt: updatedAt.present ? updatedAt.value : this.updatedAt,
  );
  LocalCustomerRecord copyWithCompanion(LocalCustomersCompanion data) {
    return LocalCustomerRecord(
      id: data.id.present ? data.id.value : this.id,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      branchId: data.branchId.present ? data.branchId.value : this.branchId,
      tin: data.tin.present ? data.tin.value : this.tin,
      vatNumber: data.vatNumber.present ? data.vatNumber.value : this.vatNumber,
      legalName: data.legalName.present ? data.legalName.value : this.legalName,
      tradeName: data.tradeName.present ? data.tradeName.value : this.tradeName,
      phone: data.phone.present ? data.phone.value : this.phone,
      email: data.email.present ? data.email.value : this.email,
      country: data.country.present ? data.country.value : this.country,
      region: data.region.present ? data.region.value : this.region,
      city: data.city.present ? data.city.value : this.city,
      zone: data.zone.present ? data.zone.value : this.zone,
      woreda: data.woreda.present ? data.woreda.value : this.woreda,
      kebele: data.kebele.present ? data.kebele.value : this.kebele,
      houseNumber: data.houseNumber.present
          ? data.houseNumber.value
          : this.houseNumber,
      buyerIdType: data.buyerIdType.present
          ? data.buyerIdType.value
          : this.buyerIdType,
      buyerIdNumber: data.buyerIdNumber.present
          ? data.buyerIdNumber.value
          : this.buyerIdNumber,
      isVatRegistered: data.isVatRegistered.present
          ? data.isVatRegistered.value
          : this.isVatRegistered,
      status: data.status.present ? data.status.value : this.status,
      syncStatus: data.syncStatus.present
          ? data.syncStatus.value
          : this.syncStatus,
      createdAt: data.createdAt.present ? data.createdAt.value : this.createdAt,
      updatedAt: data.updatedAt.present ? data.updatedAt.value : this.updatedAt,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalCustomerRecord(')
          ..write('id: $id, ')
          ..write('tenantId: $tenantId, ')
          ..write('branchId: $branchId, ')
          ..write('tin: $tin, ')
          ..write('vatNumber: $vatNumber, ')
          ..write('legalName: $legalName, ')
          ..write('tradeName: $tradeName, ')
          ..write('phone: $phone, ')
          ..write('email: $email, ')
          ..write('country: $country, ')
          ..write('region: $region, ')
          ..write('city: $city, ')
          ..write('zone: $zone, ')
          ..write('woreda: $woreda, ')
          ..write('kebele: $kebele, ')
          ..write('houseNumber: $houseNumber, ')
          ..write('buyerIdType: $buyerIdType, ')
          ..write('buyerIdNumber: $buyerIdNumber, ')
          ..write('isVatRegistered: $isVatRegistered, ')
          ..write('status: $status, ')
          ..write('syncStatus: $syncStatus, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hashAll([
    id,
    tenantId,
    branchId,
    tin,
    vatNumber,
    legalName,
    tradeName,
    phone,
    email,
    country,
    region,
    city,
    zone,
    woreda,
    kebele,
    houseNumber,
    buyerIdType,
    buyerIdNumber,
    isVatRegistered,
    status,
    syncStatus,
    createdAt,
    updatedAt,
  ]);
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalCustomerRecord &&
          other.id == this.id &&
          other.tenantId == this.tenantId &&
          other.branchId == this.branchId &&
          other.tin == this.tin &&
          other.vatNumber == this.vatNumber &&
          other.legalName == this.legalName &&
          other.tradeName == this.tradeName &&
          other.phone == this.phone &&
          other.email == this.email &&
          other.country == this.country &&
          other.region == this.region &&
          other.city == this.city &&
          other.zone == this.zone &&
          other.woreda == this.woreda &&
          other.kebele == this.kebele &&
          other.houseNumber == this.houseNumber &&
          other.buyerIdType == this.buyerIdType &&
          other.buyerIdNumber == this.buyerIdNumber &&
          other.isVatRegistered == this.isVatRegistered &&
          other.status == this.status &&
          other.syncStatus == this.syncStatus &&
          other.createdAt == this.createdAt &&
          other.updatedAt == this.updatedAt);
}

class LocalCustomersCompanion extends UpdateCompanion<LocalCustomerRecord> {
  final Value<String> id;
  final Value<String> tenantId;
  final Value<String?> branchId;
  final Value<String?> tin;
  final Value<String?> vatNumber;
  final Value<String> legalName;
  final Value<String?> tradeName;
  final Value<String?> phone;
  final Value<String?> email;
  final Value<String> country;
  final Value<String?> region;
  final Value<String?> city;
  final Value<String?> zone;
  final Value<String?> woreda;
  final Value<String?> kebele;
  final Value<String?> houseNumber;
  final Value<String> buyerIdType;
  final Value<String?> buyerIdNumber;
  final Value<bool> isVatRegistered;
  final Value<String> status;
  final Value<String> syncStatus;
  final Value<DateTime> createdAt;
  final Value<DateTime?> updatedAt;
  final Value<int> rowid;
  const LocalCustomersCompanion({
    this.id = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.branchId = const Value.absent(),
    this.tin = const Value.absent(),
    this.vatNumber = const Value.absent(),
    this.legalName = const Value.absent(),
    this.tradeName = const Value.absent(),
    this.phone = const Value.absent(),
    this.email = const Value.absent(),
    this.country = const Value.absent(),
    this.region = const Value.absent(),
    this.city = const Value.absent(),
    this.zone = const Value.absent(),
    this.woreda = const Value.absent(),
    this.kebele = const Value.absent(),
    this.houseNumber = const Value.absent(),
    this.buyerIdType = const Value.absent(),
    this.buyerIdNumber = const Value.absent(),
    this.isVatRegistered = const Value.absent(),
    this.status = const Value.absent(),
    this.syncStatus = const Value.absent(),
    this.createdAt = const Value.absent(),
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  LocalCustomersCompanion.insert({
    required String id,
    required String tenantId,
    this.branchId = const Value.absent(),
    this.tin = const Value.absent(),
    this.vatNumber = const Value.absent(),
    required String legalName,
    this.tradeName = const Value.absent(),
    this.phone = const Value.absent(),
    this.email = const Value.absent(),
    this.country = const Value.absent(),
    this.region = const Value.absent(),
    this.city = const Value.absent(),
    this.zone = const Value.absent(),
    this.woreda = const Value.absent(),
    this.kebele = const Value.absent(),
    this.houseNumber = const Value.absent(),
    this.buyerIdType = const Value.absent(),
    this.buyerIdNumber = const Value.absent(),
    this.isVatRegistered = const Value.absent(),
    this.status = const Value.absent(),
    this.syncStatus = const Value.absent(),
    required DateTime createdAt,
    this.updatedAt = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       tenantId = Value(tenantId),
       legalName = Value(legalName),
       createdAt = Value(createdAt);
  static Insertable<LocalCustomerRecord> custom({
    Expression<String>? id,
    Expression<String>? tenantId,
    Expression<String>? branchId,
    Expression<String>? tin,
    Expression<String>? vatNumber,
    Expression<String>? legalName,
    Expression<String>? tradeName,
    Expression<String>? phone,
    Expression<String>? email,
    Expression<String>? country,
    Expression<String>? region,
    Expression<String>? city,
    Expression<String>? zone,
    Expression<String>? woreda,
    Expression<String>? kebele,
    Expression<String>? houseNumber,
    Expression<String>? buyerIdType,
    Expression<String>? buyerIdNumber,
    Expression<bool>? isVatRegistered,
    Expression<String>? status,
    Expression<String>? syncStatus,
    Expression<DateTime>? createdAt,
    Expression<DateTime>? updatedAt,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (tenantId != null) 'tenant_id': tenantId,
      if (branchId != null) 'branch_id': branchId,
      if (tin != null) 'tin': tin,
      if (vatNumber != null) 'vat_number': vatNumber,
      if (legalName != null) 'legal_name': legalName,
      if (tradeName != null) 'trade_name': tradeName,
      if (phone != null) 'phone': phone,
      if (email != null) 'email': email,
      if (country != null) 'country': country,
      if (region != null) 'region': region,
      if (city != null) 'city': city,
      if (zone != null) 'zone': zone,
      if (woreda != null) 'woreda': woreda,
      if (kebele != null) 'kebele': kebele,
      if (houseNumber != null) 'house_number': houseNumber,
      if (buyerIdType != null) 'buyer_id_type': buyerIdType,
      if (buyerIdNumber != null) 'buyer_id_number': buyerIdNumber,
      if (isVatRegistered != null) 'is_vat_registered': isVatRegistered,
      if (status != null) 'status': status,
      if (syncStatus != null) 'sync_status': syncStatus,
      if (createdAt != null) 'created_at': createdAt,
      if (updatedAt != null) 'updated_at': updatedAt,
      if (rowid != null) 'rowid': rowid,
    });
  }

  LocalCustomersCompanion copyWith({
    Value<String>? id,
    Value<String>? tenantId,
    Value<String?>? branchId,
    Value<String?>? tin,
    Value<String?>? vatNumber,
    Value<String>? legalName,
    Value<String?>? tradeName,
    Value<String?>? phone,
    Value<String?>? email,
    Value<String>? country,
    Value<String?>? region,
    Value<String?>? city,
    Value<String?>? zone,
    Value<String?>? woreda,
    Value<String?>? kebele,
    Value<String?>? houseNumber,
    Value<String>? buyerIdType,
    Value<String?>? buyerIdNumber,
    Value<bool>? isVatRegistered,
    Value<String>? status,
    Value<String>? syncStatus,
    Value<DateTime>? createdAt,
    Value<DateTime?>? updatedAt,
    Value<int>? rowid,
  }) {
    return LocalCustomersCompanion(
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
      syncStatus: syncStatus ?? this.syncStatus,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (branchId.present) {
      map['branch_id'] = Variable<String>(branchId.value);
    }
    if (tin.present) {
      map['tin'] = Variable<String>(tin.value);
    }
    if (vatNumber.present) {
      map['vat_number'] = Variable<String>(vatNumber.value);
    }
    if (legalName.present) {
      map['legal_name'] = Variable<String>(legalName.value);
    }
    if (tradeName.present) {
      map['trade_name'] = Variable<String>(tradeName.value);
    }
    if (phone.present) {
      map['phone'] = Variable<String>(phone.value);
    }
    if (email.present) {
      map['email'] = Variable<String>(email.value);
    }
    if (country.present) {
      map['country'] = Variable<String>(country.value);
    }
    if (region.present) {
      map['region'] = Variable<String>(region.value);
    }
    if (city.present) {
      map['city'] = Variable<String>(city.value);
    }
    if (zone.present) {
      map['zone'] = Variable<String>(zone.value);
    }
    if (woreda.present) {
      map['woreda'] = Variable<String>(woreda.value);
    }
    if (kebele.present) {
      map['kebele'] = Variable<String>(kebele.value);
    }
    if (houseNumber.present) {
      map['house_number'] = Variable<String>(houseNumber.value);
    }
    if (buyerIdType.present) {
      map['buyer_id_type'] = Variable<String>(buyerIdType.value);
    }
    if (buyerIdNumber.present) {
      map['buyer_id_number'] = Variable<String>(buyerIdNumber.value);
    }
    if (isVatRegistered.present) {
      map['is_vat_registered'] = Variable<bool>(isVatRegistered.value);
    }
    if (status.present) {
      map['status'] = Variable<String>(status.value);
    }
    if (syncStatus.present) {
      map['sync_status'] = Variable<String>(syncStatus.value);
    }
    if (createdAt.present) {
      map['created_at'] = Variable<DateTime>(createdAt.value);
    }
    if (updatedAt.present) {
      map['updated_at'] = Variable<DateTime>(updatedAt.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LocalCustomersCompanion(')
          ..write('id: $id, ')
          ..write('tenantId: $tenantId, ')
          ..write('branchId: $branchId, ')
          ..write('tin: $tin, ')
          ..write('vatNumber: $vatNumber, ')
          ..write('legalName: $legalName, ')
          ..write('tradeName: $tradeName, ')
          ..write('phone: $phone, ')
          ..write('email: $email, ')
          ..write('country: $country, ')
          ..write('region: $region, ')
          ..write('city: $city, ')
          ..write('zone: $zone, ')
          ..write('woreda: $woreda, ')
          ..write('kebele: $kebele, ')
          ..write('houseNumber: $houseNumber, ')
          ..write('buyerIdType: $buyerIdType, ')
          ..write('buyerIdNumber: $buyerIdNumber, ')
          ..write('isVatRegistered: $isVatRegistered, ')
          ..write('status: $status, ')
          ..write('syncStatus: $syncStatus, ')
          ..write('createdAt: $createdAt, ')
          ..write('updatedAt: $updatedAt, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

class $LocalProductsTable extends LocalProducts
    with TableInfo<$LocalProductsTable, LocalProductRecord> {
  @override
  final GeneratedDatabase attachedDatabase;
  final String? _alias;
  $LocalProductsTable(this.attachedDatabase, [this._alias]);
  static const VerificationMeta _idMeta = const VerificationMeta('id');
  @override
  late final GeneratedColumn<String> id = GeneratedColumn<String>(
    'id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _tenantIdMeta = const VerificationMeta(
    'tenantId',
  );
  @override
  late final GeneratedColumn<String> tenantId = GeneratedColumn<String>(
    'tenant_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _branchIdMeta = const VerificationMeta(
    'branchId',
  );
  @override
  late final GeneratedColumn<String> branchId = GeneratedColumn<String>(
    'branch_id',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _itemCodeMeta = const VerificationMeta(
    'itemCode',
  );
  @override
  late final GeneratedColumn<String> itemCode = GeneratedColumn<String>(
    'item_code',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _descriptionMeta = const VerificationMeta(
    'description',
  );
  @override
  late final GeneratedColumn<String> description = GeneratedColumn<String>(
    'description',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _unitPriceMeta = const VerificationMeta(
    'unitPrice',
  );
  @override
  late final GeneratedColumn<double> unitPrice = GeneratedColumn<double>(
    'unit_price',
    aliasedName,
    false,
    type: DriftSqlType.double,
    requiredDuringInsert: true,
  );
  static const VerificationMeta _taxCodeMeta = const VerificationMeta(
    'taxCode',
  );
  @override
  late final GeneratedColumn<String> taxCode = GeneratedColumn<String>(
    'tax_code',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('VAT15'),
  );
  static const VerificationMeta _unitMeta = const VerificationMeta('unit');
  @override
  late final GeneratedColumn<String> unit = GeneratedColumn<String>(
    'unit',
    aliasedName,
    false,
    type: DriftSqlType.string,
    requiredDuringInsert: false,
    defaultValue: const Constant('PCS'),
  );
  @override
  List<GeneratedColumn> get $columns => [
    id,
    tenantId,
    branchId,
    itemCode,
    description,
    unitPrice,
    taxCode,
    unit,
  ];
  @override
  String get aliasedName => _alias ?? actualTableName;
  @override
  String get actualTableName => $name;
  static const String $name = 'local_products';
  @override
  VerificationContext validateIntegrity(
    Insertable<LocalProductRecord> instance, {
    bool isInserting = false,
  }) {
    final context = VerificationContext();
    final data = instance.toColumns(true);
    if (data.containsKey('id')) {
      context.handle(_idMeta, id.isAcceptableOrUnknown(data['id']!, _idMeta));
    } else if (isInserting) {
      context.missing(_idMeta);
    }
    if (data.containsKey('tenant_id')) {
      context.handle(
        _tenantIdMeta,
        tenantId.isAcceptableOrUnknown(data['tenant_id']!, _tenantIdMeta),
      );
    } else if (isInserting) {
      context.missing(_tenantIdMeta);
    }
    if (data.containsKey('branch_id')) {
      context.handle(
        _branchIdMeta,
        branchId.isAcceptableOrUnknown(data['branch_id']!, _branchIdMeta),
      );
    } else if (isInserting) {
      context.missing(_branchIdMeta);
    }
    if (data.containsKey('item_code')) {
      context.handle(
        _itemCodeMeta,
        itemCode.isAcceptableOrUnknown(data['item_code']!, _itemCodeMeta),
      );
    } else if (isInserting) {
      context.missing(_itemCodeMeta);
    }
    if (data.containsKey('description')) {
      context.handle(
        _descriptionMeta,
        description.isAcceptableOrUnknown(
          data['description']!,
          _descriptionMeta,
        ),
      );
    } else if (isInserting) {
      context.missing(_descriptionMeta);
    }
    if (data.containsKey('unit_price')) {
      context.handle(
        _unitPriceMeta,
        unitPrice.isAcceptableOrUnknown(data['unit_price']!, _unitPriceMeta),
      );
    } else if (isInserting) {
      context.missing(_unitPriceMeta);
    }
    if (data.containsKey('tax_code')) {
      context.handle(
        _taxCodeMeta,
        taxCode.isAcceptableOrUnknown(data['tax_code']!, _taxCodeMeta),
      );
    }
    if (data.containsKey('unit')) {
      context.handle(
        _unitMeta,
        unit.isAcceptableOrUnknown(data['unit']!, _unitMeta),
      );
    }
    return context;
  }

  @override
  Set<GeneratedColumn> get $primaryKey => {id};
  @override
  LocalProductRecord map(Map<String, dynamic> data, {String? tablePrefix}) {
    final effectivePrefix = tablePrefix != null ? '$tablePrefix.' : '';
    return LocalProductRecord(
      id: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}id'],
      )!,
      tenantId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tenant_id'],
      )!,
      branchId: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}branch_id'],
      )!,
      itemCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}item_code'],
      )!,
      description: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}description'],
      )!,
      unitPrice: attachedDatabase.typeMapping.read(
        DriftSqlType.double,
        data['${effectivePrefix}unit_price'],
      )!,
      taxCode: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}tax_code'],
      )!,
      unit: attachedDatabase.typeMapping.read(
        DriftSqlType.string,
        data['${effectivePrefix}unit'],
      )!,
    );
  }

  @override
  $LocalProductsTable createAlias(String alias) {
    return $LocalProductsTable(attachedDatabase, alias);
  }
}

class LocalProductRecord extends DataClass
    implements Insertable<LocalProductRecord> {
  final String id;
  final String tenantId;
  final String branchId;
  final String itemCode;
  final String description;
  final double unitPrice;
  final String taxCode;
  final String unit;
  const LocalProductRecord({
    required this.id,
    required this.tenantId,
    required this.branchId,
    required this.itemCode,
    required this.description,
    required this.unitPrice,
    required this.taxCode,
    required this.unit,
  });
  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    map['id'] = Variable<String>(id);
    map['tenant_id'] = Variable<String>(tenantId);
    map['branch_id'] = Variable<String>(branchId);
    map['item_code'] = Variable<String>(itemCode);
    map['description'] = Variable<String>(description);
    map['unit_price'] = Variable<double>(unitPrice);
    map['tax_code'] = Variable<String>(taxCode);
    map['unit'] = Variable<String>(unit);
    return map;
  }

  LocalProductsCompanion toCompanion(bool nullToAbsent) {
    return LocalProductsCompanion(
      id: Value(id),
      tenantId: Value(tenantId),
      branchId: Value(branchId),
      itemCode: Value(itemCode),
      description: Value(description),
      unitPrice: Value(unitPrice),
      taxCode: Value(taxCode),
      unit: Value(unit),
    );
  }

  factory LocalProductRecord.fromJson(
    Map<String, dynamic> json, {
    ValueSerializer? serializer,
  }) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return LocalProductRecord(
      id: serializer.fromJson<String>(json['id']),
      tenantId: serializer.fromJson<String>(json['tenantId']),
      branchId: serializer.fromJson<String>(json['branchId']),
      itemCode: serializer.fromJson<String>(json['itemCode']),
      description: serializer.fromJson<String>(json['description']),
      unitPrice: serializer.fromJson<double>(json['unitPrice']),
      taxCode: serializer.fromJson<String>(json['taxCode']),
      unit: serializer.fromJson<String>(json['unit']),
    );
  }
  @override
  Map<String, dynamic> toJson({ValueSerializer? serializer}) {
    serializer ??= driftRuntimeOptions.defaultSerializer;
    return <String, dynamic>{
      'id': serializer.toJson<String>(id),
      'tenantId': serializer.toJson<String>(tenantId),
      'branchId': serializer.toJson<String>(branchId),
      'itemCode': serializer.toJson<String>(itemCode),
      'description': serializer.toJson<String>(description),
      'unitPrice': serializer.toJson<double>(unitPrice),
      'taxCode': serializer.toJson<String>(taxCode),
      'unit': serializer.toJson<String>(unit),
    };
  }

  LocalProductRecord copyWith({
    String? id,
    String? tenantId,
    String? branchId,
    String? itemCode,
    String? description,
    double? unitPrice,
    String? taxCode,
    String? unit,
  }) => LocalProductRecord(
    id: id ?? this.id,
    tenantId: tenantId ?? this.tenantId,
    branchId: branchId ?? this.branchId,
    itemCode: itemCode ?? this.itemCode,
    description: description ?? this.description,
    unitPrice: unitPrice ?? this.unitPrice,
    taxCode: taxCode ?? this.taxCode,
    unit: unit ?? this.unit,
  );
  LocalProductRecord copyWithCompanion(LocalProductsCompanion data) {
    return LocalProductRecord(
      id: data.id.present ? data.id.value : this.id,
      tenantId: data.tenantId.present ? data.tenantId.value : this.tenantId,
      branchId: data.branchId.present ? data.branchId.value : this.branchId,
      itemCode: data.itemCode.present ? data.itemCode.value : this.itemCode,
      description: data.description.present
          ? data.description.value
          : this.description,
      unitPrice: data.unitPrice.present ? data.unitPrice.value : this.unitPrice,
      taxCode: data.taxCode.present ? data.taxCode.value : this.taxCode,
      unit: data.unit.present ? data.unit.value : this.unit,
    );
  }

  @override
  String toString() {
    return (StringBuffer('LocalProductRecord(')
          ..write('id: $id, ')
          ..write('tenantId: $tenantId, ')
          ..write('branchId: $branchId, ')
          ..write('itemCode: $itemCode, ')
          ..write('description: $description, ')
          ..write('unitPrice: $unitPrice, ')
          ..write('taxCode: $taxCode, ')
          ..write('unit: $unit')
          ..write(')'))
        .toString();
  }

  @override
  int get hashCode => Object.hash(
    id,
    tenantId,
    branchId,
    itemCode,
    description,
    unitPrice,
    taxCode,
    unit,
  );
  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is LocalProductRecord &&
          other.id == this.id &&
          other.tenantId == this.tenantId &&
          other.branchId == this.branchId &&
          other.itemCode == this.itemCode &&
          other.description == this.description &&
          other.unitPrice == this.unitPrice &&
          other.taxCode == this.taxCode &&
          other.unit == this.unit);
}

class LocalProductsCompanion extends UpdateCompanion<LocalProductRecord> {
  final Value<String> id;
  final Value<String> tenantId;
  final Value<String> branchId;
  final Value<String> itemCode;
  final Value<String> description;
  final Value<double> unitPrice;
  final Value<String> taxCode;
  final Value<String> unit;
  final Value<int> rowid;
  const LocalProductsCompanion({
    this.id = const Value.absent(),
    this.tenantId = const Value.absent(),
    this.branchId = const Value.absent(),
    this.itemCode = const Value.absent(),
    this.description = const Value.absent(),
    this.unitPrice = const Value.absent(),
    this.taxCode = const Value.absent(),
    this.unit = const Value.absent(),
    this.rowid = const Value.absent(),
  });
  LocalProductsCompanion.insert({
    required String id,
    required String tenantId,
    required String branchId,
    required String itemCode,
    required String description,
    required double unitPrice,
    this.taxCode = const Value.absent(),
    this.unit = const Value.absent(),
    this.rowid = const Value.absent(),
  }) : id = Value(id),
       tenantId = Value(tenantId),
       branchId = Value(branchId),
       itemCode = Value(itemCode),
       description = Value(description),
       unitPrice = Value(unitPrice);
  static Insertable<LocalProductRecord> custom({
    Expression<String>? id,
    Expression<String>? tenantId,
    Expression<String>? branchId,
    Expression<String>? itemCode,
    Expression<String>? description,
    Expression<double>? unitPrice,
    Expression<String>? taxCode,
    Expression<String>? unit,
    Expression<int>? rowid,
  }) {
    return RawValuesInsertable({
      if (id != null) 'id': id,
      if (tenantId != null) 'tenant_id': tenantId,
      if (branchId != null) 'branch_id': branchId,
      if (itemCode != null) 'item_code': itemCode,
      if (description != null) 'description': description,
      if (unitPrice != null) 'unit_price': unitPrice,
      if (taxCode != null) 'tax_code': taxCode,
      if (unit != null) 'unit': unit,
      if (rowid != null) 'rowid': rowid,
    });
  }

  LocalProductsCompanion copyWith({
    Value<String>? id,
    Value<String>? tenantId,
    Value<String>? branchId,
    Value<String>? itemCode,
    Value<String>? description,
    Value<double>? unitPrice,
    Value<String>? taxCode,
    Value<String>? unit,
    Value<int>? rowid,
  }) {
    return LocalProductsCompanion(
      id: id ?? this.id,
      tenantId: tenantId ?? this.tenantId,
      branchId: branchId ?? this.branchId,
      itemCode: itemCode ?? this.itemCode,
      description: description ?? this.description,
      unitPrice: unitPrice ?? this.unitPrice,
      taxCode: taxCode ?? this.taxCode,
      unit: unit ?? this.unit,
      rowid: rowid ?? this.rowid,
    );
  }

  @override
  Map<String, Expression> toColumns(bool nullToAbsent) {
    final map = <String, Expression>{};
    if (id.present) {
      map['id'] = Variable<String>(id.value);
    }
    if (tenantId.present) {
      map['tenant_id'] = Variable<String>(tenantId.value);
    }
    if (branchId.present) {
      map['branch_id'] = Variable<String>(branchId.value);
    }
    if (itemCode.present) {
      map['item_code'] = Variable<String>(itemCode.value);
    }
    if (description.present) {
      map['description'] = Variable<String>(description.value);
    }
    if (unitPrice.present) {
      map['unit_price'] = Variable<double>(unitPrice.value);
    }
    if (taxCode.present) {
      map['tax_code'] = Variable<String>(taxCode.value);
    }
    if (unit.present) {
      map['unit'] = Variable<String>(unit.value);
    }
    if (rowid.present) {
      map['rowid'] = Variable<int>(rowid.value);
    }
    return map;
  }

  @override
  String toString() {
    return (StringBuffer('LocalProductsCompanion(')
          ..write('id: $id, ')
          ..write('tenantId: $tenantId, ')
          ..write('branchId: $branchId, ')
          ..write('itemCode: $itemCode, ')
          ..write('description: $description, ')
          ..write('unitPrice: $unitPrice, ')
          ..write('taxCode: $taxCode, ')
          ..write('unit: $unit, ')
          ..write('rowid: $rowid')
          ..write(')'))
        .toString();
  }
}

abstract class _$AppDatabase extends GeneratedDatabase {
  _$AppDatabase(QueryExecutor e) : super(e);
  $AppDatabaseManager get managers => $AppDatabaseManager(this);
  late final $LocalInvoicesTable localInvoices = $LocalInvoicesTable(this);
  late final $LocalInvoiceLinesTable localInvoiceLines =
      $LocalInvoiceLinesTable(this);
  late final $OutboxOperationsTable outboxOperations = $OutboxOperationsTable(
    this,
  );
  late final $LocalCustomersTable localCustomers = $LocalCustomersTable(this);
  late final $LocalProductsTable localProducts = $LocalProductsTable(this);
  @override
  Iterable<TableInfo<Table, Object?>> get allTables =>
      allSchemaEntities.whereType<TableInfo<Table, Object?>>();
  @override
  List<DatabaseSchemaEntity> get allSchemaEntities => [
    localInvoices,
    localInvoiceLines,
    outboxOperations,
    localCustomers,
    localProducts,
  ];
}

typedef $$LocalInvoicesTableCreateCompanionBuilder =
    LocalInvoicesCompanion Function({
      required String localId,
      required String tenantId,
      required String branchId,
      Value<String?> serverId,
      required String documentNumber,
      Value<BigInt?> invoiceCounter,
      required DateTime invoiceDate,
      required String transactionType,
      required String paymentMode,
      Value<String?> buyerTin,
      Value<String?> buyerName,
      required double preTaxTotal,
      required double taxTotal,
      required double grandTotal,
      Value<String> currency,
      required String syncStatus,
      Value<String?> irn,
      Value<String?> rrn,
      Value<String?> signedQr,
      Value<int> reprintCount,
      required String rawPayload,
      required DateTime createdAt,
      Value<DateTime?> syncedAt,
      Value<int> rowid,
    });
typedef $$LocalInvoicesTableUpdateCompanionBuilder =
    LocalInvoicesCompanion Function({
      Value<String> localId,
      Value<String> tenantId,
      Value<String> branchId,
      Value<String?> serverId,
      Value<String> documentNumber,
      Value<BigInt?> invoiceCounter,
      Value<DateTime> invoiceDate,
      Value<String> transactionType,
      Value<String> paymentMode,
      Value<String?> buyerTin,
      Value<String?> buyerName,
      Value<double> preTaxTotal,
      Value<double> taxTotal,
      Value<double> grandTotal,
      Value<String> currency,
      Value<String> syncStatus,
      Value<String?> irn,
      Value<String?> rrn,
      Value<String?> signedQr,
      Value<int> reprintCount,
      Value<String> rawPayload,
      Value<DateTime> createdAt,
      Value<DateTime?> syncedAt,
      Value<int> rowid,
    });

class $$LocalInvoicesTableFilterComposer
    extends Composer<_$AppDatabase, $LocalInvoicesTable> {
  $$LocalInvoicesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get localId => $composableBuilder(
    column: $table.localId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tenantId => $composableBuilder(
    column: $table.tenantId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get branchId => $composableBuilder(
    column: $table.branchId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get serverId => $composableBuilder(
    column: $table.serverId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get documentNumber => $composableBuilder(
    column: $table.documentNumber,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<BigInt> get invoiceCounter => $composableBuilder(
    column: $table.invoiceCounter,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get invoiceDate => $composableBuilder(
    column: $table.invoiceDate,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get transactionType => $composableBuilder(
    column: $table.transactionType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get paymentMode => $composableBuilder(
    column: $table.paymentMode,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get buyerTin => $composableBuilder(
    column: $table.buyerTin,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get buyerName => $composableBuilder(
    column: $table.buyerName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get preTaxTotal => $composableBuilder(
    column: $table.preTaxTotal,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get taxTotal => $composableBuilder(
    column: $table.taxTotal,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get grandTotal => $composableBuilder(
    column: $table.grandTotal,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get currency => $composableBuilder(
    column: $table.currency,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get syncStatus => $composableBuilder(
    column: $table.syncStatus,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get irn => $composableBuilder(
    column: $table.irn,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get rrn => $composableBuilder(
    column: $table.rrn,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get signedQr => $composableBuilder(
    column: $table.signedQr,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get reprintCount => $composableBuilder(
    column: $table.reprintCount,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get rawPayload => $composableBuilder(
    column: $table.rawPayload,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get syncedAt => $composableBuilder(
    column: $table.syncedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$LocalInvoicesTableOrderingComposer
    extends Composer<_$AppDatabase, $LocalInvoicesTable> {
  $$LocalInvoicesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get localId => $composableBuilder(
    column: $table.localId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tenantId => $composableBuilder(
    column: $table.tenantId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get branchId => $composableBuilder(
    column: $table.branchId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get serverId => $composableBuilder(
    column: $table.serverId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get documentNumber => $composableBuilder(
    column: $table.documentNumber,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<BigInt> get invoiceCounter => $composableBuilder(
    column: $table.invoiceCounter,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get invoiceDate => $composableBuilder(
    column: $table.invoiceDate,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get transactionType => $composableBuilder(
    column: $table.transactionType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get paymentMode => $composableBuilder(
    column: $table.paymentMode,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get buyerTin => $composableBuilder(
    column: $table.buyerTin,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get buyerName => $composableBuilder(
    column: $table.buyerName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get preTaxTotal => $composableBuilder(
    column: $table.preTaxTotal,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get taxTotal => $composableBuilder(
    column: $table.taxTotal,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get grandTotal => $composableBuilder(
    column: $table.grandTotal,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get currency => $composableBuilder(
    column: $table.currency,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get syncStatus => $composableBuilder(
    column: $table.syncStatus,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get irn => $composableBuilder(
    column: $table.irn,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get rrn => $composableBuilder(
    column: $table.rrn,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get signedQr => $composableBuilder(
    column: $table.signedQr,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get reprintCount => $composableBuilder(
    column: $table.reprintCount,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get rawPayload => $composableBuilder(
    column: $table.rawPayload,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get syncedAt => $composableBuilder(
    column: $table.syncedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$LocalInvoicesTableAnnotationComposer
    extends Composer<_$AppDatabase, $LocalInvoicesTable> {
  $$LocalInvoicesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get localId =>
      $composableBuilder(column: $table.localId, builder: (column) => column);

  GeneratedColumn<String> get tenantId =>
      $composableBuilder(column: $table.tenantId, builder: (column) => column);

  GeneratedColumn<String> get branchId =>
      $composableBuilder(column: $table.branchId, builder: (column) => column);

  GeneratedColumn<String> get serverId =>
      $composableBuilder(column: $table.serverId, builder: (column) => column);

  GeneratedColumn<String> get documentNumber => $composableBuilder(
    column: $table.documentNumber,
    builder: (column) => column,
  );

  GeneratedColumn<BigInt> get invoiceCounter => $composableBuilder(
    column: $table.invoiceCounter,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get invoiceDate => $composableBuilder(
    column: $table.invoiceDate,
    builder: (column) => column,
  );

  GeneratedColumn<String> get transactionType => $composableBuilder(
    column: $table.transactionType,
    builder: (column) => column,
  );

  GeneratedColumn<String> get paymentMode => $composableBuilder(
    column: $table.paymentMode,
    builder: (column) => column,
  );

  GeneratedColumn<String> get buyerTin =>
      $composableBuilder(column: $table.buyerTin, builder: (column) => column);

  GeneratedColumn<String> get buyerName =>
      $composableBuilder(column: $table.buyerName, builder: (column) => column);

  GeneratedColumn<double> get preTaxTotal => $composableBuilder(
    column: $table.preTaxTotal,
    builder: (column) => column,
  );

  GeneratedColumn<double> get taxTotal =>
      $composableBuilder(column: $table.taxTotal, builder: (column) => column);

  GeneratedColumn<double> get grandTotal => $composableBuilder(
    column: $table.grandTotal,
    builder: (column) => column,
  );

  GeneratedColumn<String> get currency =>
      $composableBuilder(column: $table.currency, builder: (column) => column);

  GeneratedColumn<String> get syncStatus => $composableBuilder(
    column: $table.syncStatus,
    builder: (column) => column,
  );

  GeneratedColumn<String> get irn =>
      $composableBuilder(column: $table.irn, builder: (column) => column);

  GeneratedColumn<String> get rrn =>
      $composableBuilder(column: $table.rrn, builder: (column) => column);

  GeneratedColumn<String> get signedQr =>
      $composableBuilder(column: $table.signedQr, builder: (column) => column);

  GeneratedColumn<int> get reprintCount => $composableBuilder(
    column: $table.reprintCount,
    builder: (column) => column,
  );

  GeneratedColumn<String> get rawPayload => $composableBuilder(
    column: $table.rawPayload,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get syncedAt =>
      $composableBuilder(column: $table.syncedAt, builder: (column) => column);
}

class $$LocalInvoicesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $LocalInvoicesTable,
          LocalInvoiceRecord,
          $$LocalInvoicesTableFilterComposer,
          $$LocalInvoicesTableOrderingComposer,
          $$LocalInvoicesTableAnnotationComposer,
          $$LocalInvoicesTableCreateCompanionBuilder,
          $$LocalInvoicesTableUpdateCompanionBuilder,
          (
            LocalInvoiceRecord,
            BaseReferences<
              _$AppDatabase,
              $LocalInvoicesTable,
              LocalInvoiceRecord
            >,
          ),
          LocalInvoiceRecord,
          PrefetchHooks Function()
        > {
  $$LocalInvoicesTableTableManager(_$AppDatabase db, $LocalInvoicesTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LocalInvoicesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LocalInvoicesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LocalInvoicesTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> localId = const Value.absent(),
                Value<String> tenantId = const Value.absent(),
                Value<String> branchId = const Value.absent(),
                Value<String?> serverId = const Value.absent(),
                Value<String> documentNumber = const Value.absent(),
                Value<BigInt?> invoiceCounter = const Value.absent(),
                Value<DateTime> invoiceDate = const Value.absent(),
                Value<String> transactionType = const Value.absent(),
                Value<String> paymentMode = const Value.absent(),
                Value<String?> buyerTin = const Value.absent(),
                Value<String?> buyerName = const Value.absent(),
                Value<double> preTaxTotal = const Value.absent(),
                Value<double> taxTotal = const Value.absent(),
                Value<double> grandTotal = const Value.absent(),
                Value<String> currency = const Value.absent(),
                Value<String> syncStatus = const Value.absent(),
                Value<String?> irn = const Value.absent(),
                Value<String?> rrn = const Value.absent(),
                Value<String?> signedQr = const Value.absent(),
                Value<int> reprintCount = const Value.absent(),
                Value<String> rawPayload = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> syncedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => LocalInvoicesCompanion(
                localId: localId,
                tenantId: tenantId,
                branchId: branchId,
                serverId: serverId,
                documentNumber: documentNumber,
                invoiceCounter: invoiceCounter,
                invoiceDate: invoiceDate,
                transactionType: transactionType,
                paymentMode: paymentMode,
                buyerTin: buyerTin,
                buyerName: buyerName,
                preTaxTotal: preTaxTotal,
                taxTotal: taxTotal,
                grandTotal: grandTotal,
                currency: currency,
                syncStatus: syncStatus,
                irn: irn,
                rrn: rrn,
                signedQr: signedQr,
                reprintCount: reprintCount,
                rawPayload: rawPayload,
                createdAt: createdAt,
                syncedAt: syncedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String localId,
                required String tenantId,
                required String branchId,
                Value<String?> serverId = const Value.absent(),
                required String documentNumber,
                Value<BigInt?> invoiceCounter = const Value.absent(),
                required DateTime invoiceDate,
                required String transactionType,
                required String paymentMode,
                Value<String?> buyerTin = const Value.absent(),
                Value<String?> buyerName = const Value.absent(),
                required double preTaxTotal,
                required double taxTotal,
                required double grandTotal,
                Value<String> currency = const Value.absent(),
                required String syncStatus,
                Value<String?> irn = const Value.absent(),
                Value<String?> rrn = const Value.absent(),
                Value<String?> signedQr = const Value.absent(),
                Value<int> reprintCount = const Value.absent(),
                required String rawPayload,
                required DateTime createdAt,
                Value<DateTime?> syncedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => LocalInvoicesCompanion.insert(
                localId: localId,
                tenantId: tenantId,
                branchId: branchId,
                serverId: serverId,
                documentNumber: documentNumber,
                invoiceCounter: invoiceCounter,
                invoiceDate: invoiceDate,
                transactionType: transactionType,
                paymentMode: paymentMode,
                buyerTin: buyerTin,
                buyerName: buyerName,
                preTaxTotal: preTaxTotal,
                taxTotal: taxTotal,
                grandTotal: grandTotal,
                currency: currency,
                syncStatus: syncStatus,
                irn: irn,
                rrn: rrn,
                signedQr: signedQr,
                reprintCount: reprintCount,
                rawPayload: rawPayload,
                createdAt: createdAt,
                syncedAt: syncedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$LocalInvoicesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $LocalInvoicesTable,
      LocalInvoiceRecord,
      $$LocalInvoicesTableFilterComposer,
      $$LocalInvoicesTableOrderingComposer,
      $$LocalInvoicesTableAnnotationComposer,
      $$LocalInvoicesTableCreateCompanionBuilder,
      $$LocalInvoicesTableUpdateCompanionBuilder,
      (
        LocalInvoiceRecord,
        BaseReferences<_$AppDatabase, $LocalInvoicesTable, LocalInvoiceRecord>,
      ),
      LocalInvoiceRecord,
      PrefetchHooks Function()
    >;
typedef $$LocalInvoiceLinesTableCreateCompanionBuilder =
    LocalInvoiceLinesCompanion Function({
      required String lineId,
      required String invoiceLocalId,
      required String tenantId,
      required String branchId,
      required int lineNumber,
      required String itemCode,
      required String productDescription,
      required double quantity,
      Value<String> unit,
      required double unitPrice,
      Value<double> discount,
      Value<String> taxCode,
      required double taxAmount,
      required double totalLineAmount,
      Value<int> rowid,
    });
typedef $$LocalInvoiceLinesTableUpdateCompanionBuilder =
    LocalInvoiceLinesCompanion Function({
      Value<String> lineId,
      Value<String> invoiceLocalId,
      Value<String> tenantId,
      Value<String> branchId,
      Value<int> lineNumber,
      Value<String> itemCode,
      Value<String> productDescription,
      Value<double> quantity,
      Value<String> unit,
      Value<double> unitPrice,
      Value<double> discount,
      Value<String> taxCode,
      Value<double> taxAmount,
      Value<double> totalLineAmount,
      Value<int> rowid,
    });

class $$LocalInvoiceLinesTableFilterComposer
    extends Composer<_$AppDatabase, $LocalInvoiceLinesTable> {
  $$LocalInvoiceLinesTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get lineId => $composableBuilder(
    column: $table.lineId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get invoiceLocalId => $composableBuilder(
    column: $table.invoiceLocalId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tenantId => $composableBuilder(
    column: $table.tenantId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get branchId => $composableBuilder(
    column: $table.branchId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get lineNumber => $composableBuilder(
    column: $table.lineNumber,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get itemCode => $composableBuilder(
    column: $table.itemCode,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get productDescription => $composableBuilder(
    column: $table.productDescription,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get quantity => $composableBuilder(
    column: $table.quantity,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get unit => $composableBuilder(
    column: $table.unit,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get unitPrice => $composableBuilder(
    column: $table.unitPrice,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get discount => $composableBuilder(
    column: $table.discount,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get taxCode => $composableBuilder(
    column: $table.taxCode,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get taxAmount => $composableBuilder(
    column: $table.taxAmount,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get totalLineAmount => $composableBuilder(
    column: $table.totalLineAmount,
    builder: (column) => ColumnFilters(column),
  );
}

class $$LocalInvoiceLinesTableOrderingComposer
    extends Composer<_$AppDatabase, $LocalInvoiceLinesTable> {
  $$LocalInvoiceLinesTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get lineId => $composableBuilder(
    column: $table.lineId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get invoiceLocalId => $composableBuilder(
    column: $table.invoiceLocalId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tenantId => $composableBuilder(
    column: $table.tenantId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get branchId => $composableBuilder(
    column: $table.branchId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get lineNumber => $composableBuilder(
    column: $table.lineNumber,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get itemCode => $composableBuilder(
    column: $table.itemCode,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get productDescription => $composableBuilder(
    column: $table.productDescription,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get quantity => $composableBuilder(
    column: $table.quantity,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get unit => $composableBuilder(
    column: $table.unit,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get unitPrice => $composableBuilder(
    column: $table.unitPrice,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get discount => $composableBuilder(
    column: $table.discount,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get taxCode => $composableBuilder(
    column: $table.taxCode,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get taxAmount => $composableBuilder(
    column: $table.taxAmount,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get totalLineAmount => $composableBuilder(
    column: $table.totalLineAmount,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$LocalInvoiceLinesTableAnnotationComposer
    extends Composer<_$AppDatabase, $LocalInvoiceLinesTable> {
  $$LocalInvoiceLinesTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get lineId =>
      $composableBuilder(column: $table.lineId, builder: (column) => column);

  GeneratedColumn<String> get invoiceLocalId => $composableBuilder(
    column: $table.invoiceLocalId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get tenantId =>
      $composableBuilder(column: $table.tenantId, builder: (column) => column);

  GeneratedColumn<String> get branchId =>
      $composableBuilder(column: $table.branchId, builder: (column) => column);

  GeneratedColumn<int> get lineNumber => $composableBuilder(
    column: $table.lineNumber,
    builder: (column) => column,
  );

  GeneratedColumn<String> get itemCode =>
      $composableBuilder(column: $table.itemCode, builder: (column) => column);

  GeneratedColumn<String> get productDescription => $composableBuilder(
    column: $table.productDescription,
    builder: (column) => column,
  );

  GeneratedColumn<double> get quantity =>
      $composableBuilder(column: $table.quantity, builder: (column) => column);

  GeneratedColumn<String> get unit =>
      $composableBuilder(column: $table.unit, builder: (column) => column);

  GeneratedColumn<double> get unitPrice =>
      $composableBuilder(column: $table.unitPrice, builder: (column) => column);

  GeneratedColumn<double> get discount =>
      $composableBuilder(column: $table.discount, builder: (column) => column);

  GeneratedColumn<String> get taxCode =>
      $composableBuilder(column: $table.taxCode, builder: (column) => column);

  GeneratedColumn<double> get taxAmount =>
      $composableBuilder(column: $table.taxAmount, builder: (column) => column);

  GeneratedColumn<double> get totalLineAmount => $composableBuilder(
    column: $table.totalLineAmount,
    builder: (column) => column,
  );
}

class $$LocalInvoiceLinesTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $LocalInvoiceLinesTable,
          LocalInvoiceLineRecord,
          $$LocalInvoiceLinesTableFilterComposer,
          $$LocalInvoiceLinesTableOrderingComposer,
          $$LocalInvoiceLinesTableAnnotationComposer,
          $$LocalInvoiceLinesTableCreateCompanionBuilder,
          $$LocalInvoiceLinesTableUpdateCompanionBuilder,
          (
            LocalInvoiceLineRecord,
            BaseReferences<
              _$AppDatabase,
              $LocalInvoiceLinesTable,
              LocalInvoiceLineRecord
            >,
          ),
          LocalInvoiceLineRecord,
          PrefetchHooks Function()
        > {
  $$LocalInvoiceLinesTableTableManager(
    _$AppDatabase db,
    $LocalInvoiceLinesTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LocalInvoiceLinesTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LocalInvoiceLinesTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LocalInvoiceLinesTableAnnotationComposer(
                $db: db,
                $table: table,
              ),
          updateCompanionCallback:
              ({
                Value<String> lineId = const Value.absent(),
                Value<String> invoiceLocalId = const Value.absent(),
                Value<String> tenantId = const Value.absent(),
                Value<String> branchId = const Value.absent(),
                Value<int> lineNumber = const Value.absent(),
                Value<String> itemCode = const Value.absent(),
                Value<String> productDescription = const Value.absent(),
                Value<double> quantity = const Value.absent(),
                Value<String> unit = const Value.absent(),
                Value<double> unitPrice = const Value.absent(),
                Value<double> discount = const Value.absent(),
                Value<String> taxCode = const Value.absent(),
                Value<double> taxAmount = const Value.absent(),
                Value<double> totalLineAmount = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => LocalInvoiceLinesCompanion(
                lineId: lineId,
                invoiceLocalId: invoiceLocalId,
                tenantId: tenantId,
                branchId: branchId,
                lineNumber: lineNumber,
                itemCode: itemCode,
                productDescription: productDescription,
                quantity: quantity,
                unit: unit,
                unitPrice: unitPrice,
                discount: discount,
                taxCode: taxCode,
                taxAmount: taxAmount,
                totalLineAmount: totalLineAmount,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String lineId,
                required String invoiceLocalId,
                required String tenantId,
                required String branchId,
                required int lineNumber,
                required String itemCode,
                required String productDescription,
                required double quantity,
                Value<String> unit = const Value.absent(),
                required double unitPrice,
                Value<double> discount = const Value.absent(),
                Value<String> taxCode = const Value.absent(),
                required double taxAmount,
                required double totalLineAmount,
                Value<int> rowid = const Value.absent(),
              }) => LocalInvoiceLinesCompanion.insert(
                lineId: lineId,
                invoiceLocalId: invoiceLocalId,
                tenantId: tenantId,
                branchId: branchId,
                lineNumber: lineNumber,
                itemCode: itemCode,
                productDescription: productDescription,
                quantity: quantity,
                unit: unit,
                unitPrice: unitPrice,
                discount: discount,
                taxCode: taxCode,
                taxAmount: taxAmount,
                totalLineAmount: totalLineAmount,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$LocalInvoiceLinesTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $LocalInvoiceLinesTable,
      LocalInvoiceLineRecord,
      $$LocalInvoiceLinesTableFilterComposer,
      $$LocalInvoiceLinesTableOrderingComposer,
      $$LocalInvoiceLinesTableAnnotationComposer,
      $$LocalInvoiceLinesTableCreateCompanionBuilder,
      $$LocalInvoiceLinesTableUpdateCompanionBuilder,
      (
        LocalInvoiceLineRecord,
        BaseReferences<
          _$AppDatabase,
          $LocalInvoiceLinesTable,
          LocalInvoiceLineRecord
        >,
      ),
      LocalInvoiceLineRecord,
      PrefetchHooks Function()
    >;
typedef $$OutboxOperationsTableCreateCompanionBuilder =
    OutboxOperationsCompanion Function({
      required String operationId,
      required String idempotencyKey,
      required String tenantId,
      required String branchId,
      required String operationType,
      required String endpoint,
      required String payloadJson,
      Value<String?> deviceSignature,
      Value<BigInt?> offlineSeqNo,
      required DateTime bufferedAt,
      required DateTime createdAt,
      Value<int> attemptCount,
      Value<DateTime?> nextAttemptAt,
      required String syncState,
      Value<String?> lastError,
      Value<int> rowid,
    });
typedef $$OutboxOperationsTableUpdateCompanionBuilder =
    OutboxOperationsCompanion Function({
      Value<String> operationId,
      Value<String> idempotencyKey,
      Value<String> tenantId,
      Value<String> branchId,
      Value<String> operationType,
      Value<String> endpoint,
      Value<String> payloadJson,
      Value<String?> deviceSignature,
      Value<BigInt?> offlineSeqNo,
      Value<DateTime> bufferedAt,
      Value<DateTime> createdAt,
      Value<int> attemptCount,
      Value<DateTime?> nextAttemptAt,
      Value<String> syncState,
      Value<String?> lastError,
      Value<int> rowid,
    });

class $$OutboxOperationsTableFilterComposer
    extends Composer<_$AppDatabase, $OutboxOperationsTable> {
  $$OutboxOperationsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get operationId => $composableBuilder(
    column: $table.operationId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get idempotencyKey => $composableBuilder(
    column: $table.idempotencyKey,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tenantId => $composableBuilder(
    column: $table.tenantId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get branchId => $composableBuilder(
    column: $table.branchId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get operationType => $composableBuilder(
    column: $table.operationType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get endpoint => $composableBuilder(
    column: $table.endpoint,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get payloadJson => $composableBuilder(
    column: $table.payloadJson,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get deviceSignature => $composableBuilder(
    column: $table.deviceSignature,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<BigInt> get offlineSeqNo => $composableBuilder(
    column: $table.offlineSeqNo,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get bufferedAt => $composableBuilder(
    column: $table.bufferedAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<int> get attemptCount => $composableBuilder(
    column: $table.attemptCount,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get nextAttemptAt => $composableBuilder(
    column: $table.nextAttemptAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get syncState => $composableBuilder(
    column: $table.syncState,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get lastError => $composableBuilder(
    column: $table.lastError,
    builder: (column) => ColumnFilters(column),
  );
}

class $$OutboxOperationsTableOrderingComposer
    extends Composer<_$AppDatabase, $OutboxOperationsTable> {
  $$OutboxOperationsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get operationId => $composableBuilder(
    column: $table.operationId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get idempotencyKey => $composableBuilder(
    column: $table.idempotencyKey,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tenantId => $composableBuilder(
    column: $table.tenantId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get branchId => $composableBuilder(
    column: $table.branchId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get operationType => $composableBuilder(
    column: $table.operationType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get endpoint => $composableBuilder(
    column: $table.endpoint,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get payloadJson => $composableBuilder(
    column: $table.payloadJson,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get deviceSignature => $composableBuilder(
    column: $table.deviceSignature,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<BigInt> get offlineSeqNo => $composableBuilder(
    column: $table.offlineSeqNo,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get bufferedAt => $composableBuilder(
    column: $table.bufferedAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<int> get attemptCount => $composableBuilder(
    column: $table.attemptCount,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get nextAttemptAt => $composableBuilder(
    column: $table.nextAttemptAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get syncState => $composableBuilder(
    column: $table.syncState,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get lastError => $composableBuilder(
    column: $table.lastError,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$OutboxOperationsTableAnnotationComposer
    extends Composer<_$AppDatabase, $OutboxOperationsTable> {
  $$OutboxOperationsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get operationId => $composableBuilder(
    column: $table.operationId,
    builder: (column) => column,
  );

  GeneratedColumn<String> get idempotencyKey => $composableBuilder(
    column: $table.idempotencyKey,
    builder: (column) => column,
  );

  GeneratedColumn<String> get tenantId =>
      $composableBuilder(column: $table.tenantId, builder: (column) => column);

  GeneratedColumn<String> get branchId =>
      $composableBuilder(column: $table.branchId, builder: (column) => column);

  GeneratedColumn<String> get operationType => $composableBuilder(
    column: $table.operationType,
    builder: (column) => column,
  );

  GeneratedColumn<String> get endpoint =>
      $composableBuilder(column: $table.endpoint, builder: (column) => column);

  GeneratedColumn<String> get payloadJson => $composableBuilder(
    column: $table.payloadJson,
    builder: (column) => column,
  );

  GeneratedColumn<String> get deviceSignature => $composableBuilder(
    column: $table.deviceSignature,
    builder: (column) => column,
  );

  GeneratedColumn<BigInt> get offlineSeqNo => $composableBuilder(
    column: $table.offlineSeqNo,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get bufferedAt => $composableBuilder(
    column: $table.bufferedAt,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<int> get attemptCount => $composableBuilder(
    column: $table.attemptCount,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get nextAttemptAt => $composableBuilder(
    column: $table.nextAttemptAt,
    builder: (column) => column,
  );

  GeneratedColumn<String> get syncState =>
      $composableBuilder(column: $table.syncState, builder: (column) => column);

  GeneratedColumn<String> get lastError =>
      $composableBuilder(column: $table.lastError, builder: (column) => column);
}

class $$OutboxOperationsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $OutboxOperationsTable,
          OutboxOperationRecord,
          $$OutboxOperationsTableFilterComposer,
          $$OutboxOperationsTableOrderingComposer,
          $$OutboxOperationsTableAnnotationComposer,
          $$OutboxOperationsTableCreateCompanionBuilder,
          $$OutboxOperationsTableUpdateCompanionBuilder,
          (
            OutboxOperationRecord,
            BaseReferences<
              _$AppDatabase,
              $OutboxOperationsTable,
              OutboxOperationRecord
            >,
          ),
          OutboxOperationRecord,
          PrefetchHooks Function()
        > {
  $$OutboxOperationsTableTableManager(
    _$AppDatabase db,
    $OutboxOperationsTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$OutboxOperationsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$OutboxOperationsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$OutboxOperationsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> operationId = const Value.absent(),
                Value<String> idempotencyKey = const Value.absent(),
                Value<String> tenantId = const Value.absent(),
                Value<String> branchId = const Value.absent(),
                Value<String> operationType = const Value.absent(),
                Value<String> endpoint = const Value.absent(),
                Value<String> payloadJson = const Value.absent(),
                Value<String?> deviceSignature = const Value.absent(),
                Value<BigInt?> offlineSeqNo = const Value.absent(),
                Value<DateTime> bufferedAt = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<int> attemptCount = const Value.absent(),
                Value<DateTime?> nextAttemptAt = const Value.absent(),
                Value<String> syncState = const Value.absent(),
                Value<String?> lastError = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => OutboxOperationsCompanion(
                operationId: operationId,
                idempotencyKey: idempotencyKey,
                tenantId: tenantId,
                branchId: branchId,
                operationType: operationType,
                endpoint: endpoint,
                payloadJson: payloadJson,
                deviceSignature: deviceSignature,
                offlineSeqNo: offlineSeqNo,
                bufferedAt: bufferedAt,
                createdAt: createdAt,
                attemptCount: attemptCount,
                nextAttemptAt: nextAttemptAt,
                syncState: syncState,
                lastError: lastError,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String operationId,
                required String idempotencyKey,
                required String tenantId,
                required String branchId,
                required String operationType,
                required String endpoint,
                required String payloadJson,
                Value<String?> deviceSignature = const Value.absent(),
                Value<BigInt?> offlineSeqNo = const Value.absent(),
                required DateTime bufferedAt,
                required DateTime createdAt,
                Value<int> attemptCount = const Value.absent(),
                Value<DateTime?> nextAttemptAt = const Value.absent(),
                required String syncState,
                Value<String?> lastError = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => OutboxOperationsCompanion.insert(
                operationId: operationId,
                idempotencyKey: idempotencyKey,
                tenantId: tenantId,
                branchId: branchId,
                operationType: operationType,
                endpoint: endpoint,
                payloadJson: payloadJson,
                deviceSignature: deviceSignature,
                offlineSeqNo: offlineSeqNo,
                bufferedAt: bufferedAt,
                createdAt: createdAt,
                attemptCount: attemptCount,
                nextAttemptAt: nextAttemptAt,
                syncState: syncState,
                lastError: lastError,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$OutboxOperationsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $OutboxOperationsTable,
      OutboxOperationRecord,
      $$OutboxOperationsTableFilterComposer,
      $$OutboxOperationsTableOrderingComposer,
      $$OutboxOperationsTableAnnotationComposer,
      $$OutboxOperationsTableCreateCompanionBuilder,
      $$OutboxOperationsTableUpdateCompanionBuilder,
      (
        OutboxOperationRecord,
        BaseReferences<
          _$AppDatabase,
          $OutboxOperationsTable,
          OutboxOperationRecord
        >,
      ),
      OutboxOperationRecord,
      PrefetchHooks Function()
    >;
typedef $$LocalCustomersTableCreateCompanionBuilder =
    LocalCustomersCompanion Function({
      required String id,
      required String tenantId,
      Value<String?> branchId,
      Value<String?> tin,
      Value<String?> vatNumber,
      required String legalName,
      Value<String?> tradeName,
      Value<String?> phone,
      Value<String?> email,
      Value<String> country,
      Value<String?> region,
      Value<String?> city,
      Value<String?> zone,
      Value<String?> woreda,
      Value<String?> kebele,
      Value<String?> houseNumber,
      Value<String> buyerIdType,
      Value<String?> buyerIdNumber,
      Value<bool> isVatRegistered,
      Value<String> status,
      Value<String> syncStatus,
      required DateTime createdAt,
      Value<DateTime?> updatedAt,
      Value<int> rowid,
    });
typedef $$LocalCustomersTableUpdateCompanionBuilder =
    LocalCustomersCompanion Function({
      Value<String> id,
      Value<String> tenantId,
      Value<String?> branchId,
      Value<String?> tin,
      Value<String?> vatNumber,
      Value<String> legalName,
      Value<String?> tradeName,
      Value<String?> phone,
      Value<String?> email,
      Value<String> country,
      Value<String?> region,
      Value<String?> city,
      Value<String?> zone,
      Value<String?> woreda,
      Value<String?> kebele,
      Value<String?> houseNumber,
      Value<String> buyerIdType,
      Value<String?> buyerIdNumber,
      Value<bool> isVatRegistered,
      Value<String> status,
      Value<String> syncStatus,
      Value<DateTime> createdAt,
      Value<DateTime?> updatedAt,
      Value<int> rowid,
    });

class $$LocalCustomersTableFilterComposer
    extends Composer<_$AppDatabase, $LocalCustomersTable> {
  $$LocalCustomersTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tenantId => $composableBuilder(
    column: $table.tenantId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get branchId => $composableBuilder(
    column: $table.branchId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tin => $composableBuilder(
    column: $table.tin,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get vatNumber => $composableBuilder(
    column: $table.vatNumber,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get legalName => $composableBuilder(
    column: $table.legalName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tradeName => $composableBuilder(
    column: $table.tradeName,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get phone => $composableBuilder(
    column: $table.phone,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get email => $composableBuilder(
    column: $table.email,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get country => $composableBuilder(
    column: $table.country,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get region => $composableBuilder(
    column: $table.region,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get city => $composableBuilder(
    column: $table.city,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get zone => $composableBuilder(
    column: $table.zone,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get woreda => $composableBuilder(
    column: $table.woreda,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get kebele => $composableBuilder(
    column: $table.kebele,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get houseNumber => $composableBuilder(
    column: $table.houseNumber,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get buyerIdType => $composableBuilder(
    column: $table.buyerIdType,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get buyerIdNumber => $composableBuilder(
    column: $table.buyerIdNumber,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<bool> get isVatRegistered => $composableBuilder(
    column: $table.isVatRegistered,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get syncStatus => $composableBuilder(
    column: $table.syncStatus,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnFilters(column),
  );
}

class $$LocalCustomersTableOrderingComposer
    extends Composer<_$AppDatabase, $LocalCustomersTable> {
  $$LocalCustomersTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tenantId => $composableBuilder(
    column: $table.tenantId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get branchId => $composableBuilder(
    column: $table.branchId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tin => $composableBuilder(
    column: $table.tin,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get vatNumber => $composableBuilder(
    column: $table.vatNumber,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get legalName => $composableBuilder(
    column: $table.legalName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tradeName => $composableBuilder(
    column: $table.tradeName,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get phone => $composableBuilder(
    column: $table.phone,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get email => $composableBuilder(
    column: $table.email,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get country => $composableBuilder(
    column: $table.country,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get region => $composableBuilder(
    column: $table.region,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get city => $composableBuilder(
    column: $table.city,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get zone => $composableBuilder(
    column: $table.zone,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get woreda => $composableBuilder(
    column: $table.woreda,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get kebele => $composableBuilder(
    column: $table.kebele,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get houseNumber => $composableBuilder(
    column: $table.houseNumber,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get buyerIdType => $composableBuilder(
    column: $table.buyerIdType,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get buyerIdNumber => $composableBuilder(
    column: $table.buyerIdNumber,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<bool> get isVatRegistered => $composableBuilder(
    column: $table.isVatRegistered,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get status => $composableBuilder(
    column: $table.status,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get syncStatus => $composableBuilder(
    column: $table.syncStatus,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get createdAt => $composableBuilder(
    column: $table.createdAt,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<DateTime> get updatedAt => $composableBuilder(
    column: $table.updatedAt,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$LocalCustomersTableAnnotationComposer
    extends Composer<_$AppDatabase, $LocalCustomersTable> {
  $$LocalCustomersTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get tenantId =>
      $composableBuilder(column: $table.tenantId, builder: (column) => column);

  GeneratedColumn<String> get branchId =>
      $composableBuilder(column: $table.branchId, builder: (column) => column);

  GeneratedColumn<String> get tin =>
      $composableBuilder(column: $table.tin, builder: (column) => column);

  GeneratedColumn<String> get vatNumber =>
      $composableBuilder(column: $table.vatNumber, builder: (column) => column);

  GeneratedColumn<String> get legalName =>
      $composableBuilder(column: $table.legalName, builder: (column) => column);

  GeneratedColumn<String> get tradeName =>
      $composableBuilder(column: $table.tradeName, builder: (column) => column);

  GeneratedColumn<String> get phone =>
      $composableBuilder(column: $table.phone, builder: (column) => column);

  GeneratedColumn<String> get email =>
      $composableBuilder(column: $table.email, builder: (column) => column);

  GeneratedColumn<String> get country =>
      $composableBuilder(column: $table.country, builder: (column) => column);

  GeneratedColumn<String> get region =>
      $composableBuilder(column: $table.region, builder: (column) => column);

  GeneratedColumn<String> get city =>
      $composableBuilder(column: $table.city, builder: (column) => column);

  GeneratedColumn<String> get zone =>
      $composableBuilder(column: $table.zone, builder: (column) => column);

  GeneratedColumn<String> get woreda =>
      $composableBuilder(column: $table.woreda, builder: (column) => column);

  GeneratedColumn<String> get kebele =>
      $composableBuilder(column: $table.kebele, builder: (column) => column);

  GeneratedColumn<String> get houseNumber => $composableBuilder(
    column: $table.houseNumber,
    builder: (column) => column,
  );

  GeneratedColumn<String> get buyerIdType => $composableBuilder(
    column: $table.buyerIdType,
    builder: (column) => column,
  );

  GeneratedColumn<String> get buyerIdNumber => $composableBuilder(
    column: $table.buyerIdNumber,
    builder: (column) => column,
  );

  GeneratedColumn<bool> get isVatRegistered => $composableBuilder(
    column: $table.isVatRegistered,
    builder: (column) => column,
  );

  GeneratedColumn<String> get status =>
      $composableBuilder(column: $table.status, builder: (column) => column);

  GeneratedColumn<String> get syncStatus => $composableBuilder(
    column: $table.syncStatus,
    builder: (column) => column,
  );

  GeneratedColumn<DateTime> get createdAt =>
      $composableBuilder(column: $table.createdAt, builder: (column) => column);

  GeneratedColumn<DateTime> get updatedAt =>
      $composableBuilder(column: $table.updatedAt, builder: (column) => column);
}

class $$LocalCustomersTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $LocalCustomersTable,
          LocalCustomerRecord,
          $$LocalCustomersTableFilterComposer,
          $$LocalCustomersTableOrderingComposer,
          $$LocalCustomersTableAnnotationComposer,
          $$LocalCustomersTableCreateCompanionBuilder,
          $$LocalCustomersTableUpdateCompanionBuilder,
          (
            LocalCustomerRecord,
            BaseReferences<
              _$AppDatabase,
              $LocalCustomersTable,
              LocalCustomerRecord
            >,
          ),
          LocalCustomerRecord,
          PrefetchHooks Function()
        > {
  $$LocalCustomersTableTableManager(
    _$AppDatabase db,
    $LocalCustomersTable table,
  ) : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LocalCustomersTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LocalCustomersTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LocalCustomersTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> tenantId = const Value.absent(),
                Value<String?> branchId = const Value.absent(),
                Value<String?> tin = const Value.absent(),
                Value<String?> vatNumber = const Value.absent(),
                Value<String> legalName = const Value.absent(),
                Value<String?> tradeName = const Value.absent(),
                Value<String?> phone = const Value.absent(),
                Value<String?> email = const Value.absent(),
                Value<String> country = const Value.absent(),
                Value<String?> region = const Value.absent(),
                Value<String?> city = const Value.absent(),
                Value<String?> zone = const Value.absent(),
                Value<String?> woreda = const Value.absent(),
                Value<String?> kebele = const Value.absent(),
                Value<String?> houseNumber = const Value.absent(),
                Value<String> buyerIdType = const Value.absent(),
                Value<String?> buyerIdNumber = const Value.absent(),
                Value<bool> isVatRegistered = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<String> syncStatus = const Value.absent(),
                Value<DateTime> createdAt = const Value.absent(),
                Value<DateTime?> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => LocalCustomersCompanion(
                id: id,
                tenantId: tenantId,
                branchId: branchId,
                tin: tin,
                vatNumber: vatNumber,
                legalName: legalName,
                tradeName: tradeName,
                phone: phone,
                email: email,
                country: country,
                region: region,
                city: city,
                zone: zone,
                woreda: woreda,
                kebele: kebele,
                houseNumber: houseNumber,
                buyerIdType: buyerIdType,
                buyerIdNumber: buyerIdNumber,
                isVatRegistered: isVatRegistered,
                status: status,
                syncStatus: syncStatus,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String tenantId,
                Value<String?> branchId = const Value.absent(),
                Value<String?> tin = const Value.absent(),
                Value<String?> vatNumber = const Value.absent(),
                required String legalName,
                Value<String?> tradeName = const Value.absent(),
                Value<String?> phone = const Value.absent(),
                Value<String?> email = const Value.absent(),
                Value<String> country = const Value.absent(),
                Value<String?> region = const Value.absent(),
                Value<String?> city = const Value.absent(),
                Value<String?> zone = const Value.absent(),
                Value<String?> woreda = const Value.absent(),
                Value<String?> kebele = const Value.absent(),
                Value<String?> houseNumber = const Value.absent(),
                Value<String> buyerIdType = const Value.absent(),
                Value<String?> buyerIdNumber = const Value.absent(),
                Value<bool> isVatRegistered = const Value.absent(),
                Value<String> status = const Value.absent(),
                Value<String> syncStatus = const Value.absent(),
                required DateTime createdAt,
                Value<DateTime?> updatedAt = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => LocalCustomersCompanion.insert(
                id: id,
                tenantId: tenantId,
                branchId: branchId,
                tin: tin,
                vatNumber: vatNumber,
                legalName: legalName,
                tradeName: tradeName,
                phone: phone,
                email: email,
                country: country,
                region: region,
                city: city,
                zone: zone,
                woreda: woreda,
                kebele: kebele,
                houseNumber: houseNumber,
                buyerIdType: buyerIdType,
                buyerIdNumber: buyerIdNumber,
                isVatRegistered: isVatRegistered,
                status: status,
                syncStatus: syncStatus,
                createdAt: createdAt,
                updatedAt: updatedAt,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$LocalCustomersTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $LocalCustomersTable,
      LocalCustomerRecord,
      $$LocalCustomersTableFilterComposer,
      $$LocalCustomersTableOrderingComposer,
      $$LocalCustomersTableAnnotationComposer,
      $$LocalCustomersTableCreateCompanionBuilder,
      $$LocalCustomersTableUpdateCompanionBuilder,
      (
        LocalCustomerRecord,
        BaseReferences<
          _$AppDatabase,
          $LocalCustomersTable,
          LocalCustomerRecord
        >,
      ),
      LocalCustomerRecord,
      PrefetchHooks Function()
    >;
typedef $$LocalProductsTableCreateCompanionBuilder =
    LocalProductsCompanion Function({
      required String id,
      required String tenantId,
      required String branchId,
      required String itemCode,
      required String description,
      required double unitPrice,
      Value<String> taxCode,
      Value<String> unit,
      Value<int> rowid,
    });
typedef $$LocalProductsTableUpdateCompanionBuilder =
    LocalProductsCompanion Function({
      Value<String> id,
      Value<String> tenantId,
      Value<String> branchId,
      Value<String> itemCode,
      Value<String> description,
      Value<double> unitPrice,
      Value<String> taxCode,
      Value<String> unit,
      Value<int> rowid,
    });

class $$LocalProductsTableFilterComposer
    extends Composer<_$AppDatabase, $LocalProductsTable> {
  $$LocalProductsTableFilterComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnFilters<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get tenantId => $composableBuilder(
    column: $table.tenantId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get branchId => $composableBuilder(
    column: $table.branchId,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get itemCode => $composableBuilder(
    column: $table.itemCode,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<double> get unitPrice => $composableBuilder(
    column: $table.unitPrice,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get taxCode => $composableBuilder(
    column: $table.taxCode,
    builder: (column) => ColumnFilters(column),
  );

  ColumnFilters<String> get unit => $composableBuilder(
    column: $table.unit,
    builder: (column) => ColumnFilters(column),
  );
}

class $$LocalProductsTableOrderingComposer
    extends Composer<_$AppDatabase, $LocalProductsTable> {
  $$LocalProductsTableOrderingComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  ColumnOrderings<String> get id => $composableBuilder(
    column: $table.id,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get tenantId => $composableBuilder(
    column: $table.tenantId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get branchId => $composableBuilder(
    column: $table.branchId,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get itemCode => $composableBuilder(
    column: $table.itemCode,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<double> get unitPrice => $composableBuilder(
    column: $table.unitPrice,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get taxCode => $composableBuilder(
    column: $table.taxCode,
    builder: (column) => ColumnOrderings(column),
  );

  ColumnOrderings<String> get unit => $composableBuilder(
    column: $table.unit,
    builder: (column) => ColumnOrderings(column),
  );
}

class $$LocalProductsTableAnnotationComposer
    extends Composer<_$AppDatabase, $LocalProductsTable> {
  $$LocalProductsTableAnnotationComposer({
    required super.$db,
    required super.$table,
    super.joinBuilder,
    super.$addJoinBuilderToRootComposer,
    super.$removeJoinBuilderFromRootComposer,
  });
  GeneratedColumn<String> get id =>
      $composableBuilder(column: $table.id, builder: (column) => column);

  GeneratedColumn<String> get tenantId =>
      $composableBuilder(column: $table.tenantId, builder: (column) => column);

  GeneratedColumn<String> get branchId =>
      $composableBuilder(column: $table.branchId, builder: (column) => column);

  GeneratedColumn<String> get itemCode =>
      $composableBuilder(column: $table.itemCode, builder: (column) => column);

  GeneratedColumn<String> get description => $composableBuilder(
    column: $table.description,
    builder: (column) => column,
  );

  GeneratedColumn<double> get unitPrice =>
      $composableBuilder(column: $table.unitPrice, builder: (column) => column);

  GeneratedColumn<String> get taxCode =>
      $composableBuilder(column: $table.taxCode, builder: (column) => column);

  GeneratedColumn<String> get unit =>
      $composableBuilder(column: $table.unit, builder: (column) => column);
}

class $$LocalProductsTableTableManager
    extends
        RootTableManager<
          _$AppDatabase,
          $LocalProductsTable,
          LocalProductRecord,
          $$LocalProductsTableFilterComposer,
          $$LocalProductsTableOrderingComposer,
          $$LocalProductsTableAnnotationComposer,
          $$LocalProductsTableCreateCompanionBuilder,
          $$LocalProductsTableUpdateCompanionBuilder,
          (
            LocalProductRecord,
            BaseReferences<
              _$AppDatabase,
              $LocalProductsTable,
              LocalProductRecord
            >,
          ),
          LocalProductRecord,
          PrefetchHooks Function()
        > {
  $$LocalProductsTableTableManager(_$AppDatabase db, $LocalProductsTable table)
    : super(
        TableManagerState(
          db: db,
          table: table,
          createFilteringComposer: () =>
              $$LocalProductsTableFilterComposer($db: db, $table: table),
          createOrderingComposer: () =>
              $$LocalProductsTableOrderingComposer($db: db, $table: table),
          createComputedFieldComposer: () =>
              $$LocalProductsTableAnnotationComposer($db: db, $table: table),
          updateCompanionCallback:
              ({
                Value<String> id = const Value.absent(),
                Value<String> tenantId = const Value.absent(),
                Value<String> branchId = const Value.absent(),
                Value<String> itemCode = const Value.absent(),
                Value<String> description = const Value.absent(),
                Value<double> unitPrice = const Value.absent(),
                Value<String> taxCode = const Value.absent(),
                Value<String> unit = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => LocalProductsCompanion(
                id: id,
                tenantId: tenantId,
                branchId: branchId,
                itemCode: itemCode,
                description: description,
                unitPrice: unitPrice,
                taxCode: taxCode,
                unit: unit,
                rowid: rowid,
              ),
          createCompanionCallback:
              ({
                required String id,
                required String tenantId,
                required String branchId,
                required String itemCode,
                required String description,
                required double unitPrice,
                Value<String> taxCode = const Value.absent(),
                Value<String> unit = const Value.absent(),
                Value<int> rowid = const Value.absent(),
              }) => LocalProductsCompanion.insert(
                id: id,
                tenantId: tenantId,
                branchId: branchId,
                itemCode: itemCode,
                description: description,
                unitPrice: unitPrice,
                taxCode: taxCode,
                unit: unit,
                rowid: rowid,
              ),
          withReferenceMapper: (p0) => p0
              .map((e) => (e.readTable(table), BaseReferences(db, table, e)))
              .toList(),
          prefetchHooksCallback: null,
        ),
      );
}

typedef $$LocalProductsTableProcessedTableManager =
    ProcessedTableManager<
      _$AppDatabase,
      $LocalProductsTable,
      LocalProductRecord,
      $$LocalProductsTableFilterComposer,
      $$LocalProductsTableOrderingComposer,
      $$LocalProductsTableAnnotationComposer,
      $$LocalProductsTableCreateCompanionBuilder,
      $$LocalProductsTableUpdateCompanionBuilder,
      (
        LocalProductRecord,
        BaseReferences<_$AppDatabase, $LocalProductsTable, LocalProductRecord>,
      ),
      LocalProductRecord,
      PrefetchHooks Function()
    >;

class $AppDatabaseManager {
  final _$AppDatabase _db;
  $AppDatabaseManager(this._db);
  $$LocalInvoicesTableTableManager get localInvoices =>
      $$LocalInvoicesTableTableManager(_db, _db.localInvoices);
  $$LocalInvoiceLinesTableTableManager get localInvoiceLines =>
      $$LocalInvoiceLinesTableTableManager(_db, _db.localInvoiceLines);
  $$OutboxOperationsTableTableManager get outboxOperations =>
      $$OutboxOperationsTableTableManager(_db, _db.outboxOperations);
  $$LocalCustomersTableTableManager get localCustomers =>
      $$LocalCustomersTableTableManager(_db, _db.localCustomers);
  $$LocalProductsTableTableManager get localProducts =>
      $$LocalProductsTableTableManager(_db, _db.localProducts);
}
