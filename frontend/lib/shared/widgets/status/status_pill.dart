import 'package:flutter/material.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';

enum InvoiceSyncStatus {
  synced,
  syncing,
  offlineDraft,
  syncError,
}

/// Standardized Status Pill Component per UT Invoice Brand Guidelines v1.1
/// Renders a small colored dot + sentence-case label. Never all-caps, never raw enum.
class StatusPill extends StatefulWidget {
  final InvoiceSyncStatus status;
  final String? customLabel;
  final String? errorMessage;

  const StatusPill({
    super.key,
    required this.status,
    this.customLabel,
    this.errorMessage,
  });

  const StatusPill.synced({super.key, this.customLabel})
      : status = InvoiceSyncStatus.synced,
        errorMessage = null;

  const StatusPill.syncing({super.key, this.customLabel})
      : status = InvoiceSyncStatus.syncing,
        errorMessage = null;

  const StatusPill.offlineDraft({super.key, this.customLabel})
      : status = InvoiceSyncStatus.offlineDraft,
        errorMessage = null;

  const StatusPill.syncError({super.key, this.customLabel, this.errorMessage})
      : status = InvoiceSyncStatus.syncError;

  @override
  State<StatusPill> createState() => _StatusPillState();
}

class _StatusPillState extends State<StatusPill> with TickerProviderStateMixin {
  AnimationController? _pulseController;
  Animation<double>? _pulseAnimation;

  @override
  void initState() {
    super.initState();
    if (widget.status == InvoiceSyncStatus.syncing) {
      _initPulsing();
    }
  }

  @override
  void didUpdateWidget(covariant StatusPill oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.status == InvoiceSyncStatus.syncing) {
      if (_pulseController == null) {
        _initPulsing();
      }
    } else if (_pulseController != null) {
      _pulseController?.dispose();
      _pulseController = null;
    }
  }

  void _initPulsing() {
    _pulseController?.dispose();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat(reverse: true);
    _pulseAnimation = Tween<double>(begin: 0.35, end: 1.0).animate(
      CurvedAnimation(parent: _pulseController!, curve: Curves.easeInOut),
    );
  }

  @override
  void dispose() {
    _pulseController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    Color dotColor;
    Color bgColor;
    Color textColor;
    String defaultLabel;

    switch (widget.status) {
      case InvoiceSyncStatus.synced:
        dotColor = AppColors.green700;
        bgColor = AppColors.green700.withValues(alpha: 0.08);
        textColor = AppColors.green700;
        defaultLabel = 'Synced';
        break;
      case InvoiceSyncStatus.syncing:
        dotColor = AppColors.navy700;
        bgColor = AppColors.navy700.withValues(alpha: 0.08);
        textColor = AppColors.navy700;
        defaultLabel = 'Syncing';
        break;
      case InvoiceSyncStatus.offlineDraft:
        dotColor = AppColors.amber600;
        bgColor = AppColors.amber600.withValues(alpha: 0.08);
        textColor = AppColors.amber600;
        defaultLabel = 'Offline draft';
        break;
      case InvoiceSyncStatus.syncError:
        dotColor = AppColors.red600;
        bgColor = AppColors.red600.withValues(alpha: 0.08);
        textColor = AppColors.red600;
        defaultLabel = 'Sync error';
        break;
    }

    final String label = widget.customLabel ?? defaultLabel;

    Widget dot = Container(
      width: 7,
      height: 7,
      decoration: BoxDecoration(
        color: dotColor,
        shape: BoxShape.circle,
      ),
    );

    if (widget.status == InvoiceSyncStatus.syncing && _pulseAnimation != null) {
      dot = FadeTransition(
        opacity: _pulseAnimation!,
        child: dot,
      );
    }

    final Widget pill = Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: bgColor,
        borderRadius: BorderRadius.circular(3), // 3px radius per Brand Guidelines
        border: Border.all(color: dotColor.withValues(alpha: 0.25), width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          dot,
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

    if (widget.status == InvoiceSyncStatus.syncError && widget.errorMessage != null) {
      return Tooltip(
        message: widget.errorMessage!,
        child: pill,
      );
    }

    return pill;
  }
}
