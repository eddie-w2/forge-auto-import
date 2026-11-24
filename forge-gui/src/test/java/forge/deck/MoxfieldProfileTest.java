package forge.deck;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MoxfieldProfileTest {
    @Test
    public void parsesBareUsernamesAndLinks() {
        Assert.assertEquals(MoxfieldProfile.parseUsername("eddie_w"), "eddie_w");
        Assert.assertEquals(MoxfieldProfile.parseUsername("  @eddie-w  "), "eddie-w");
        Assert.assertEquals(MoxfieldProfile.parseUsername("https://moxfield.com/users/eddie_w"), "eddie_w");
        Assert.assertEquals(MoxfieldProfile.parseUsername("moxfield.com/users/eddie_w/decks?x=1"), "eddie_w");
        Assert.assertEquals(MoxfieldProfile.parseUsername("https://www.moxfield.com/users/Eddie"), "Eddie");
    }

    @Test
    public void rejectsInvalidInput() {
        Assert.assertNull(MoxfieldProfile.parseUsername(null));
        Assert.assertNull(MoxfieldProfile.parseUsername("   "));
        Assert.assertNull(MoxfieldProfile.parseUsername("https://example.com/users/bob"));
        Assert.assertNull(MoxfieldProfile.parseUsername("https://moxfield.com/decks/abc"));
        Assert.assertNull(MoxfieldProfile.parseUsername("bad name!"));
    }

    @Test
    public void detectsMoxfieldUrls() {
        Assert.assertTrue(MoxfieldProfile.isMoxfieldUrl("https://moxfield.com/decks/abc"));
        Assert.assertTrue(MoxfieldProfile.isMoxfieldUrl("https://www.moxfield.com/decks/abc"));
        Assert.assertTrue(MoxfieldProfile.isMoxfieldUrl("moxfield.com/decks/abc"));
        Assert.assertFalse(MoxfieldProfile.isMoxfieldUrl("https://notmoxfield.com/decks/abc"));
        Assert.assertFalse(MoxfieldProfile.isMoxfieldUrl("https://archidekt.com/decks/1"));
        Assert.assertFalse(MoxfieldProfile.isMoxfieldUrl(null));
        Assert.assertFalse(MoxfieldProfile.isMoxfieldUrl(""));
    }

    @Test
    public void parsesDeckPage() {
        final Map<String, Object> d1 = new LinkedHashMap<>();
        d1.put("publicId", "abc123");
        d1.put("name", "Goblins");
        d1.put("format", "commander");
        d1.put("publicUrl", "https://moxfield.com/decks/abc123");
        final Map<String, Object> d2 = new LinkedHashMap<>();
        d2.put("publicId", "def456"); //no name/url -> fall back to id and built URL
        final Map<String, Object> bad = new LinkedHashMap<>(); //no id -> skipped
        bad.put("name", "Nope");
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put("data", List.of(d1, d2, bad));

        final List<MoxfieldProfile.DeckSummary> out = MoxfieldProfile.parseDeckPage(root);
        Assert.assertEquals(out.size(), 2);
        Assert.assertEquals(out.get(0).toString(), "Goblins (commander)");
        Assert.assertEquals(out.get(1).name(), "def456");
        Assert.assertEquals(out.get(1).url(), "https://moxfield.com/decks/def456");
        Assert.assertTrue(MoxfieldProfile.parseDeckPage(new LinkedHashMap<>()).isEmpty());
    }
}
