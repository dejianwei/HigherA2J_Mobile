package xyz.pengzhihui.androidplugin.Utils;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import xyz.pengzhihui.androidplugin.Activities.MainActivity;

public class FileUtils {
    private static final String TAG = FileUtils.class.getSimpleName();

    public static String getPathFromUri(final Context context, final Uri uri)
    {
        if (uri == null)
        {
            return null;
        }
        final boolean after44 = Build.VERSION.SDK_INT >= 19;
        if (after44 && DocumentsContract.isDocumentUri(context, uri))
        {
            final String authority = uri.getAuthority();
            if ("com.android.externalstorage.documents".equals(authority))
            {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] divide = docId.split(":");
                final String type = divide[0];
                if ("primary".equals(type))
                {
                    String path = Environment.getExternalStorageDirectory().getAbsolutePath().concat("/").concat(divide[1]);
                    return path;
                } else
                {
                    String path = "/storage/".concat(type).concat("/").concat(divide[1]);
                    return path;
                }
            } else if ("com.android.providers.downloads.documents".equals(authority))
            {
                final String docId = DocumentsContract.getDocumentId(uri);
                if (docId.startsWith("raw:"))
                {
                    final String path = docId.replaceFirst("raw:", "");
                    return path;
                }
                final Uri downloadUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.parseLong(docId));
                String path = queryAbsolutePath(context, downloadUri);
                return path;
            } else if ("com.android.providers.media.documents".equals(authority))
            {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] divide = docId.split(":");
                final String type = divide[0];
                Uri mediaUri = null;
                if ("image".equals(type))
                {
                    mediaUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type))
                {
                    mediaUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type))
                {
                    mediaUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                } else
                {
                    return null;
                }
                mediaUri = ContentUris.withAppendedId(mediaUri, Long.parseLong(divide[1]));
                String path = queryAbsolutePath(context, mediaUri);
                return path;
            }
        } else
        {
            final String scheme = uri.getScheme();
            String path = null;
            if ("content".equals(scheme))
            {
                path = queryAbsolutePath(context, uri);
            } else if ("file".equals(scheme))
            {
                path = uri.getPath();
            }
            return path;
        }
        return null;
    }

    public static String queryAbsolutePath(final Context context, final Uri uri)
    {
        final String[] projection = {MediaStore.MediaColumns.DATA};
        Cursor cursor = null;
        try
        {
            cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null && cursor.moveToFirst())
            {
                final int index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                return cursor.getString(index);
            }
        } catch (final Exception ex)
        {
            ex.printStackTrace();
            if (cursor != null)
            {
                cursor.close();
            }
        }
        return null;
    }

    public static float[][] getDepthImg(InputStream in, int height, int width) {
        float[][] img = new float[height][width];
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line = br.readLine();
            int i = 0;
            while (line != null && !line.isEmpty()) {
                String[] arr = line.split(" ");
                for (int k = 0; k < arr.length; k++) {
                    img[i][k] = Float.parseFloat(arr[k]);
                }
                line = br.readLine();
                i++;
            }
            br.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return img;
    }
}
