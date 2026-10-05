package et.ut.einvoice.government.controller;

import et.ut.einvoice.government.dto.ProvisionTenantCredentialRequest;
import et.ut.einvoice.government.dto.TenantGovernmentCredentialMetadataResponse;
import et.ut.einvoice.government.service.TenantGovernmentCredentialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/government/credentials")
public class TenantGovernmentCredentialController {

    private final TenantGovernmentCredentialService credentialService;

    public TenantGovernmentCredentialController(TenantGovernmentCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/provision")
    public ResponseEntity<TenantGovernmentCredentialMetadataResponse> provisionCredentials(
            @RequestBody ProvisionTenantCredentialRequest request
    ) {
        return ResponseEntity.ok(credentialService.provisionCredentials(request));
    }

    @GetMapping("/metadata")
    public ResponseEntity<TenantGovernmentCredentialMetadataResponse> getMetadata() {
        return ResponseEntity.ok(credentialService.getMetadataForCurrentTenant());
    }

    @PostMapping("/rotate-api-key")
    public ResponseEntity<TenantGovernmentCredentialMetadataResponse> rotateApiKey(
            @RequestBody Map<String, String> body
    ) {
        String newApiKey = body.get("newApiKey");
        return ResponseEntity.ok(credentialService.rotateApiKey(newApiKey));
    }
}
