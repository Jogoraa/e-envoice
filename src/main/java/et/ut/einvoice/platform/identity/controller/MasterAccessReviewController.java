package et.ut.einvoice.platform.identity.controller;

import et.ut.einvoice.platform.identity.dto.IdentityDtos.*;
import et.ut.einvoice.platform.identity.service.MasterAccessReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/master/access-reviews")
@PreAuthorize("hasAuthority('ROLE_PLATFORM_ADMIN')")
public class MasterAccessReviewController {

    private final MasterAccessReviewService reviewService;

    public MasterAccessReviewController(MasterAccessReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/campaigns")
    public ResponseEntity<List<AccessReviewCampaignDto>> listCampaigns() {
        return ResponseEntity.ok(reviewService.listCampaigns());
    }

    @PostMapping("/campaigns")
    public ResponseEntity<AccessReviewCampaignDto> createCampaign(
            Authentication auth,
            @RequestBody CreateReviewCampaignRequest req
    ) {
        String adminUsername = resolveUsername(auth);
        return ResponseEntity.ok(reviewService.createCampaign(adminUsername, req));
    }

    @GetMapping("/campaigns/{campaignId}/entries")
    public ResponseEntity<List<AccessReviewEntryDto>> getCampaignEntries(@PathVariable UUID campaignId) {
        return ResponseEntity.ok(reviewService.getCampaignEntries(campaignId));
    }

    @PostMapping("/campaigns/{campaignId}/finalize")
    public ResponseEntity<AccessReviewCampaignDto> finalizeCampaign(
            Authentication auth,
            @PathVariable UUID campaignId
    ) {
        String adminUsername = resolveUsername(auth);
        return ResponseEntity.ok(reviewService.finalizeCampaign(adminUsername, campaignId));
    }

    @PostMapping("/entries/{entryId}/decision")
    public ResponseEntity<AccessReviewEntryDto> submitDecision(
            Authentication auth,
            @PathVariable UUID entryId,
            @RequestBody SubmitReviewDecisionRequest req
    ) {
        String adminUsername = resolveUsername(auth);
        return ResponseEntity.ok(reviewService.submitEntryDecision(adminUsername, entryId, req));
    }

    private String resolveUsername(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            return "platform.admin";
        }
        return auth.getName();
    }
}
