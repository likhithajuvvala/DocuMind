package com.documind.query.pii;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PiiRedactorTest {

    private final PiiRedactor redactor = new PiiRedactor();

    @Test
    void removesContactDetailsFromTheText() {
        String text =
                "Escalate to alice.smith@northwind.example or call +353 1 555 0199 immediately.";

        String redacted = redactor.newSession().redact(text);

        assertThat(redacted)
                .doesNotContain("alice.smith@northwind.example")
                .doesNotContain("+353 1 555 0199");
        assertThat(redacted).contains("[EMAIL_1]").contains("[PHONE_1]");
    }

    @Test
    void givesTheSameValueTheSamePlaceholderWithinOnePrompt() {
        PiiRedactor.RedactionSession session = redactor.newSession();

        String redacted =
                session.redact(
                        "Contact alice@example.com. Reply to alice@example.com within a day.");

        assertThat(redacted).doesNotContain("[EMAIL_2]").doesNotContain("alice@example.com");
        assertThat(redacted.split("\\[EMAIL_1]", -1))
                .as("one value maps to one placeholder, so both mentions read as the same person")
                .hasSize(3);
    }

    @Test
    void keepsPlaceholdersDistinctForDifferentPeople() {
        PiiRedactor.RedactionSession session = redactor.newSession();

        String redacted = session.redact("alice@example.com escalates to bob@example.com");

        assertThat(redacted).contains("[EMAIL_1]").contains("[EMAIL_2]");
    }

    @Test
    void redactsCardNumbersThatPassLuhn() {
        String redacted =
                redactor.newSession().redact("Card on file 4539 1488 0343 6467 expires soon.");

        assertThat(redacted).doesNotContain("4539").contains("[CARD_1]");
    }

    @Test
    void leavesContractFiguresAloneWhenTheyAreNotCardNumbers() {
        // A long reference number in a contract must survive: over-redaction destroys the answer.
        String text = "Purchase order 1234567890123456 covers the twelve month period.";

        String redacted = redactor.newSession().redact(text);

        assertThat(redacted).isEqualTo(text);
    }

    @Test
    void doesNotTouchOrdinaryContractLanguage() {
        String text =
                "Either party may terminate with ninety (90) days notice, per clause 3.1, at 98% dispatch.";

        assertThat(redactor.newSession().redact(text)).isEqualTo(text);
    }

    @Test
    void redactsNationalIdentifiersAndAddresses() {
        String redacted =
                redactor.newSession().redact("SSN 123-45-6789 logged from 192.168.10.24 today.");

        assertThat(redacted).contains("[NATIONAL_ID_1]").contains("[IP_1]");
        assertThat(redacted).doesNotContain("123-45-6789").doesNotContain("192.168.10.24");
    }

    @Test
    void ignoresDottedNumbersThatCannotBeAddresses() {
        String text = "Version 300.400.500.600 of the schedule applies.";

        assertThat(redactor.newSession().redact(text)).isEqualTo(text);
    }

    @Test
    void countsWhatItRemovedWithoutExposingTheValues() {
        PiiRedactor.RedactionSession session = redactor.newSession();

        session.redact("a@example.com, b@example.com, SSN 111-22-3333");

        assertThat(session.totalRedactions()).isEqualTo(3);
        assertThat(session.counts())
                .containsEntry(PiiCategory.EMAIL, 2)
                .containsEntry(PiiCategory.NATIONAL_ID, 1);
    }

    @Test
    void handlesEmptyInput() {
        assertThat(redactor.newSession().redact("")).isEmpty();
        assertThat(redactor.newSession().redact(null)).isNull();
    }
}
