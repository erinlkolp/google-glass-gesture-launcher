package dev.erinlkolp.glasslauncher;

import android.content.Context;
import java.util.Collections;
import java.util.List;

/**
 * A tile that exists only to carry a key.
 *
 * <p>Test-source only. {@link #activate(Context)} is deliberately empty: these tests
 * cover list and selection behaviour, never activation.
 */
final class FakeTile implements Tile {

    private final String key;

    FakeTile(String key) {
        this.key = key;
    }

    @Override
    public String label() {
        return key;
    }

    @Override
    public List<String> detailLines() {
        return Collections.emptyList();
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public void activate(Context context) {
    }
}
