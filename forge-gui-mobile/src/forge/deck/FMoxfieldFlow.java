package forge.deck;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import forge.Forge;
import forge.deck.MoxfieldProfile.DeckSummary;
import forge.gui.FThreads;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;
import forge.screens.LoadingOverlay;
import forge.toolbox.FOptionPane;
import forge.toolbox.GuiChoose;
import forge.util.Localizer;

/**
 * Android flow for a linked public Moxfield profile: link the profile, choose which decks to import,
 * and refresh every previously imported Moxfield deck in one tap.
 */
public final class FMoxfieldFlow {
    private FMoxfieldFlow() {
    }

    /** @param onChanged invoked on the EDT after decks were imported or refreshed (e.g. to reload a deck list). */
    public static void show(final Runnable onChanged) {
        final String linked = getLinkedUsername();
        if (linked == null) {
            askForProfile(() -> show(onChanged));
            return;
        }
        final Localizer loc = Forge.getLocalizer();
        final int linkedCount = DeckUrlLoader.getMoxfieldDecks().size();
        final List<String> options = List.of(
                loc.getMessage("lblMoxfieldImportSelected"),
                loc.getMessage("lblMoxfieldRefreshCount", String.valueOf(linkedCount)),
                loc.getMessage("lblMoxfieldChangeProfile"),
                loc.getMessage("lblCancel"));
        FOptionPane.showOptionDialog(loc.getMessage("lblMoxfieldProfile", linked), loc.getMessage("lblMoxfieldDecks"), null, options, 0, choice -> {
            if (choice == null) {
                return;
            }
            switch (choice) {
            case 0 -> chooseAndImport(linked, onChanged);
            case 1 -> refreshAll(onChanged);
            case 2 -> askForProfile(() -> show(onChanged));
            default -> { }
            }
        });
    }

    /** One-tap refresh of every saved Moxfield deck. */
    public static void refreshAll(final Runnable onChanged) {
        final Localizer loc = Forge.getLocalizer();
        if (DeckUrlLoader.getMoxfieldDecks().isEmpty()) {
            FOptionPane.showMessageDialog(loc.getMessage("lblMoxfieldNothingToRefresh"), loc.getMessage("lblMoxfieldDecks"));
            return;
        }
        LoadingOverlay.runBackgroundTask(loc.getMessage("lblMoxfieldRefreshAll"), () -> {
            final DeckUrlLoader.BatchResult result = DeckUrlLoader.refreshMoxfieldDecks();
            FThreads.invokeInEdtLater(() -> report(result, onChanged));
        });
    }

    private static void chooseAndImport(final String username, final Runnable onChanged) {
        final Localizer loc = Forge.getLocalizer();
        LoadingOverlay.runBackgroundTask(loc.getMessage("lblMoxfieldLoadDecks"), () -> {
            final List<DeckSummary> decks;
            final Set<String> alreadyImported = new HashSet<>();
            try {
                decks = MoxfieldProfile.listPublicDecks(username);
                for (final Deck saved : DeckUrlLoader.getMoxfieldDecks()) {
                    alreadyImported.add(saved.getSourceUrl());
                }
            } catch (final Exception ex) {
                FThreads.invokeInEdtLater(() -> FOptionPane.showErrorDialog(String.valueOf(ex.getMessage())));
                return;
            }
            FThreads.invokeInEdtLater(() -> {
                if (decks.isEmpty()) {
                    FOptionPane.showMessageDialog(loc.getMessage("lblMoxfieldNoDecks"), loc.getMessage("lblMoxfieldDecks"));
                    return;
                }
                final List<DeckSummary> preselected = new ArrayList<>();
                for (final DeckSummary d : decks) {
                    if (alreadyImported.stream().anyMatch(u -> u != null && u.contains(d.publicId()))) {
                        preselected.add(d);
                    }
                }
                GuiChoose.getChoices(loc.getMessage("lblMoxfieldDecks"), 0, decks.size(), decks, preselected, null,
                        picked -> importPicked(picked, onChanged));
            });
        });
    }

    private static void importPicked(final List<DeckSummary> picked, final Runnable onChanged) {
        if (picked == null || picked.isEmpty()) {
            return;
        }
        LoadingOverlay.runBackgroundTask(Forge.getLocalizer().getMessage("lblMoxfieldImportSelected"), () -> {
            final DeckUrlLoader.BatchResult result = DeckUrlLoader.importMoxfieldDecks(picked);
            FThreads.invokeInEdtLater(() -> report(result, onChanged));
        });
    }

    private static void report(final DeckUrlLoader.BatchResult result, final Runnable onChanged) {
        final Localizer loc = Forge.getLocalizer();
        final StringBuilder msg = new StringBuilder(loc.getMessage("lblMoxfieldImportResult",
                String.valueOf(result.succeeded().size()), String.valueOf(result.failed().size())));
        result.failed().forEach((deck, error) -> msg.append('\n').append(deck).append(": ").append(error));
        FOptionPane.showMessageDialog(msg.toString(), loc.getMessage("lblMoxfieldDecks"));
        if (onChanged != null) {
            onChanged.run();
        }
    }

    private static void askForProfile(final Runnable then) {
        final Localizer loc = Forge.getLocalizer();
        final String current = FModel.getPreferences().getPref(FPref.MOXFIELD_USERNAME);
        FOptionPane.showInputDialog(loc.getMessage("lblMoxfieldUsername"), current == null ? "" : current, (String input) -> {
            if (input == null) {
                return;
            }
            final String username = MoxfieldProfile.parseUsername(input);
            if (username == null) {
                FOptionPane.showErrorDialog(loc.getMessage("lblMoxfieldInvalidUser"));
                return;
            }
            final ForgePreferences prefs = FModel.getPreferences();
            prefs.setPref(FPref.MOXFIELD_USERNAME, username);
            prefs.save();
            then.run();
        });
    }

    private static String getLinkedUsername() {
        final String name = FModel.getPreferences().getPref(FPref.MOXFIELD_USERNAME);
        return name == null || name.isBlank() ? null : name;
    }
}
