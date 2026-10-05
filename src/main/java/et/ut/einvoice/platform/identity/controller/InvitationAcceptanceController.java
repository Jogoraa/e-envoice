package et.ut.einvoice.platform.identity.controller;

import et.ut.einvoice.platform.identity.dto.IdentityDtos.AcceptInvitationRequest;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.InvitationAcceptanceResponse;
import et.ut.einvoice.platform.identity.dto.IdentityDtos.InvitationValidationDto;
import et.ut.einvoice.platform.identity.service.MasterUserLifecycleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, token-gated endpoint for the one-time administrative account activation flow. */
@RestController
@RequestMapping("/api/v1/auth/invitations")
public class InvitationAcceptanceController {

    private final MasterUserLifecycleService invitationService;

    public InvitationAcceptanceController(MasterUserLifecycleService invitationService) {
        this.invitationService = invitationService;
    }

    @GetMapping("/validate")
    public ResponseEntity<InvitationValidationDto> validate(
            @RequestParam @Size(max = 256) String token
    ) {
        return ResponseEntity.ok(invitationService.validateInvitation(token));
    }

    @PostMapping("/accept")
    public ResponseEntity<InvitationAcceptanceResponse> accept(
            @Valid @RequestBody AcceptInvitationRequest request
    ) {
        return ResponseEntity.ok(invitationService.acceptInvitation(request));
    }
}
