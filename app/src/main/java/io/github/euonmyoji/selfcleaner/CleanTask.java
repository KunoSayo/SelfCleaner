package io.github.euonmyoji.selfcleaner;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * @author yinyangshi
 */
public class CleanTask {
    private static final double _MB = 1024 * 1024;
    int deleted;
    long deletedBytes;
    int shouldDeleted;
    long shouldDeletedBytes;
    int files;
    long filesBytes;

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.CHINA, "%d(%.1fM)/%d(%.1fM)/%d(%.1fM)", deleted, deletedBytes / _MB,
                shouldDeleted, shouldDeletedBytes / _MB
                , files, filesBytes / _MB);
    }

    void add(CleanTask task) {
        this.deleted += task.deleted;
        this.deletedBytes += task.deletedBytes;

        this.shouldDeleted += task.shouldDeleted;
        this.shouldDeletedBytes += task.shouldDeletedBytes;

        this.files += task.files;
        this.filesBytes += task.filesBytes;
    }
}
