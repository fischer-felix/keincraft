package engine.collection.trait;

import engine.util.Conditions;
import org.checkerframework.checker.nullness.qual.Nullable;

public interface HeadTrait<T> {
    @Nullable
    T headOrNull();

    default T head() {
        T head = headOrNull();
        Conditions.elementNotNull(head, "Can not peek first from collection: it is empty");
        return head;
    }
}
