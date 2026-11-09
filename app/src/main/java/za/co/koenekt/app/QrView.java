package za.co.koenekt.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import org.json.JSONArray;

/**
 * Draws a QR code the Link relay made (rows of "0"/"1", dark = 1) with a 4-module quiet
 * zone, always black on white so every camera reads it, also in dark mode. The app has no
 * QR library: the relay encodes (server/qr.py), the phone only paints squares.
 */
@android.annotation.SuppressLint("ViewConstructor")   // made in code only, with its rows
final class QrView extends View {
    private final boolean[][] dark;
    private final Paint ink = new Paint();

    QrView(Context c, JSONArray rows) {
        super(c);
        int n = rows == null ? 0 : rows.length();
        dark = new boolean[n][];
        for (int y = 0; y < n; y++) {
            String row = rows.optString(y);
            dark[y] = new boolean[row.length()];
            for (int x = 0; x < row.length(); x++) dark[y][x] = row.charAt(x) == '1';
        }
        ink.setColor(Color.BLACK); ink.setAntiAlias(false);
        setContentDescription("QR code of the invite link");
    }

    boolean empty() { return dark.length == 0; }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(w, w);
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.WHITE);
        int n = dark.length;
        if (n == 0) return;
        int total = n + 8;
        float module = (float) Math.floor((float) Math.min(getWidth(), getHeight()) / total);
        float offset = (Math.min(getWidth(), getHeight()) - module * total) / 2f + module * 4;
        for (int y = 0; y < n; y++)
            for (int x = 0; x < dark[y].length; x++)
                if (dark[y][x]) canvas.drawRect(offset + x * module, offset + y * module,
                        offset + (x + 1) * module, offset + (y + 1) * module, ink);
    }
}
