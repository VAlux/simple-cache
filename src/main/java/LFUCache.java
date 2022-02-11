import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public final class LFUCache<K, V> implements Cache<K, V> {

  private static final int DEFAULT_CAPACITY = 10;

  private final Map<K, V> data;
  private final Map<K, Integer> frequencies;
  private final Map<Integer, LinkedHashSet<K>> usageCounts;
  private final int capacity;

  private final EventEmitter<V> onValueEvicted = new EventEmitter<>() {
    @Override
    public void emit(final V value) {
      if (evictionListener != null) {
        evictionListener.accept(value);
      }
    }
  };

  private final ReentrantLock lock = new ReentrantLock();

  private int minFrequency = -1;
  private Consumer<V> evictionListener;

  public LFUCache(int capacity) {
    this.data = new HashMap<>();
    this.frequencies = new HashMap<>();
    this.usageCounts = new HashMap<>();

    if (capacity <= 0) {
      this.capacity = DEFAULT_CAPACITY;
    } else {
      this.capacity = capacity;
    }
  }

  public LFUCache() {
    this.data = new HashMap<>();
    this.frequencies = new HashMap<>();
    this.usageCounts = new HashMap<>();
    this.capacity = DEFAULT_CAPACITY;
  }

  public void setEvictionListener(final Consumer<V> evictionListener) {
    this.evictionListener = evictionListener;
  }

  @Override
  public Optional<V> get(final K key) {
    if (!data.containsKey(key)) {
      return Optional.empty();
    }

    lock.lock();
    try {
      final int freq = frequencies.get(key);
      final int nextFreq = freq + 1;

      frequencies.put(key, nextFreq);

      usageCounts.get(freq).remove(key);
      if (freq == minFrequency && usageCounts.get(freq).size() == 0) {
        minFrequency++;
      }

      if (!usageCounts.containsKey(nextFreq)) {
        usageCounts.put(nextFreq, new LinkedHashSet<>());
      }

      usageCounts.get(nextFreq).add(key);
      return Optional.ofNullable(data.get(key));
    } finally {
      lock.unlock();
    }
  }

  @Override
  public V put(final K key, final V value) {
    lock.lock();
    try {
      if (data.containsKey(key)) {
        data.put(key, value);
        return get(key).orElseThrow(() -> new RuntimeException("Error during data insertion!"));
      }

      if (data.size() >= capacity) {
        final K evictCandidateKey = usageCounts.get(minFrequency).iterator().next();
        usageCounts.get(minFrequency).remove(evictCandidateKey);
        final V evictedData = data.remove(evictCandidateKey);
        frequencies.remove(evictCandidateKey);
        onValueEvicted.emit(evictedData);
      }

      data.put(key, value);
      frequencies.put(key, 1);
      minFrequency = 1;
      usageCounts.get(1).add(key);
      return value;
    } finally {
      lock.unlock();
    }
  }
}
