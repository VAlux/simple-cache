import java.util.Optional;

public interface Cache<K, V> {
  Optional<V> get(final K key);

  V put(final K key, final V value);
}
