@FunctionalInterface
public interface EventEmitter<T> {
  void emit(final T value);
}
