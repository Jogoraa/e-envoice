import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../../../app/theme/app_colors.dart';
import '../../../app/theme/app_typography.dart';
import '../../../core/di/providers.dart';
import '../../../data/services/compliance/tenant_lifecycle_service.dart';

class TenantLifecycleNotificationsScreen extends ConsumerStatefulWidget {
  const TenantLifecycleNotificationsScreen({super.key});

  @override
  ConsumerState<TenantLifecycleNotificationsScreen> createState() =>
      _TenantLifecycleNotificationsScreenState();
}

class _TenantLifecycleNotificationsScreenState
    extends ConsumerState<TenantLifecycleNotificationsScreen>
    with SingleTickerProviderStateMixin {
  late TabController _tabController;
  bool _isLoading = true;
  String? _errorMessage;
  List<TenantLifecycleEventDto> _events = [];

  final _dateFormat = DateFormat('dd/MM/yyyy HH:mm');

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 5, vsync: this);
    _loadEvents();
  }

  @override
  void dispose() {
    _tabController.dispose();
    super.dispose();
  }

  Future<void> _loadEvents() async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });

    try {
      final service = ref.read(tenantLifecycleServiceProvider);
      final events = await service.getEvents('all');
      if (mounted) {
        setState(() {
          _events = events;
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

  List<TenantLifecycleEventDto> _filterEvents(String tab) {
    switch (tab) {
      case 'COMMENCEMENT':
        return _events.where((e) => e.eventType == 'COMMENCEMENT').toList();
      case 'TERMINATION':
        return _events.where((e) => e.eventType == 'TERMINATION').toList();
      case 'FAILED':
        return _events.where((e) => e.status == 'FAILED').toList();
      case 'ACKNOWLEDGED':
        return _events.where((e) => e.status == 'ACKNOWLEDGED').toList();
      case 'ALL':
      default:
        return _events;
    }
  }

  Future<void> _triggerCommencement(String tenantId) async {
    try {
      final service = ref.read(tenantLifecycleServiceProvider);
      await service.triggerCommencement(tenantId);
      _loadEvents();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Commencement notification transmitted to MoR.'),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Commencement failed: $e'), backgroundColor: AppColors.error),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Taxpayer Lifecycle Notifications (Directive Art. 19)'),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: _loadEvents,
          ),
        ],
        bottom: TabBar(
          controller: _tabController,
          labelColor: AppColors.primary,
          unselectedLabelColor: AppColors.textSecondary,
          indicatorColor: AppColors.primary,
          tabs: [
            Tab(text: 'Commencement (${_filterEvents('COMMENCEMENT').length})'),
            Tab(text: 'Termination (${_filterEvents('TERMINATION').length})'),
            Tab(text: 'Failed (${_filterEvents('FAILED').length})'),
            Tab(text: 'Acknowledged (${_filterEvents('ACKNOWLEDGED').length})'),
            Tab(text: 'All (${_events.length})'),
          ],
        ),
      ),
      body: Column(
        children: [
          _buildDirectiveNoticeCard(),
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _errorMessage != null
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            const Icon(Icons.error_outline, size: 48, color: AppColors.error),
                            const SizedBox(height: 16),
                            Text(_errorMessage!, style: AppTypography.bodyMedium),
                            const SizedBox(height: 16),
                            ElevatedButton(
                              onPressed: _loadEvents,
                              child: const Text('Retry'),
                            ),
                          ],
                        ),
                      )
                    : TabBarView(
                        controller: _tabController,
                        children: [
                          _buildEventList(_filterEvents('COMMENCEMENT')),
                          _buildEventList(_filterEvents('TERMINATION')),
                          _buildEventList(_filterEvents('FAILED')),
                          _buildEventList(_filterEvents('ACKNOWLEDGED')),
                          _buildEventList(_events),
                        ],
                      ),
          ),
        ],
      ),
    );
  }

  Widget _buildDirectiveNoticeCard() {
    return Container(
      padding: const EdgeInsets.all(16),
      color: AppColors.primary.withValues(alpha: 0.08),
      child: Row(
        children: [
          const Icon(Icons.notifications_active_outlined, color: AppColors.primary, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Directive No. 1142/2026 Art. 19 — Mandatory MoR Notification Protocol',
                  style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 4),
                Text(
                  'The e-invoicing service provider must notify the Ministry of Revenues electronically '
                  'within 48 hours of onboarding a taxpayer (Commencement) and within 24 hours of suspending '
                  'or terminating an invoicing account (Termination).',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildEventList(List<TenantLifecycleEventDto> events) {
    if (events.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.mark_email_read_outlined, size: 64, color: AppColors.textTertiary),
            const SizedBox(height: 16),
            Text('No lifecycle notifications in this category', style: AppTypography.titleMedium),
          ],
        ),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.all(16),
      itemCount: events.length,
      itemBuilder: (ctx, index) {
        final ev = events[index];
        final isCommence = ev.eventType == 'COMMENCEMENT';
        final isFailed = ev.status == 'FAILED';

        return Card(
          margin: const EdgeInsets.only(bottom: 12),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        CircleAvatar(
                          radius: 16,
                          backgroundColor: isCommence
                              ? AppColors.primary.withValues(alpha: 0.1)
                              : AppColors.error.withValues(alpha: 0.1),
                          child: Icon(
                            isCommence ? Icons.person_add : Icons.person_remove,
                            size: 18,
                            color: isCommence ? AppColors.primary : AppColors.error,
                          ),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          ev.eventType,
                          style: AppTypography.titleSmall.copyWith(fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                    _buildStatusBadge(ev.status),
                  ],
                ),
                const SizedBox(height: 12),
                Text('Tenant ID: ${ev.tenantId}', style: AppTypography.bodyMedium),
                const SizedBox(height: 4),
                Text(
                  'Created: ${_dateFormat.format(ev.createdAt)}',
                  style: AppTypography.caption.copyWith(color: AppColors.textSecondary),
                ),
                if (ev.governmentAckReference != null) ...[
                  const SizedBox(height: 6),
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.success.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Row(
                      children: [
                        const Icon(Icons.verified, size: 16, color: AppColors.success),
                        const SizedBox(width: 6),
                        Text(
                          'MoR Acknowledgment Ref: ${ev.governmentAckReference}',
                          style: const TextStyle(color: AppColors.success, fontSize: 12, fontWeight: FontWeight.bold),
                        ),
                      ],
                    ),
                  ),
                ],
                if (isFailed && ev.lastError != null) ...[
                  const SizedBox(height: 6),
                  Text(
                    'Last Error: ${ev.lastError}',
                    style: AppTypography.caption.copyWith(color: AppColors.error),
                  ),
                ],
                if (isFailed) ...[
                  const Divider(height: 20),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      ElevatedButton.icon(
                        onPressed: () => _triggerCommencement(ev.tenantId),
                        icon: const Icon(Icons.refresh, size: 16),
                        label: const Text('Retry Government Transmission'),
                        style: ElevatedButton.styleFrom(backgroundColor: AppColors.primary),
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildStatusBadge(String status) {
    Color bg;
    Color fg;

    switch (status.toUpperCase()) {
      case 'ACKNOWLEDGED':
        bg = AppColors.success.withValues(alpha: 0.15);
        fg = AppColors.success;
        break;
      case 'FAILED':
        bg = AppColors.error.withValues(alpha: 0.15);
        fg = AppColors.error;
        break;
      case 'SUBMITTED':
      default:
        bg = AppColors.primary.withValues(alpha: 0.15);
        fg = AppColors.primary;
        break;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(12)),
      child: Text(
        status,
        style: TextStyle(color: fg, fontSize: 11, fontWeight: FontWeight.bold),
      ),
    );
  }
}
