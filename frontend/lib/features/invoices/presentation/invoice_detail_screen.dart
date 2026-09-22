import 'dart:typed_data';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:qr_flutter/qr_flutter.dart';
import '../../../app/localization/app_localizations.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../core/errors/app_error.dart';
import '../../../core/errors/error_category.dart';
import '../../../core/utils/number_to_words.dart';
import '../../../core/utils/platform_file_helper.dart';
import '../../../domain/invoice/models/invoice_models.dart';
import '../../../domain/tenant/models/tenant_context.dart';
import '../../../shared/widgets/status/status_pill.dart';

class InvoiceDetailScreen extends ConsumerStatefulWidget {
  final String invoiceId;

  const InvoiceDetailScreen({super.key, required this.invoiceId});

  @override
  ConsumerState<InvoiceDetailScreen> createState() => _InvoiceDetailScreenState();
}

class _InvoiceDetailScreenState extends ConsumerState<InvoiceDetailScreen> {
  InvoiceModel? _invoice;
  bool _isLoading = true;
  bool _isDownloading = false;
  String? _errorMessage;

  final DateFormat _dateFormat = DateFormat('dd/MM/yyyy HH:mm:ss');

  @override
  void initState() {
    super.initState();
    _loadInvoiceDetails();
  }

  Future<void> _loadInvoiceDetails() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final repo = ref.read(invoiceRepositoryProvider);
      final inv = await repo.getInvoiceById(widget.invoiceId);
      if (mounted) {
        setState(() {
          _invoice = inv;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _errorMessage = e.toString();
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _handleReprint() async {
    final repo = ref.read(invoiceRepositoryProvider);
    try {
      await repo.reprintInvoice(widget.invoiceId);
      await _loadInvoiceDetails();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              'Reprint recorded. Watermarked DUPLICATE per Directive Art. 22 (Count: ${_invoice?.reprintCount ?? 1})',
            ),
            backgroundColor: AppColors.navy900,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to record reprint: $e'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    }
  }

  Future<void> _handleCancelInvoice() async {
    final inv = _invoice;
    if (inv == null || inv.irn == null || inv.irn!.isEmpty) return;

    final now = DateTime.now();
    final hoursElapsed = now.difference(inv.invoiceDate).inHours;
    final remainingHours = 48 - hoursElapsed;
    final isWithinSla = remainingHours > 0;

    if (!isWithinSla) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Cancellation rejected: The statutory 48-hour cancellation window has expired pursuant to Directive No. 1142/2026 Art. 26. Use a Credit Note (Art. 25) instead.',
          ),
          backgroundColor: AppColors.red600,
        ),
      );
      return;
    }

    final reasonCategories = [
      'INCORRECT_BUYER_TIN',
      'ITEM_RETURN',
      'ORDER_CANCELLED',
      'DATA_ENTRY_ERROR',
      'SYSTEM_GLITCH',
      'OTHER',
    ];

    String selectedCategory = reasonCategories.first;
    final justificationController = TextEditingController();
    bool isSubmitting = false;
    String? dialogError;

