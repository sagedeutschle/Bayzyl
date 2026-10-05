package com.bayzyl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ClipboardCommandParserTest {
    @Test
    void pasteRotationIsNormalisedToAQuarterTurnBelow360() {
        assertEquals(270, ClipboardCommandParser.parsePaste(new String[]{"rotation:-90"}).rotation());
        assertEquals(90, ClipboardCommandParser.parsePaste(new String[]{"rotate:450"}).rotation());
        assertEquals(0, ClipboardCommandParser.parsePaste(new String[]{"rotation:360"}).rotation());
        assertEquals(270, ClipboardCommandParser.parsePaste(new String[]{"rotation:left"}).rotation());
    }

    @Test
    void pasteRotationThatIsNotAQuarterTurnIsIgnored() {
        assertEquals(0, ClipboardCommandParser.parsePaste(new String[]{"rotation:45"}).rotation());
        assertEquals(0, ClipboardCommandParser.parsePaste(new String[]{"rotation:garbage"}).rotation());
    }
}
