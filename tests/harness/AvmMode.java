package harness;

import android.content.Context;
import android.content.ContextWrapper;

/** Stand-in for the stock camera app's surround-view screen. The mod recognises that screen by this class name. */
public final class AvmMode extends ContextWrapper {
    public AvmMode(Context base) {
        super(base);
    }
}
