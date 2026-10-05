package et.ut.einvoice.government.domain;

import et.ut.einvoice.invoicing.domain.Invoice;
import et.ut.einvoice.taxpayer.domain.TaxpayerProfile;

public interface GovernmentRegistrationProvider {

    String getProviderVersion();

    GovernmentRegistrationResult registerInvoice(Invoice invoice, TaxpayerProfile sellerProfile, String token);

    default GovernmentRegistrationResult registerAdjustment(et.ut.einvoice.adjustments.domain.TaxAdjustment adjustment, TaxpayerProfile sellerProfile, String token) {
        String notePrefix = adjustment.getNoteType() == et.ut.einvoice.adjustments.domain.NoteType.CREDIT_NOTE ? "CN-" : "DN-";
        String irn = notePrefix + "MOR-" + java.util.UUID.randomUUID().toString().substring(0, 16).toUpperCase();
        String rrn = "RRN-" + notePrefix + java.util.UUID.randomUUID().toString().substring(0, 18).toUpperCase();
        String ackDate = java.time.Instant.now().toString();
        String qr = "TYPE:" + adjustment.getNoteType() + "|IRN:" + irn + "|RRN:" + rrn + "|ORIG:" + adjustment.getOriginalIrn() + "|AMT:" + adjustment.getAdjustedTotal();
        return GovernmentRegistrationResult.success(irn, rrn, ackDate, qr, "signed-adjustment-payload");
    }

    GovernmentVerificationResult verifySubmission(String submissionId, String documentNumber, TaxpayerProfile sellerProfile, String token);

    String authenticate(String clientId, String clientSecret, String apiKey, String tin);

    CancellationResult cancelInvoice(String irn, String reason, String token);

    record GovernmentRegistrationResult(
            boolean success,
            String irn,
            String rrn,
            String ackDate,
            String signedQr,
            String signedInvoice,
            Long expectedNextDoc,
            Long expectedNextCounter,
            String errorCode,
            String errorMessage
    ) {
        public static GovernmentRegistrationResult success(String irn, String rrn, String ackDate, String signedQr, String signedInvoice) {
            return new GovernmentRegistrationResult(true, irn, rrn, ackDate, signedQr, signedInvoice, null, null, null, null);
        }

        public static GovernmentRegistrationResult sequenceAdjustment(Long nextDoc, Long nextCounter) {
            return new GovernmentRegistrationResult(false, null, null, null, null, null, nextDoc, nextCounter, "SEQUENCE_MISMATCH", "Sequence adjustment required by MoR");
        }

        public static GovernmentRegistrationResult failure(String code, String message) {
            return new GovernmentRegistrationResult(false, null, null, null, null, null, null, null, code, message);
        }
    }

    record GovernmentVerificationResult(
            boolean verified,
            String irn,
            String rrn,
            String ackDate,
            String status
    ) {
        public static GovernmentVerificationResult notFound() {
            return new GovernmentVerificationResult(false, null, null, null, "NOT_FOUND");
        }
        public static GovernmentVerificationResult found(String irn, String rrn, String ackDate) {
            return new GovernmentVerificationResult(true, irn, rrn, ackDate, "REGISTERED");
        }
    }

    record CancellationResult(
            boolean success,
            String cancellationRef,
            String message
    ) {}
}
