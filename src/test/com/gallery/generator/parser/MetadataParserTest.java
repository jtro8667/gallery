package com.gallery.generator.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

class MetadataParserTest {

    private MetadataParser parser;

    @BeforeEach
    void setUp() {
        parser = new MetadataParser();
    }

    @Test
    void testValidHeaderWithAllComponents() throws Exception {
        parser.parseHeaderStrictly("Aueršperk, 26.3.2023 (Zubštejnský èundr)", "fallback", "test.txt");
        assertEquals("Aueršperk", parser.getGalleryName());
        assertEquals("26.3.2023", parser.getDate());
        assertEquals("Zubštejnský èundr", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testValidHeaderWithGalleryAndDateOnly() throws Exception {
        parser.parseHeaderStrictly("Beistein, 25.12.2019", "fallback", "test.txt");
        assertEquals("Beistein", parser.getGalleryName());
        assertEquals("25.12.2019", parser.getDate());
        assertNull(parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testMultipleCommasAndBrackets() throws Exception {
        parser.parseHeaderStrictly("Blansko (Blankenstein, u Ústí nad Labem), 5.7.2008 (Labský èundr)", "fallback", "test.txt");
        assertEquals("Blansko (Blankenstein, u Ústí nad Labem)", parser.getGalleryName());
        assertEquals("5.7.2008", parser.getDate());
        assertEquals("Labský èundr", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testLotsOfCommasAndBrackets() throws Exception {
        parser.parseHeaderStrictly("Blansko (Blankenstein, ((u)) Ústí (n)(((,,,a)),d) Labem), 5.7.2008 (La((b),s),ký èundr)", "fallback", "test.txt");
        assertEquals("Blansko (Blankenstein, ((u)) Ústí (n)(((,,,a)),d) Labem)", parser.getGalleryName());
        assertEquals("5.7.2008", parser.getDate());
        assertEquals("La((b),s),ký èundr", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testValidHeaderWithGalleryAndEventOnly() throws Exception {
        parser.parseHeaderStrictly("Birthday Party (Celebration)", "fallback", "test.txt");
        assertEquals("Birthday Party", parser.getGalleryName());
        assertNull(parser.getDate());
        assertEquals("Celebration", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testValidHeaderWithGalleryNameOnly() throws Exception {
        parser.parseHeaderStrictly("Simple Gallery", "fallback", "test.txt");
        assertEquals("Simple Gallery", parser.getGalleryName());
        assertNull(parser.getDate());
        assertNull(parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testHeaderWithExtraWhitespace() throws Exception {
        parser.parseHeaderStrictly("  Spaced Gallery  ,  2023-01-01  (  Event Name  )  ", "fallback", "test.txt");
        assertEquals("Spaced Gallery", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Event Name", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testHeaderWithLeadingTrailingWhitespace() throws Exception {
        parser.parseHeaderStrictly("   Trimmed Gallery, 2023-05-10 (Test Event)   ", "fallback", "test.txt");
        assertEquals("Trimmed Gallery", parser.getGalleryName());
        assertEquals("2023-05-10", parser.getDate());
        assertEquals("Test Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testEmptyGalleryNameUsesFallback() throws Exception {
        parser.parseHeaderStrictly(", 2023-01-01 (Event)", "fallback", "test.txt");
        assertEquals("fallback", parser.getGalleryName());
        assertFalse(parser.isValidHeader());
    }

    @Test
    void testWhitespaceOnlyGalleryNameUsesFallback() throws Exception {
        parser.parseHeaderStrictly("   , 2023-01-01 (Event)", "fallback", "test.txt");
        assertEquals("fallback", parser.getGalleryName());
        assertFalse(parser.isValidHeader());
    }

    @Test
    void testMalformedBracketsOnlyOpening() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, 2023-01-01 (Event", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertNull(parser.getEvent());
        assertFalse(parser.isValidHeader());
    }

    @Test
    void testMalformedBracketsOnlyClosing() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, 2023-01-01 Event)", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("2023-01-01 Event)", parser.getDate());
        assertNull(parser.getEvent());
        assertFalse(parser.isValidHeader());
    }

    @Test
    void testMultipleBracketsUsesLastClosing() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, 2023-01-01 (Event (nested))", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Event (nested)", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testCommaInEvent() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, 2023-01-01 (Event, with comma)", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Event, with comma", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testNoCommaWithBrackets() throws Exception {
        parser.parseHeaderStrictly("Gallery Name (Event)", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertNull(parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testEmptyDateBetweenCommaAndBracket() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, (Event)", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("", parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testEmptyEventInBrackets() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, 2023-01-01 ()", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testSpecialCharactersInGalleryName() throws Exception {
        parser.parseHeaderStrictly("Gällery Nämé: Čřžšť, 2023-01-01 (Event)", "fallback", "test.txt");
        assertEquals("Gällery Nämé: Čřžšť", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testSpecialCharactersInEvent() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, 2023-01-01 (Špeciál Ěvënt)", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Špeciál Ěvënt", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testNumbersInGalleryName() throws Exception {
        parser.parseHeaderStrictly("Gallery 2023 v2.0, 2023-01-01 (Event)", "fallback", "test.txt");
        assertEquals("Gallery 2023 v2.0", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testVeryLongGalleryName() throws Exception {
        String longName = "A".repeat(1000);
        parser.parseHeaderStrictly(longName + ", 2023-01-01 (Event)", "fallback", "test.txt");
        assertEquals(longName, parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testDateWithSpaces() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, January 1st 2023 (Event)", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("January 1st 2023", parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testTabCharacters() throws Exception {
        parser.parseHeaderStrictly("Gallery\tName,\t2023-01-01\t(Event)", "fallback", "test.txt");
        assertEquals("Gallery\tName", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testEventWithMultipleWords() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, 2023-01-01 (Multi Word Event Description)", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Multi Word Event Description", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testGalleryNameWithMultipleWords() throws Exception {
        parser.parseHeaderStrictly("My Amazing Summer Vacation Gallery, 2023-07-15 (Beach)", "fallback", "test.txt");
        assertEquals("My Amazing Summer Vacation Gallery", parser.getGalleryName());
        assertEquals("2023-07-15", parser.getDate());
        assertEquals("Beach", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testNoDateNoEvent() throws Exception {
        parser.parseHeaderStrictly("JustAGallery", "fallback", "test.txt");
        assertEquals("JustAGallery", parser.getGalleryName());
        assertNull(parser.getDate());
        assertNull(parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testCommaButNoDate() throws Exception {
        parser.parseHeaderStrictly("Gallery Name,", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("", parser.getDate());
        assertNull(parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testBracketsBeforeComma() throws Exception {
        parser.parseHeaderStrictly("Gallery (details), 2023-01-01", "fallback", "test.txt");
        assertEquals("Gallery (details)", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertNull(parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testEmptyStringUsesFallback() throws Exception {
        parser.parseHeaderStrictly("", "fallback", "test.txt");
        assertEquals("fallback", parser.getGalleryName());
        assertFalse(parser.isValidHeader());
    }

    @Test
    void testWhitespaceOnlyUsesFallback() throws Exception {
        parser.parseHeaderStrictly("   ", "fallback", "test.txt");
        assertEquals("fallback", parser.getGalleryName());
        assertFalse(parser.isValidHeader());
    }

    @Test
    void testHyphenatedGalleryName() throws Exception {
        parser.parseHeaderStrictly("Gallery-Name-Test, 2023-01-01 (Event)", "fallback", "test.txt");
        assertEquals("Gallery-Name-Test", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testUnderscoreInGalleryName() throws Exception {
        parser.parseHeaderStrictly("Gallery_Name_Test, 2023-01-01 (Event)", "fallback", "test.txt");
        assertEquals("Gallery_Name_Test", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testEventWithPunctuation() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, 2023-01-01 (Event! It's great.)", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("2023-01-01", parser.getDate());
        assertEquals("Event! It's great.", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testDateWithSlashes() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, 01/01/2023 (Event)", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("01/01/2023", parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }

    @Test
    void testDateWithDots() throws Exception {
        parser.parseHeaderStrictly("Gallery Name, 01.01.2023 (Event)", "fallback", "test.txt");
        assertEquals("Gallery Name", parser.getGalleryName());
        assertEquals("01.01.2023", parser.getDate());
        assertEquals("Event", parser.getEvent());
        assertTrue(parser.isValidHeader());
    }
}
