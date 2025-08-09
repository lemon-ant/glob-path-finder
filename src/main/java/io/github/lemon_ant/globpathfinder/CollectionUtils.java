package io.github.lemon_ant.globpathfinder;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;
import lombok.experimental.UtilityClass;

@UtilityClass
class CollectionUtils {

    static boolean isCollectionEmpty(@Nullable Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }
}
