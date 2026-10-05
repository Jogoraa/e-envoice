package et.ut.einvoice.portability.domain;

public enum RetentionClassification {
    RETAIN_BY_LAW,             // Fiscal invoices, receipts, tax adjustments, audit chain (Art. 4(2)(d), Proc. 983/2016 Art. 17)
    LEGAL_HOLD,                // Investigation hold or active audit dispute
    TRANSFER_THEN_PURGE,       // Product catalog, temporary sync buffers, drafts
    PURGE_ELIGIBLE_AFTER_EXIT  // Non-statutory operational caches, draft templates, local user logs
}
