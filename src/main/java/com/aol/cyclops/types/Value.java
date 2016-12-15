package com.aol.cyclops.types;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import com.aol.cyclops.Monoid;
import com.aol.cyclops.Reducer;
import com.aol.cyclops.control.Eval;
import com.aol.cyclops.control.Ior;
import com.aol.cyclops.control.LazyReact;
import com.aol.cyclops.control.Maybe;
import com.aol.cyclops.control.ReactiveSeq;
import com.aol.cyclops.control.SimpleReact;
import com.aol.cyclops.control.Try;
import com.aol.cyclops.control.Xor;
import com.aol.cyclops.data.LazyImmutable;
import com.aol.cyclops.data.Mutable;
import com.aol.cyclops.data.collections.extensions.persistent.PBagX;
import com.aol.cyclops.data.collections.extensions.persistent.POrderedSetX;
import com.aol.cyclops.data.collections.extensions.persistent.PQueueX;
import com.aol.cyclops.data.collections.extensions.persistent.PSetX;
import com.aol.cyclops.data.collections.extensions.persistent.PStackX;
import com.aol.cyclops.data.collections.extensions.persistent.PVectorX;
import com.aol.cyclops.data.collections.extensions.standard.DequeX;
import com.aol.cyclops.data.collections.extensions.standard.ListX;
import com.aol.cyclops.data.collections.extensions.standard.QueueX;
import com.aol.cyclops.data.collections.extensions.standard.SetX;
import com.aol.cyclops.data.collections.extensions.standard.SortedSetX;
import com.aol.cyclops.types.futurestream.LazyFutureStream;
import com.aol.cyclops.types.futurestream.SimpleReactStream;
import com.aol.cyclops.types.stream.reactive.ValueSubscriber;
import com.aol.cyclops.util.function.Predicates;

import lombok.AllArgsConstructor;

/**
 * A data type that stores at most 1 Values
 * 
 * @author johnmcclean
 *
 * @param <T> Data type of element in this value
 */
