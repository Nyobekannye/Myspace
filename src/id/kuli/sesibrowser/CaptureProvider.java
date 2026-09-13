package id.kuli.sesibrowser;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;

/** Provider mini (pengganti FileProvider) untuk hasil foto kamera saat upload dari halaman web. */
public class CaptureProvider extends ContentProvider {
    static Uri uriFor(android.content.Context c, File f) {
        return Uri.parse("content://" + c.getPackageName() + ".capture/" + f.getName());
    }
    static File dir(android.content.Context c) { File d = new File(c.getCacheDir(), "captures"); d.mkdirs(); return d; }

    @Override public boolean onCreate() { return true; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) throw new java.io.FileNotFoundException();
        File f = new File(dir(getContext()), name);
        int m = mode.contains("w") ? (ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE)
                                   : ParcelFileDescriptor.MODE_READ_ONLY;
        return ParcelFileDescriptor.open(f, m);
    }
    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
