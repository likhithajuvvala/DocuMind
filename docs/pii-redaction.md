# PII redaction before model calls

Retrieved excerpts are sent verbatim to whatever model provider is configured. For a hosted provider that means personal data in a contract or incident report leaves your infrastructure. Redaction replaces those values with placeholders in the prompt, so the provider receives the structure of the text without the identities.

## Modes

```
PII_REDACTION_MODE=THIRD_PARTY_ONLY   # default
PII_REDACTION_MODE=ALWAYS
PII_REDACTION_MODE=NEVER
```

`THIRD_PARTY_ONLY` redacts when the chat provider is hosted and skips it for a locally running model, because a local model never transmits the text and redacting it only removes detail from the answer. An unrecognised provider counts as third party — failing open would leak to any vendor nobody had classified yet.

## What is detected

| Category | Placeholder | Notes |
|---|---|---|
| Email | `[EMAIL_1]` | domain labels matched explicitly, so sentence punctuation is not absorbed |
| Phone | `[PHONE_1]` | international form |
| Payment card | `[CARD_1]` | confirmed with a Luhn check |
| National identifier | `[NATIONAL_ID_1]` | |
| IBAN | `[IBAN_1]` | |
| IP address | `[IP_1]` | octets validated |

Placeholders are stable within a single prompt: the same address is always `[EMAIL_1]`, so the model can still tell that two mentions are the same person. They are not stable across requests, and nothing maps a placeholder back to a value.

The Luhn check exists because contracts are full of long numbers. A purchase order like `1234567890123456` is left untouched, while `4539 1488 0343 6467` is removed. Over-redaction is not a safe default — it silently destroys the answer.

## Verified on the wire

The claim worth testing is not that the redactor works on strings, but that the values never reach the provider. A recording proxy in front of the model captured the actual request bytes for the same question against a document containing five kinds of personal data.

| | Redaction off | Redaction on |
|---|---|---|
| Email | **leaked** | absent |
| Phone | **leaked** | absent |
| IP address | **leaked** | absent |
| National id | **leaked** | absent |
| Card number | **leaked** | absent |

With redaction on, the payload carried `[EMAIL_1]`, `[PHONE_1]`, `[IP_1]`, `[NATIONAL_ID_1]`, and `[CARD_1]` instead.

## What the user sees

The answer comes back referring to the placeholder:

> The escalation contact for the incident is [EMAIL_1] [1].

Redaction is one way. The reverse mapping is deliberately not applied to the streamed answer, since re-inserting values would mean holding them alongside the response and reassembling them across streaming token boundaries. The citation still points at the document, where the reader — who is already authorised for that workspace — can see the real value.

That is a genuine trade-off rather than an oversight. If answers must contain the real values, run a local model and leave the default in place: nothing is redacted because nothing leaves the machine.

## Observability

`documind.pii.redactions` counts removals by category, and the service logs how many values were removed per prompt. Neither records the values themselves, which would put the data straight back into the log pipeline it was removed from.

Redaction applies to the excerpts, the question, and the replayed chat history. It does not alter what is stored: the database keeps the original text, because this is about egress, not retention.
