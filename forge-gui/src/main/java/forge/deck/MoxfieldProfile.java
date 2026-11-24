package forge.deck;

import forge.util.Localizer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helpers for a linked public Moxfield profile: username parsing and listing the user's public decks.
 * The Moxfield endpoints used here are unofficial and may change; all network access is isolated in this class.
 */
public final class MoxfieldProfile {
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.\\-]{1,64}$");
    private static final Pattern PROFILE_PATH = Pattern.compile("(?i)^/users/([^/?#]+)");
    private static final String USER_DECKS_URL = "https://api2.moxfield.com/v2/users/%s/decks?pageNumber=%d&pageSize=%d";
    private static final String SEARCH_URL =
            "https://api2.moxfield.com/v2/decks/search?authorUserNames=%s&pageNumber=%d&pageSize=%d&sortType=updated&sortDirection=descending";
    private static final String DECK_URL = "https://moxfield.com/decks/";
    private static final int PAGE_SIZE = 100;
    private static final int MAX_PAGES = 10;
    private static final String PROVIDER_NAME = "Moxfield";

    /** One public deck on a profile. */
    public record DeckSummary(String publicId, String name, String format, String url, String lastUpdated) {
        @Override
        public String toString() {
            return format == null || format.isBlank() ? name : name + " (" + format + ")";
        }
    }

    /** True if the given deck source URL points at moxfield.com. */
    public static boolean isMoxfieldUrl(final String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        try {
            final String trimmed = url.trim();
            final String host = new URI(trimmed.contains("://") ? trimmed : "https://" + trimmed).getHost();
            return host != null && (host.equalsIgnoreCase("moxfield.com") || host.toLowerCase().endsWith(".moxfield.com"));
        } catch (final Exception ex) {
            return false;
        }
    }

    public static boolean isMoxfieldDeck(final Deck deck) {
        return deck != null && isMoxfieldUrl(deck.getSourceUrl());
    }

    /**
     * Accepts a bare username, "@name", or a profile link such as https://moxfield.com/users/name.
     * @return the username, or null if the input is not recognizable.
     */
    public static String parseUsername(final String input) {
        if (input == null) {
            return null;
        }
        String text = input.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.contains("/") || text.contains(".com")) {
            try {
                final URI uri = new URI(text.contains("://") ? text : "https://" + text);
                if (uri.getHost() == null || !isMoxfieldUrl(text)) {
                    return null;
                }
                final Matcher m = PROFILE_PATH.matcher(uri.getPath() == null ? "" : uri.getPath());
                if (!m.find()) {
                    return null;
                }
                text = m.group(1);
            } catch (final Exception ex) {
                return null;
            }
        }
        if (text.startsWith("@")) {
            text = text.substring(1);
        }
        return USERNAME.matcher(text).matches() ? text : null;
    }

    /** True for Commander-style formats (commander, duelCommander, pauperCommander, ...). */
    public static boolean isCommanderFormat(final String format) {
        return format != null && format.toLowerCase().contains("commander");
    }

    /** Lists the user's public Commander decks, newest first when the server honors ordering. */
    public static List<DeckSummary> listPublicCommanderDecks(final String usernameOrLink) throws IOException {
        final List<DeckSummary> out = new ArrayList<>();
        for (final DeckSummary d : listPublicDecks(usernameOrLink)) {
            if (isCommanderFormat(d.format())) {
                out.add(d);
            }
        }
        return out;
    }

    /** Lists the user's public decks, newest first when the server honors ordering. */
    public static List<DeckSummary> listPublicDecks(final String usernameOrLink) throws IOException {
        final String username = parseUsername(usernameOrLink);
        if (username == null) {
            throw new IOException(Localizer.getInstance().getMessage("lblMoxfieldInvalidUser"));
        }
        final String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
        try {
            return fetchAll(USER_DECKS_URL, encoded);
        } catch (final IOException primaryFailure) {
            try {
                return fetchAll(SEARCH_URL, encoded);
            } catch (final IOException ignored) {
                throw primaryFailure;
            }
        }
    }

    private static List<DeckSummary> fetchAll(final String template, final String encodedUser) throws IOException {
        final List<DeckSummary> out = new ArrayList<>();
        int totalPages = 1;
        for (int page = 1; page <= totalPages && page <= MAX_PAGES; page++) {
            final Map<?, ?> root = DeckUrlLoader.readJsonObject(
                    String.format(template, encodedUser, page, PAGE_SIZE), PROVIDER_NAME);
            totalPages = Math.max(1, DeckUrlLoader.getInt(root.get("totalPages"), 1));
            out.addAll(parseDeckPage(root));
        }
        return out;
    }

    static List<DeckSummary> parseDeckPage(final Map<?, ?> root) {
        final List<DeckSummary> out = new ArrayList<>();
        if (!(root.get("data") instanceof List<?> items)) {
            return out;
        }
        for (final Object item : items) {
            if (!(item instanceof Map<?, ?> deck)) {
                continue;
            }
            final String id = DeckUrlLoader.getString(deck.get("publicId"), null);
            if (id == null) {
                continue;
            }
            final String publicUrl = DeckUrlLoader.getString(deck.get("publicUrl"), DECK_URL + id);
            out.add(new DeckSummary(id,
                    DeckUrlLoader.getString(deck.get("name"), id),
                    DeckUrlLoader.getString(deck.get("format"), ""),
                    publicUrl,
                    DeckUrlLoader.getString(deck.get("lastUpdatedAtUtc"), "")));
        }
        return out;
    }

    private MoxfieldProfile() {
    }
}
