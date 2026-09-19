# Customer & Enterprise ERP/POS Integration Guide

**Document Reference**: UT-GUIDE-INT-2026-001  
**System**: UT Electronic Invoicing Platform (SaaS & Hybrid Edge)  
**Target Audience**: Software Engineers, Enterprise IT Teams, ERP Administrators, POS Integrators  
**Regulatory Baseline**: Ministry of Revenues Directive No. 1142/2018 EC (2026 GC) (*የኤሌክትሮኒክ ደረሰኝ ሥርዓት አስተዳደር መመሪያ ቁጥር 1142/2018*)  
**Primary Directive Reference**: [`docs/directive/1142_የኤሌክትሮኒክ_ደረሰኝ_ሥርዓት_አስተዳደር_መመሪያ_ቁጥር_1142_2018.pdf`](file:///d:/UT/e-envoice/docs/directive/1142_የኤሌክትሮኒክ_ደረሰኝ_ሥርዓት_አስተዳደር_መመሪያ_ቁጥር_1142_2018.pdf)

---

## 1. Integration Architecture & Regulatory Boundaries

When integrating your Point-of-Sale (POS), Enterprise Resource Planning (ERP), or Billing system with the UT Electronic Invoicing Platform, understand two fundamental boundaries:

### Boundary 1: Commercial Subscription vs. Government Accreditation
* A paid, active UT SaaS subscription gives you access to the cloud infrastructure, multi-tenant APIs, and developer tooling.
* **HOWEVER, a commercial subscription CANNOT and DOES NOT authorize you to legally issue electronic tax invoices.**
* Pursuant to Directive No. 1142/2018 Art. 13 & 14, electronic invoices may only be issued once the Ministry of Revenues (MoR) approves your Taxpayer Identification Number (TIN) and assigns an active fiscal registration status (`GOVERNMENT_ACTIVE`).
* If your subscription is active but your government status is pending or suspended, invoice creation calls will return `403 FORBIDDEN` (`GOVERNMENT_AUTHORIZATION_REQUIRED`).

### Boundary 2: Synchronous Real-Time vs. Offline-Buffered Issuance
* **Online Mode (Normal)**: Your ERP sends invoice -> UT commits to database -> UT submits to MoR EIRS -> MoR acknowledges with IRN, RRN, and cryptographic signature -> Response returned in < 200 ms.
* **Offline Mode (Directive Art. 8(3), 9, 10)**: When internet or MoR EIRS is unreachable, UT automatically buffers the invoice locally, generates a valid offline QR code, and allows printing. Your system must reconcile buffered invoices within **48 hours (or 72 hours under certified telecommunication outage)**.

---

## 2. Mandatory Invoice Elements (Directive No. 1142/2018 Art. 4)

Pursuant to Article 4(1), every electronic tax invoice generated must include:
1. Seller legal name, trade name, and Taxpayer Identification Number (TIN).
2. Registered business address (Region, Sub-city/Zone, Woreda, House No., Phone, Email).
3. Buyer legal name and TIN (Mandatory for all B2B transactions and retail transactions exceeding ETB 10,000).
4. Line item breakdown (Item description, unit of measure, quantity, unit price, discounts).
5. Pre-tax total, applicable tax rates (VAT 15%, TOT 2% or 10%, Excise), and grand total in Ethiopian Birr (ETB).
6. Monotonic, continuous fiscal document sequence number.
7. Unique Invoice Reference Number (IRN), Receipt Reference Number (RRN), and MoR-compliant 2D QR Code.

---

## 3. Integration Code Examples

### 1. cURL (Direct M2M API Request)

```bash
curl -X POST https://api.einvoice.utsystems.et/api/v1/invoices \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_API_KEY_HERE" \
  -H "X-Client-Secret: YOUR_CLIENT_SECRET_HERE" \
  -H "Idempotency-Key: 9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d" \
  -d '{
    "transactionType": "B2C",
    "paymentMode": "CASH",
    "paymentTerm": "IMMEDIATE",
    "items": [
      {
        "itemCode": "PROD-101",
        "productDescription": "Roasted Coffee Beans 1KG",
        "natureOfSupplies": "goods",
        "unit": "KG",
        "quantity": 2.00,
        "unitPrice": 450.00,
        "discount": 0.00,
        "taxCode": "VAT15",
        "exciseRate": 0.00
      }
    ]
  }'
```

### 2. Python 3 (Using `requests`)

```python
import uuid
import requests

API_URL = "https://api.einvoice.utsystems.et/api/v1/invoices"
HEADERS = {
    "Content-Type": "application/json",
    "X-API-Key": "YOUR_API_KEY_HERE",
    "X-Client-Secret": "YOUR_CLIENT_SECRET_HERE",
    "Idempotency-Key": str(uuid.uuid4())
}

payload = {
    "transactionType": "B2B",
    "paymentMode": "BANK_TRANSFER",
    "paymentTerm": "CREDIT_30",
    "buyer": {
        "legalName": "Customer Enterprise PLC",
        "tin": "0099887766",
        "phone": "+251911223344",
        "email": "finance@customer.et",
        "region": "14",
        "woreda": "03"
    },
    "items": [
        {
            "itemCode": "SERV-201",
            "productDescription": "Annual IT Support Maintenance",
            "natureOfSupplies": "services",
            "unit": "MONTH",
            "quantity": 12.00,
            "unitPrice": 5000.00,
            "discount": 1000.00,
            "taxCode": "VAT15",
            "exciseRate": 0.00
        }
    ]
}

response = requests.post(API_URL, json=payload, headers=HEADERS)
data = response.json()

if response.status_code in (200, 202):
    print(f"Invoice Issued! Document: {data['documentNumber']}, IRN: {data['irn']}")
    print(f"Grand Total: {data['grandTotal']} {data['currency']}")
else:
    print(f"Error {data.get('error')}: {data.get('message')}")
    print(f"Amharic Legal Notice: {data.get('amharicMessage')}")
```

### 3. JavaScript / TypeScript (Node.js or Frontend Service)

```typescript
import axios from 'axios';
import { randomUUID } from 'crypto';

interface LineItem {
  itemCode: string;
  productDescription: string;
  natureOfSupplies: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  discount: number;
  taxCode: string;
  exciseRate: number;
}

async function issueInvoice(items: LineItem[]) {
  const response = await axios.post(
    'https://api.einvoice.utsystems.et/api/v1/invoices',
    {
      transactionType: 'B2C',
      paymentMode: 'TELEBIRR',
      paymentTerm: 'IMMEDIATE',
      items: items
    },
    {
      headers: {
        'X-API-Key': process.env.UT_API_KEY,
        'X-Client-Secret': process.env.UT_CLIENT_SECRET,
        'Idempotency-Key': randomUUID(),
      }
    }
  );
  return response.data;
}
```

---

## 4. Consuming QR Code & Printing Thermal Receipts

1. The API response includes a `signedQr` field containing a Base64-encoded PNG image of the QR code (or raw string payload for embedded printers).
2. For thermal printers (58mm or 80mm ESC/POS):
   - You can call `GET /api/v1/invoices/{id}/receipt` to receive ready-to-print formatted ESC/POS command sequences or text layouts.
   - The QR code payload conforms to the MoR EIRS specification: `SELLER:<TIN>|DOC:<DOC_NUM>|TOTAL:<GRAND_TOTAL>|IRN:<IRN>`.
3. Customers can scan the printed QR code using any smartphone or QR reader, which redirects to the public verification endpoint:
   `https://api.einvoice.utsystems.et/api/v1/public/verify/{irn}`
   displaying the official Ministry of Revenues electronic registration status.
