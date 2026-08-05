# Information Security Policy

Version 4.2 — approved by the Security Steering Group on 12 January 2026. This policy applies to all employees, contractors, and third parties who access company systems or data.

## 1. Data Classification

All information is classified into one of four levels:

- **Public** — approved for external publication.
- **Internal** — routine business information; disclosure would cause minor harm.
- **Confidential** — customer records, contracts, and financial data; disclosure would cause significant harm.
- **Restricted** — credentials, encryption keys, and personal data of a special category; disclosure would cause severe harm.

Confidential and Restricted data must be encrypted at rest using AES-256 and in transit using TLS 1.2 or above.

## 2. Access Control

Access is granted on a least-privilege basis and reviewed quarterly by system owners. Multi-factor authentication is mandatory for all administrative accounts and for any remote access to production systems. Shared accounts are prohibited except for documented break-glass procedures, which require Security Team approval and are rotated after every use.

## 3. Password Requirements

Passwords must be at least fourteen (14) characters. Reuse of the previous ten passwords is blocked. Service account credentials are rotated every ninety (90) days, and all credentials are stored in the approved secrets manager rather than in source code or configuration files.

## 4. Incident Response

Suspected security incidents must be reported to the Security Team within one (1) hour of discovery. The Security Team triages every report within four (4) hours and assigns a severity:

- **Severity 1** — confirmed breach of Restricted data; executive notification within two (2) hours.
- **Severity 2** — confirmed unauthorised access without data loss; notification within one (1) business day.
- **Severity 3** — policy violation or near miss; handled in the normal work queue.

Where personal data is affected, the Data Protection Officer notifies the supervisory authority within seventy-two (72) hours of becoming aware of the breach.

## 5. Data Retention

Customer records are retained for seven (7) years after the end of the contractual relationship. Application logs are retained for eighteen (18) months. Backups are retained for thirty-five (35) days and are tested by restore drill twice per year.

## 6. Third-Party Risk

Every vendor with access to Confidential or Restricted data must complete a security assessment before onboarding and annually thereafter. Vendors processing personal data must sign a data processing agreement.

## 7. Exceptions

Exceptions require written approval from the Chief Information Security Officer, must state a compensating control, and expire after a maximum of six (6) months.
