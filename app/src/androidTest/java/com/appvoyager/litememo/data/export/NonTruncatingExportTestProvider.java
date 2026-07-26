package com.appvoyager.litememo.data.export;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.regex.Pattern;

/**
 * mode "w" では truncate しない DocumentsProvider（DownloadsProvider 等）を模したテスト用 provider。
 *
 * 意図的に Java で実装している。この provider はテスト APK 側のプロセス
 * （com.appvoyager.litememo.dev.test）で単独インスタンス化されるが、そのプロセスの
 * classpath には kotlin-stdlib が含まれない（アプリ APK 側と重複するため除外される）。
 * Kotlin で書くと openFile() で
 * NoClassDefFoundError: kotlin.jvm.internal.Intrinsics でクラッシュする（再現確認済み）。
 */
public final class NonTruncatingExportTestProvider extends ContentProvider {

    private static final String DEFAULT_FILE_NAME = "export.zip";
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File file = fileFor(uri);
        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        int flags;
        if (mode.contains("w")) {
            flags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE;
            if (mode.contains("t")) {
                flags |= ParcelFileDescriptor.MODE_TRUNCATE;
            }
        } else if (mode.contains("r")) {
            flags = ParcelFileDescriptor.MODE_READ_ONLY;
        } else {
            throw new FileNotFoundException("Unsupported mode: " + mode);
        }
        return ParcelFileDescriptor.open(file, flags);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return fileFor(uri).delete() ? 1 : 0;
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder
    ) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "application/zip";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private File fileFor(Uri uri) {
        String fileName = uri.getLastPathSegment();
        if (!isSafeFileName(fileName)) {
            fileName = DEFAULT_FILE_NAME;
        }
        return new File(getContext().getCacheDir(), fileName);
    }

    private boolean isSafeFileName(String fileName) {
        return fileName != null
                && !fileName.contains("..")
                && !fileName.contains("/")
                && !fileName.contains("\\")
                && SAFE_FILE_NAME.matcher(fileName).matches();
    }
}
