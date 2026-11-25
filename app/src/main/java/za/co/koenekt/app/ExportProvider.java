package za.co.koenekt.app;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Hands a report download (PDF, Excel, CSV) to the app the owner picks in the share sheet:
 * content://&lt;package&gt;.exports/&lt;file name&gt;, read only, files in cache/exports/ only. The
 * app has no AndroidX, so this is the few lines of FileProvider it needs. Not exported: other apps
 * get a file only through the one-off read grant on the share intent.
 */
public final class ExportProvider extends ContentProvider {
    static final String DIR = "exports";

    static String authority(android.content.Context c) { return c.getPackageName() + ".exports"; }

    static File dir(android.content.Context c) {
        File d = new File(c.getCacheDir(), DIR);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    static Uri uriFor(android.content.Context c, File f) {
        return new Uri.Builder().scheme("content").authority(authority(c)).appendPath(f.getName()).build();
    }

    /** The file behind a uri, or null when it is not one of ours (no path tricks). */
    private File file(Uri uri) {
        if (getContext() == null || uri.getPathSegments().size() != 1) return null;
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty() || name.contains("/") || name.contains("\\") || name.startsWith(".")) return null;
        File d = dir(getContext()), f = new File(d, name);
        try {
            if (!f.getCanonicalFile().getParentFile().equals(d.getCanonicalFile())) return null;
        } catch (IOException e) { return null; }
        return f.isFile() ? f : null;
    }

    static String mime(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (n.endsWith(".csv")) return "text/csv";
        if (n.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    @Override public boolean onCreate() { return true; }

    @Override public String getType(Uri uri) {
        File f = file(uri);
        return f == null ? null : mime(f.getName());
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode == null || !mode.equals("r")) throw new FileNotFoundException("read only");
        File f = file(uri);
        if (f == null) throw new FileNotFoundException(uri.toString());
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        File f = file(uri);
        if (f == null) return null;
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) row[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) row[i] = f.length();
        }
        c.addRow(row);
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