@FunctionalInterface
public interface Value<T> extends Supplier<T>, 
                                    Foldable<T>, 
                                    Convertable<T>, 
                                    Publisher<T>, 
                                    Predicate<T>, 
                                    Zippable<T>{

    /* An Iterator over the list returned from toList()
     * 
     *  (non-Javadoc)
     * @see java.lang.Iterable#iterator()
     */
    @Override
    default Iterator<T> iterator() {
        return Convertable.super.iterator();
    }

    /* (non-Javadoc)
     * @see java.util.function.Predicate#test(java.lang.Object)
     */
    @Override
    default boolean test(final T t) {
        if (!(t instanceof Value))
            return Predicates.eqv(Maybe.ofNullable(t))
                             .test(this);
        else
            return Predicates.eqv((Value) t)
                             .test(this);

    }

    /**
     * @return A factory class generating Values from reactive-streams Subscribers
     */
    default ValueSubscriber<T> newSubscriber() {
        return ValueSubscriber.subscriber();
    }

    /* (non-Javadoc)
     * @see org.reactivestreams.Publisher#subscribe(org.reactivestreams.Subscriber)
     */
    @Override
    default void subscribe(final Subscriber<? super T> sub) {
        sub.onSubscribe(new Subscription() {

            AtomicBoolean running = new AtomicBoolean(
                                                      true);
            AtomicBoolean cancelled = new AtomicBoolean(false);

            @Override
            public void request(final long n) {

                if (n < 1) {
                    sub.onError(new IllegalArgumentException(
                                                             "3.9 While the Subscription is not cancelled, Subscription.request(long n) MUST throw a java.lang.IllegalArgumentException if the argument is <= 0."));
                }

                if (!running.compareAndSet(true, false)) {

                    return;
                }
                try {
                    T value = get();
                    if(!cancelled.get())
                        sub.onNext(get());

                } catch (final Throwable t) {
                    sub.onError(t);

                }
                try {
                    sub.onComplete();

                } finally {

                }

            }

            @Override
            public void cancel() {

                cancelled.set(true);

            }

        });

    }

    /**
     * Construct a generic Value from the provided Supplier 
     * 
     * @param supplier Value supplier
     * @return Value wrapping a value that can be generated from the provided Supplier
     */
    public static <T> Value<T> of(final Supplier<T> supplier) {
        return new ValueImpl<T>(
                                supplier);
    }

    @AllArgsConstructor
    public static class ValueImpl<T> implements Value<T> {
        private final Supplier<T> delegate;

        @Override
        public T get() {
            return delegate.get();
        }

        @Override
        public Iterator<T> iterator() {
            return stream().iterator();
        }
    }

    /* (non-Javadoc)
     * @see com.aol.cyclops.types.Foldable#stream()
     */
    @Override
    default ReactiveSeq<T> stream() {
        return ReactiveSeq.of(Try.withCatch(() -> get(), NoSuchElementException.class))
                          .filter(Try::isSuccess)
                          .map(Try::get);
    }



    /**
     * Use the value stored in this Value to seed a Stream generated from the provided function
     * 
     * @param fn Function to generate a Stream
     * @return Stream generated from a seed value (the Value stored in this Value) and the provided function
     */
    default ReactiveSeq<T> iterate(final UnaryOperator<T> fn) {
        return ReactiveSeq.iterate(get(), fn);
    }

    /**
     * @return A Stream that repeats the value stored in this Value over and over
     */
    default ReactiveSeq<T> generate() {
        return ReactiveSeq.generate(this);
    }


    /**
     * @return Primary Xor that has the same value as this Value
     */
    default  Xor<?, T> toXor() {
        if (this instanceof Xor)
            return (Xor) this;
        final Optional<T> o = toOptional();
        return o.isPresent() ? Xor.primary(o.get()) : Xor.secondary(null);

    }

    /**
     * Convert to an Xor where the secondary value will be used if no primary value is present
     * 
    * @param secondary Value to use in case no primary value is present
    * @return Primary Xor with same value as this Value, or a Secondary Xor with the provided Value if this Value is empty
    */
    default <ST> Xor<ST, T> toXor(final ST secondary) {
        final Optional<T> o = toOptional();
        return o.isPresent() ? Xor.primary(o.get()) : Xor.secondary(secondary);
    }

    /**
     * @param throwable Exception to use if this Value is empty
     * @return Try that has the same value as this Value or the provided Exception
     */
    default <X extends Throwable> Try<T, X> toTry(final X throwable) {
        return toXor().visit(secondary -> Try.failure(throwable), primary -> Try.success(primary));

    }

    /**
     * @return This Value converted to a Try. If this Value is empty the Try will contain a NoSuchElementException
     */
    default Try<T, Throwable> toTry() {
        return toXor().visit(secondary -> Try.failure(new NoSuchElementException()), primary -> Try.success(primary));

    }

    /**
     * Convert this Value to a Try that will catch the provided exception types on subsequent operations
     * 
     * @param classes Exception classes to catch on subsequent operations
     * @return This Value to converted to a Try.
     */
    default <X extends Throwable> Try<T, X> toTry(final Class<X>... classes) {
        return Try.withCatch(() -> get(), classes);
    }

    
    /**
     * Return an Ior that can be this object or a Ior.primary or Ior.secondary
     * @return new Ior 
     */
    default  Ior<?, T> toIor() {
        if (this instanceof Ior)
            return (Ior) this;
        final Optional<T> o = toOptional();
        return o.isPresent() ? Ior.primary(o.get()) : Ior.secondary(null);
    }

    

    /**
     * Return the value, evaluated right now.
     * @return value evaluated from this object.
     */
    default Eval<T> toEvalNow() {
        return Eval.now(get());
    }

    /**
     * Return the value, evaluated later.
     * @return value evaluated from this object.
     */
    default Eval<T> toEvalLater() {
        return Eval.later(this);
    }

    /**
     * Return the value of this object, evaluated always.
     * @return value evaluated from this object.
     */
    default Eval<T> toEvalAlways() {
        return Eval.always(this);
    }

    /**
     * Returns a function result or a supplier result. The first one if the function isn't null and the second one if it is.
     * @return new Maybe with the result of a function or supplier. 
     */
    default Maybe<T> toMaybe() {
        return visit(p -> Maybe.ofNullable(p), () -> Maybe.none());
    }


    /**
     * Returns the class name and the name of the subclass, if there is any value, the value is showed between square brackets.
     * @return String
     */
    default String mkString() {

        if (isPresent())
            return getClass().getSimpleName() + "[" + get() + "]";
        return getClass().getSimpleName() + "[]";
    }



}