    await showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          backgroundColor: AppColors.paperRaised,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(4)),
          title: Row(
            children: [
              const Icon(Icons.cancel_outlined, color: AppColors.red600, size: 24),
              const SizedBox(width: 8),
              Text('CANCEL TAX INVOICE', style: AppTypography.h2().copyWith(color: AppColors.red600)),
            ],
          ),
          content: Container(
            constraints: const BoxConstraints(maxWidth: 480),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: AppColors.amber600.withValues(alpha: 0.1),
                    border: Border.all(color: AppColors.amber600),
                    borderRadius: BorderRadius.circular(2),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.timer, color: AppColors.amber600, size: 18),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          'Directive No. 1142/2026 Art. 26 SLA: ~$remainingHours hours remaining in legal cancellation window.',
                          style: AppTypography.bodySmall(color: AppColors.navy900).copyWith(fontWeight: FontWeight.w600),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                Text('Invoice Document: ${inv.documentNumber}', style: AppTypography.uiLabelBold(color: AppColors.ink)),
                Text('Invoice IRN: ${inv.irn}', style: AppTypography.mono(color: AppColors.navy700).copyWith(fontSize: 11)),
                const Divider(height: 24, color: AppColors.rule),
                if (dialogError != null) ...[
                  Text(dialogError!, style: AppTypography.bodySmall(color: AppColors.red600)),
                  const SizedBox(height: 12),
                ],
                DropdownButtonFormField<String>(
                  value: selectedCategory,
                  decoration: const InputDecoration(
                    labelText: 'Statutory Reason Category *',
                    border: OutlineInputBorder(),
                  ),
                  items: reasonCategories.map((c) => DropdownMenuItem(value: c, child: Text(c))).toList(),
                  onChanged: (val) {
                    if (val != null) setDialogState(() => selectedCategory = val);
                  },
                ),
                const SizedBox(height: 16),
                TextField(
                  controller: justificationController,
                  maxLines: 3,
                  decoration: const InputDecoration(
                    labelText: 'Detailed Legal Justification *',
                    hintText: 'Enter reason for voiding registered tax invoice...',
                    border: OutlineInputBorder(),
                  ),
                ),
              ],
            ),
          ),
          actions: [
            TextButton(
              onPressed: isSubmitting ? null : () => Navigator.of(ctx).pop(),
              child: const Text('Abort'),
            ),
            ElevatedButton(
              style: ElevatedButton.styleFrom(backgroundColor: AppColors.red600),
              onPressed: isSubmitting
                  ? null
                  : () async {
                      final reason = justificationController.text.trim();
                      if (reason.isEmpty) {
                        setDialogState(() => dialogError = 'Detailed justification is mandatory.');
                        return;
                      }

                      setDialogState(() {
                        isSubmitting = true;
                        dialogError = null;
                      });

                      try {
                        final client = ref.read(apiClientProvider);
                        final response = await client.post(
                          '/api/v1/cancellations',
                          data: {
                            'irn': inv.irn,
                            'reasonCategory': selectedCategory,
                            'detailedReason': reason,
                          },
                        );
                        if (ctx.mounted) {
                          Navigator.of(ctx).pop();
                          await _loadInvoiceDetails();
                          if (mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(
                                  'Invoice cancelled successfully with MoR. Cancellation Ref: ${response.data['cancellationRef'] ?? 'CONFIRMED'}',
                                ),
                                backgroundColor: AppColors.green700,
                              ),
                            );
                          }
                        }
                      } catch (e) {
                        setDialogState(() {
                          isSubmitting = false;
                          dialogError = e is AppError ? e.userMessage : e.toString();
                        });
                      }
                    },
              child: isSubmitting
                  ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Text('Confirm Cancellation'),
            ),
          ],
        ),
      ),
    );
  }

  String? _progressMessage;

  Future<void> _handleViewOfficialReceipt() async {
    if (_isDownloading) return; // Debounce rapid clicks
    setState(() {
      _isDownloading = true;
      _progressMessage = 'Loading official receipt…';
    });

    debugPrint('[Receipt] Starting receipt fetch for invoice: ${widget.invoiceId}');
    try {
      final repo = ref.read(invoiceRepositoryProvider);
      final tenantState = ref.read(tenantContextProvider);
      final activeTenant = tenantState.activeTenant;

      final html = await repo.getInvoiceReceiptHtml(
        widget.invoiceId,
        tenantId: activeTenant?.id,
        branchId: tenantState.activeBranch?.id,
        onProgress: (progress) {
          if (mounted) {
            setState(() => _progressMessage = progress.message);
          }
        },
      );

      debugPrint('[Receipt] Received HTML receipt (${html.length} chars) for invoice: ${widget.invoiceId}');
      if (html.isNotEmpty) {
        PlatformFileHelper.openHtmlReceipt(
          html,
          title: 'MoR Tax Invoice - ${_invoice?.documentNumber ?? widget.invoiceId}',
        );
      } else {
        throw const AppError(
          code: 'RECEIPT_NOT_AVAILABLE',
          message: 'Official receipt is not currently available.',
          category: ErrorCategory.notFound,
        );
      }
    } on AppError catch (appErr) {
      debugPrint('[Receipt] AppError: code=${appErr.code}, status=${appErr.status}, correlationId=${appErr.correlationId}, message=${appErr.message}');
      if (mounted) {
        final refText = appErr.correlationId != null ? ' (Ref: ${appErr.correlationId})' : '';
        final isDraft = appErr.category == ErrorCategory.governmentPending;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${appErr.userMessage}$refText'),
            backgroundColor: isDraft ? AppColors.navy900 : AppColors.red600,
            duration: const Duration(seconds: 4),
          ),
        );
      }
    } catch (e) {
      debugPrint('[Receipt] Unhandled exception: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Could not open official receipt: $e'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isDownloading = false;
          _progressMessage = null;
        });
      }
    }
  }

  Future<void> _handleDownloadPdf() async {
    if (_isDownloading) return; // Debounce rapid clicks
    setState(() {
      _isDownloading = true;
      _progressMessage = 'Preparing official PDF…';
    });

    debugPrint('[PDF] Starting PDF generation/fetch for invoice: ${widget.invoiceId}');
    try {
      final repo = ref.read(invoiceRepositoryProvider);
      final tenantState = ref.read(tenantContextProvider);
      final activeTenant = tenantState.activeTenant;

      final bytes = await repo.downloadInvoicePdf(
        widget.invoiceId,
        tenantId: activeTenant?.id,
        branchId: tenantState.activeBranch?.id,
        onProgress: (progress) {
          if (mounted) {
            setState(() => _progressMessage = progress.message);
          }
        },
      );

      debugPrint('[PDF] Received ${bytes.length} bytes for invoice: ${widget.invoiceId}');
      final docNum = _invoice?.documentNumber.replaceAll('/', '_').replaceAll('\\', '_') ?? widget.invoiceId;
      await PlatformFileHelper.saveOrLaunchPdf(Uint8List.fromList(bytes), 'Invoice_$docNum.pdf');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Official Tax Invoice PDF downloaded successfully.'),
            backgroundColor: AppColors.green700,
          ),
        );
      }
    } on AppError catch (appErr) {
      debugPrint('[PDF] AppError: code=${appErr.code}, status=${appErr.status}, correlationId=${appErr.correlationId}, message=${appErr.message}');
      if (mounted) {
        final refText = appErr.correlationId != null ? ' (Ref: ${appErr.correlationId})' : '';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${appErr.userMessage}$refText'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    } catch (e) {
      debugPrint('[PDF] Unhandled exception: $e');
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Failed to download PDF: $e'),
            backgroundColor: AppColors.red600,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isDownloading = false;
          _progressMessage = null;
        });
      }
    }
  }


  @override
  Widget build(BuildContext context) {
    final loc = AppLocalizations.of(context);
    final tenantState = ref.watch(tenantContextProvider);
    final activeTenant = tenantState.activeTenant;

    if (_isLoading) {
      return const Scaffold(
        backgroundColor: AppColors.paper,
        body: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              CircularProgressIndicator(),
              SizedBox(height: 16),
              Text('Loading authoritative invoice data...', style: TextStyle(color: AppColors.inkMuted)),
            ],
          ),
        ),
      );
    }

    if (_errorMessage != null || _invoice == null) {
      return Scaffold(
        backgroundColor: AppColors.paper,
        appBar: AppBar(
          title: const Text('Invoice Details'),
          leading: IconButton(
            icon: const Icon(Icons.arrow_back),
            onPressed: () => context.go('/invoices'),
          ),
        ),
        body: Center(
          child: Container(
            constraints: const BoxConstraints(maxWidth: 480),
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              color: AppColors.paperRaised,
              border: Border.all(color: AppColors.rule),
              borderRadius: BorderRadius.circular(3),
            ),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline, size: 48, color: AppColors.red600),
                const SizedBox(height: 16),
                Text('Invoice Not Found', style: AppTypography.h2()),
                const SizedBox(height: 8),
                Text(
                  _errorMessage ?? 'The requested invoice could not be located in local or remote storage.',
                  textAlign: TextAlign.center,
                  style: AppTypography.bodySmall(color: AppColors.inkMuted),
                ),
                const SizedBox(height: 20),
                ElevatedButton.icon(
                  onPressed: () => context.go('/invoices'),
                  icon: const Icon(Icons.arrow_back, size: 16),
                  label: const Text('Back to Invoices'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final inv = _invoice!;
    final isRegistered = inv.irn != null && inv.irn!.isNotEmpty;

    return Scaffold(
      backgroundColor: AppColors.paper,
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Top Navigation & Actions Bar
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Row(
                    children: [
                      IconButton(
                        icon: const Icon(Icons.arrow_back),
                        onPressed: () => context.go('/invoices'),
                        tooltip: 'Back to Invoices',
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text('INVOICE DETAILS / የደረሰኝ ዝርዝር', style: AppTypography.h1()),
                            const SizedBox(height: 2),
                            Text(
                              inv.documentNumber,
                              style: AppTypography.mono(
                                color: AppColors.navy700,
                                weight: FontWeight.w600,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 16),
                Wrap(
                  spacing: 12,
                  runSpacing: 8,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    // Sync Status Pill
                    StatusPill(
                      status: inv.status == 'synced'
                          ? InvoiceSyncStatus.synced
                          : (inv.status == 'offlineDraft'
                              ? InvoiceSyncStatus.offlineDraft
                              : (inv.status == 'syncError'
                                  ? InvoiceSyncStatus.syncError
                                  : InvoiceSyncStatus.syncing)),
                    ),

                    // Government Registration Status Pill
                    _buildGovernmentStatusPill(inv.status, inv.irn),

                    // Print / View Official Tax Invoice Action
                    OutlinedButton.icon(
                      onPressed: _isDownloading ? null : _handleViewOfficialReceipt,
                      icon: const Icon(Icons.receipt_long_outlined, size: 16),
                      label: const Text('Print / View Official Tax Invoice'),
                    ),

                    // Download Official PDF Action
                    ElevatedButton.icon(
                      onPressed: _isDownloading ? null : _handleDownloadPdf,
                      icon: _isDownloading
                          ? const SizedBox(
                              width: 14,
                              height: 14,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                            )
                          : const Icon(Icons.picture_as_pdf_outlined, size: 16),
                      label: const Text('Download PDF'),
                    ),

                    // Official Reprint / Duplicate Action
                    if (isRegistered)
                      OutlinedButton.icon(
                        onPressed: _handleReprint,
                        icon: const Icon(Icons.print_outlined, size: 16),
                        label: Text(
                          inv.reprintCount > 0
                              ? 'Reprint (${inv.reprintCount} duplicates)'
                              : loc.get('reprint_duplicate'),
                        ),
                      ),
                    // Official Cancel Invoice Action (Directive Art. 26)
                    if (isRegistered && inv.status != 'CANCELLED')
                      OutlinedButton.icon(
                        onPressed: _isDownloading ? null : _handleCancelInvoice,
                        style: OutlinedButton.styleFrom(
                          foregroundColor: AppColors.red600,
                          side: const BorderSide(color: AppColors.red600),
                        ),
                        icon: const Icon(Icons.cancel_outlined, size: 16),
                        label: const Text('Cancel Invoice (48h SLA)'),
                      ),
                  ],
                ),
              ],
            ),
            // Progress Bar / Notice during receipt / document retrieval
            if (_isDownloading && _progressMessage != null) ...[
              Container(
                padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 14),
                decoration: BoxDecoration(
                  color: AppColors.navy700.withValues(alpha: 0.08),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.navy700.withValues(alpha: 0.2)),
                ),
                child: Row(
                  children: [
                    const SizedBox(
                      width: 14,
                      height: 14,
                      child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.navy700),
                    ),
                    const SizedBox(width: 10),
                    Text(
                      _progressMessage!,
                      style: AppTypography.uiLabel(color: AppColors.navy900),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
            ],

            // Duplicate Watermark Warning Banner (Directive Art. 22)
            if (inv.reprintCount > 0) ...[

              Container(
                padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 16),
                decoration: BoxDecoration(
                  color: AppColors.amber600.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(3),
                  border: Border.all(color: AppColors.amber600, width: 1.5),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.warning_amber_rounded, color: AppColors.amber600, size: 20),
                    const SizedBox(width: 10),
                    Text(
                      'DUPLICATE / ደረሰኝ ቅጂ (Reprint #${inv.reprintCount}) — Directive No. 1142/2026 Art. 22',
                      style: AppTypography.uiLabelBold(color: AppColors.navy900).copyWith(fontSize: 13),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
            ],

            // Top Row: Fiscal & Trader Context + QR Code
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Left 3 Flex: Trader Header & Ministry Fiscal Registration Details
                Expanded(
                  flex: 3,
                  child: Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: AppColors.paperRaised,
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(color: AppColors.rule, width: 1),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // Trader / Seller Bilingual Header
                        Text(
                          activeTenant?.name ?? 'UT Technologies',
                          style: AppTypography.h2().copyWith(color: AppColors.navy900, fontSize: 18),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'የተጨማሪ እሴት ታክስ ደረሰኝ / የሽያጭ ደረሰኝ — VAT INVOICE / SALES RECEIPT',
                          style: AppTypography.uiLabelBold(color: AppColors.inkMuted),
                        ),
                        const SizedBox(height: 16),
                        const Divider(height: 1, color: AppColors.rule),
                        const SizedBox(height: 16),

                        // Fiscal Identifiers
                        _buildInfoRow(
                          'Invoice Reference Number (IRN)',
                          inv.irn ?? 'Pending government submission',
                          isMono: true,
                          isAccent: isRegistered,
                        ),
                        const Divider(height: 16, color: AppColors.rule),
                        _buildInfoRow(
                          'Machine Registration Code (MRC) / Sys No',
                          'MRC-UT-EIRS-001',
                          isMono: true,
                        ),
                        const Divider(height: 16, color: AppColors.rule),
                        _buildInfoRow(
                          'Official Invoice Number',
                          inv.documentNumber,
                          isMono: true,
                        ),
                        const Divider(height: 16, color: AppColors.rule),
                        _buildInfoRow(
                          'Date & Time (ቀን እና ሰዓት)',
                          _dateFormat.format(inv.invoiceDate.toLocal()),
                          isMono: true,
                        ),
                        const Divider(height: 16, color: AppColors.rule),
                        _buildInfoRow(
                          'Transaction Designation',
                          '${inv.transactionType} / ${inv.paymentMode}',
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 20),

                // Right 2 Flex: Authoritative Fiscal QR Code Block
                Expanded(
                  flex: 2,
                  child: Container(
                    padding: const EdgeInsets.all(24),
                    decoration: BoxDecoration(
                      color: AppColors.paperRaised,
                      borderRadius: BorderRadius.circular(3),
                      border: Border.all(color: AppColors.rule, width: 1),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        Text('AUTHORITATIVE FISCAL QR', style: AppTypography.h2()),
                        const SizedBox(height: 4),
                        Text(
                          'የገቢዎች ሚኒስቴር ይፋዊ QR ኮድ',
                          style: AppTypography.bodySmall(color: AppColors.inkMuted).copyWith(fontSize: 11),
                        ),
                        const SizedBox(height: 16),
                        if (inv.signedQr != null && inv.signedQr!.isNotEmpty) ...[
                          Builder(
                            builder: (context) {
                              final rawQr = inv.signedQr!;
                              final safeData = (rawQr.startsWith('http://') || rawQr.startsWith('https://'))
                                  ? rawQr
                                  : 'https://portal.mor.gov.et/public/invoice?irn=${Uri.encodeComponent(inv.irn ?? inv.id)}';

                              return Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(
                                  color: Colors.white,
                                  borderRadius: BorderRadius.circular(3),
                                  border: Border.all(color: AppColors.rule),
                                ),
                                child: QrImageView(
                                  data: safeData,
                                  version: QrVersions.auto,
                                  size: 160.0,
                                  gapless: true,
                                  errorCorrectionLevel: QrErrorCorrectLevel.M,
                                  errorStateBuilder: (cxt, err) => Container(
                                    width: 160,
                                    height: 160,
                                    alignment: Alignment.center,
                                    child: const Text(
                                      'Fiscal QR Code Available via MoR Portal',
                                      textAlign: TextAlign.center,
                                      style: TextStyle(fontSize: 10, color: AppColors.inkMuted),
                                    ),
                                  ),
                                ),
                              );
                            },
                          ),
                          const SizedBox(height: 14),
                          Text(
                            'Public Verification via MoR Portal',
                            style: AppTypography.uiLabelBold(color: AppColors.navy900),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Directive No. 1142/2026 Art. 20(3)(g)',
                            style: AppTypography.bodySmall(color: AppColors.inkMuted).copyWith(fontSize: 11),
                          ),
                        ] else ...[
                          Container(
                            width: double.infinity,
                            height: 160,
                            alignment: Alignment.center,
                            decoration: BoxDecoration(
                              color: AppColors.paper,
                              borderRadius: BorderRadius.circular(3),
                              border: Border.all(color: AppColors.rule, style: BorderStyle.solid),
                            ),
                            child: Column(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                const Icon(Icons.qr_code_2_outlined, size: 48, color: AppColors.inkMuted),
                                const SizedBox(height: 8),
                                Text(
                                  'Fiscal QR Pending',
                                  style: AppTypography.uiLabelBold(color: AppColors.inkMuted),
                                ),
                                const SizedBox(height: 4),
                                Text(
                                  'Awaiting MoR portal registration',
                                  style: AppTypography.monoSmall(color: AppColors.inkMuted),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 14),
                          Text(
                            'Offline Draft — Non-Registered',
                            style: AppTypography.uiLabelBold(color: AppColors.amber600),
                          ),
                        ],
                      ],
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Seller and Buyer Two-Column Address Block
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule, width: 1),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Seller Details (ከ / From)
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('ከ / FROM (SELLER)', style: AppTypography.h2().copyWith(color: AppColors.navy900)),
                        const SizedBox(height: 12),
                        _buildAddressRow('Name (ስም)', activeTenant?.name ?? 'UT Technologies PLC'),
                        _buildAddressRow('Address (አድራሻ)', 'Addis Ababa, Bole Subcity, Woreda 03'),
                        _buildAddressRow('TIN (የግብር ከፋይ ቁጥር)', activeTenant?.tin ?? '0098765432', isMono: true),
                        _buildAddressRow('VAT Reg No (የተ.እ.ታ ቁጥር)', 'VAT-0987654321', isMono: true),
                      ],
                    ),
                  ),
                  const VerticalDivider(width: 32, color: AppColors.rule),

                  // Buyer Details (ለ / To)
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('ለ / TO (BUYER)', style: AppTypography.h2().copyWith(color: AppColors.navy900)),
                        const SizedBox(height: 12),
                        _buildAddressRow('Legal Name (ስም)', inv.buyer?.legalName ?? 'Retail Customer (የችርቻሮ ደንበኛ)'),
                        _buildAddressRow(
                          'Address (አድራሻ)',
                          inv.buyer != null && (inv.buyer!.region != null || inv.buyer!.woreda != null)
                              ? '${inv.buyer?.region ?? ''}, ${inv.buyer?.woreda ?? ''}'
                              : 'Addis Ababa, Ethiopia',
                        ),
                        _buildAddressRow('TIN (የግብር ከፋይ ቁጥር)', inv.buyer?.tin ?? 'N/A', isMono: true),
                        _buildAddressRow('Phone (ስልክ ቁጥር)', inv.buyer?.phone ?? 'N/A', isMono: true),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Bilingual Line Items Table
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule, width: 1),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('TAX INVOICE ITEMS / የዕቃዎች ዝርዝር', style: AppTypography.h2()),
                      Text(
                        'Total Items: ${inv.lines.length}',
                        style: AppTypography.monoSmall(color: AppColors.inkMuted),
                      ),
                    ],
                  ),
                  const SizedBox(height: 16),
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: DataTable(
                      headingRowColor: WidgetStateProperty.all(AppColors.paper),
                      horizontalMargin: 12,
                      columnSpacing: 18,
                      columns: const [
                        DataColumn(label: Text('ተ.ቁ\n#', style: TextStyle(fontWeight: FontWeight.w600))),
                        DataColumn(label: Text('የዕቃ ቁጥር\nItem Code', style: TextStyle(fontWeight: FontWeight.w600))),
                        DataColumn(label: Text('ዕቃ / አገልግሎት መግለጫ\nDescription', style: TextStyle(fontWeight: FontWeight.w600))),
                        DataColumn(label: Text('መጠን\nQty', style: TextStyle(fontWeight: FontWeight.w600))),
                        DataColumn(label: Text('ያንዱ ዋጋ\nUnit Price', style: TextStyle(fontWeight: FontWeight.w600))),
                        DataColumn(label: Text('የግብር ዋጋ\nTaxable Val', style: TextStyle(fontWeight: FontWeight.w600))),
                        DataColumn(label: Text('ታክስ (15%)\nVAT Amount', style: TextStyle(fontWeight: FontWeight.w600))),
                        DataColumn(label: Text('ጠቅላላ ዋጋ (ETB)\nTotal Amount', style: TextStyle(fontWeight: FontWeight.w600))),
                      ],
                      rows: inv.lines.map((l) {
                        return DataRow(
                          cells: [
                            DataCell(Text('${l.lineNumber}', style: AppTypography.mono())),
                            DataCell(Text(l.itemCode, style: AppTypography.mono())),
                            DataCell(Text(l.productDescription, style: const TextStyle(fontWeight: FontWeight.w500))),
                            DataCell(Text('${l.quantity} ${l.unit}', style: AppTypography.mono())),
                            DataCell(Text('ETB ${l.unitPrice.toStringAsFixed(2)}', style: AppTypography.mono())),
                            DataCell(Text('ETB ${l.preTaxValue.toStringAsFixed(2)}', style: AppTypography.mono())),
                            DataCell(Text('ETB ${l.taxAmount.toStringAsFixed(2)}', style: AppTypography.mono())),
                            DataCell(
                              Text(
                                'ETB ${l.totalLineAmount.toStringAsFixed(2)}',
                                style: AppTypography.mono(weight: FontWeight.w600, color: AppColors.navy900),
                              ),
                            ),
                          ],
                        );
                      }).toList(),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Summary Block: Amount in Words + Authoritative Totals
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.paperRaised,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule, width: 1),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Left: Amount in Words & Payment Metadata
                  Expanded(
                    flex: 3,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('AMOUNT IN WORDS / የገንዘቡ ልክ በፊደል', style: AppTypography.uiLabelBold(color: AppColors.navy900)),
                        const SizedBox(height: 6),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: AppColors.paper,
                            borderRadius: BorderRadius.circular(3),
                            border: Border.all(color: AppColors.rule),
                          ),
                          child: Text(
                            NumberToWords.convert(inv.grandTotal),
                            style: AppTypography.bodySmall(color: AppColors.ink).copyWith(
                              fontWeight: FontWeight.w600,
                              fontStyle: FontStyle.italic,
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),
                        _buildAddressRow('Mode of Payment (የክፍያ ዘዴ)', inv.paymentMode),
                        _buildAddressRow('Fiscal Sequence (የደረሰኝ ተራ ቁጥር)', '#${inv.invoiceCounter ?? 1}', isMono: true),
                        _buildAddressRow('Currency (የገንዘብ አይነት)', inv.currency),
                      ],
                    ),
                  ),
                  const SizedBox(width: 32),

                  // Right: Authoritative Totals Breakdown
                  Expanded(
                    flex: 2,
                    child: Column(
                      children: [
                        _buildTotalLine('Pre-Tax Subtotal (ያለ ታክስ የተጣራ ድምር)', inv.preTaxTotal),
                        const SizedBox(height: 8),
                        _buildTotalLine('Taxable Amount (የግብር የሚከፈልበት)', inv.preTaxTotal),
                        const SizedBox(height: 8),
                        _buildTotalLine('VAT Total (15% ቫት)', inv.taxTotal),
                        const Divider(height: 20, color: AppColors.rule),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              'Grand Total (ጠቅላላ የሚከፈል)',
                              style: AppTypography.uiLabelBold(color: AppColors.navy900).copyWith(fontSize: 14),
                            ),
                            Text(
                              'ETB ${inv.grandTotal.toStringAsFixed(2)}',
                              style: AppTypography.mono(
                                color: AppColors.navy900,
                                weight: FontWeight.w700,
                              ).copyWith(fontSize: 19),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),

            // Legal Compliance Footer Notice
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: AppColors.paper,
                borderRadius: BorderRadius.circular(3),
                border: Border.all(color: AppColors.rule),
              ),
              child: Column(
                children: [
                  Text(
                    'ይህ ህጋዊ የሽያጭ ደረሰኝ በገቢዎች ሚኒስቴር የኤሌክትሮኒክስ ደረሰኝ መመሪያ ቁጥር 1142/2026 መሰረት የተዘጋጀ ነው::',
                    textAlign: TextAlign.center,
                    style: AppTypography.bodySmall(color: AppColors.inkMuted).copyWith(fontSize: 12),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Official Electronic Tax Invoice issued in compliance with Ministry of Revenues Directive No. 1142/2026.',
                    textAlign: TextAlign.center,
                    style: AppTypography.bodySmall(color: AppColors.inkMuted).copyWith(fontSize: 11),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildGovernmentStatusPill(String status, String? irn) {
    final isRegistered = irn != null && irn.isNotEmpty;
    Color dotColor;
    Color bgColor;
    Color textColor;
    String label;

    if (isRegistered) {
      dotColor = AppColors.green700;
      bgColor = AppColors.green700.withValues(alpha: 0.08);
      textColor = AppColors.green700;
      label = 'Government registered';
    } else if (status == 'PENDING_REGISTRATION' || status == 'syncing') {
      dotColor = AppColors.navy700;
      bgColor = AppColors.navy700.withValues(alpha: 0.08);
      textColor = AppColors.navy700;
      label = 'Government submission pending';
    } else if (status == 'RECONCILIATION_REQUIRED') {
      dotColor = AppColors.amber600;
      bgColor = AppColors.amber600.withValues(alpha: 0.08);
      textColor = AppColors.amber600;
      label = 'Reconciliation required';
    } else if (status == 'REJECTED') {
      dotColor = AppColors.red600;
      bgColor = AppColors.red600.withValues(alpha: 0.08);
      textColor = AppColors.red600;
      label = 'Government rejected';
    } else {
      dotColor = AppColors.inkMuted;
      bgColor = AppColors.inkMuted.withValues(alpha: 0.08);
      textColor = AppColors.inkMuted;
      label = 'Draft (Unregistered)';
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(3),
        border: Border.all(color: dotColor.withValues(alpha: 0.25), width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          Container(
            width: 7,
            height: 7,
            decoration: BoxDecoration(
              color: dotColor,
              shape: BoxShape.circle,
            ),
          ),
          const SizedBox(width: 6),
          Text(
            label,
            style: AppTypography.uiLabelBold(color: textColor).copyWith(
              fontSize: 12,
              height: 1.1,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildInfoRow(String label, String value, {bool isMono = false, bool isAccent = false}) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Flexible(
          flex: 2,
          child: Text(label, style: AppTypography.uiLabel(color: AppColors.inkMuted)),
        ),
        const SizedBox(width: 12),
        Flexible(
          flex: 3,
          child: Text(
            value,
            textAlign: TextAlign.right,
            style: isMono
                ? AppTypography.mono(
                    color: isAccent ? AppColors.navy700 : AppColors.ink,
                    weight: isAccent ? FontWeight.w600 : FontWeight.w400,
                  )
                : AppTypography.bodySmall(color: AppColors.ink),
          ),
        ),
      ],
    );
  }

  Widget _buildAddressRow(String label, String value, {bool isMono = false}) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 140,
            child: Text(label, style: AppTypography.uiLabel(color: AppColors.inkMuted)),
          ),
          Expanded(
            child: Text(
              value,
              style: isMono
                  ? AppTypography.mono(color: AppColors.ink, weight: FontWeight.w500)
                  : AppTypography.bodySmall(color: AppColors.ink).copyWith(fontWeight: FontWeight.w500),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTotalLine(String label, double amount) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.spaceBetween,
      children: [
        Text(label, style: AppTypography.uiLabel()),
        Text('ETB ${amount.toStringAsFixed(2)}', style: AppTypography.mono()),
      ],
    );
  }
}
