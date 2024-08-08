package com.example.serveropener;

import android.content.Context;

import java.io.File;

public class CacheUtils {
    public static void clearApplicationCache(Context context) {
        File cacheDir = context.getCacheDir();
        deleteDir(cacheDir);
    }

    public static void clearApplicationCache(String path) {
        File cacheDir = new File(path);
        if(cacheDir.exists()){
            deleteDir(cacheDir);
        }
    }

    private static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        return dir.delete();
    }
}
