package io.github.euonmyoji.selfcleaner;

import java.nio.file.Path;
import java.util.Objects;

/**
 * @author yinyangshi
 */
public class CleanData {
    int days;
    Path path;

    CleanData(int days, Path path) {
        this.days = days;
        this.path = path;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CleanData cleanData = (CleanData) o;
        return days == cleanData.days &&
                Objects.equals(path, cleanData.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(days, path);
    }
}
