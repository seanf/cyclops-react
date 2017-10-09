package cyclops.control;

import com.aol.cyclops2.hkt.Higher;
import com.aol.cyclops2.hkt.Higher2;
import cyclops.control.Maybe.Nothing;
import cyclops.monads.Witness;
import cyclops.monads.Witness.state;
import cyclops.monads.Witness.supplier;
import cyclops.typeclasses.*;
import cyclops.typeclasses.comonad.Comonad;
import cyclops.typeclasses.foldable.Foldable;
import cyclops.typeclasses.foldable.Unfoldable;
import cyclops.typeclasses.free.Free;
import cyclops.function.*;
import cyclops.typeclasses.functor.Functor;
import cyclops.typeclasses.monad.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import cyclops.collections.tuple.Tuple;
import cyclops.collections.tuple.Tuple2;

import java.util.function.BiFunction;
import java.util.function.Function;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class State<S, T> implements Higher2<state,S,T> {


    private final Function1<S, Free<supplier,Tuple2<S, T>>> runState;


    public Tuple2<S, T> run(S s) {
        return Function0.run(runState.apply(s));
    }
    public T eval(S s) {
        return Function0.run(runState.apply(s))._2();
    }
    public static <S> State<S, S> get() {
        return state(s -> Tuple.tuple(s, s));
    }

    public static <S> State<S, Nothing> transition(Function<? super S,? extends S> f) {
        return state(s -> Tuple.tuple(f.apply(s),(Nothing) Maybe.nothing()));
    }

    public static <S, T> State<S, T> transition(Function<? super S,? extends S> f, T value) {
        return state(s -> Tuple.tuple(f.apply(s),value));
    }
    public <T2, R> State<S, R> combine(State<S, T2> combine, BiFunction<? super T, ? super T2, ? extends R> combiner) {
        return flatMap(a -> combine.map(b -> combiner.apply(a,b)));
    }

    public <R> State<S, R> map(Function<? super T,? extends R> mapper) {
        return mapState(t -> Tuple.tuple(t._1(), mapper.apply(t._2())));
    }
    public <R> State<S, R> mapState(Function<Tuple2<S,T>, Tuple2<S, R>> fn) {
        return suspended(s -> runState.apply(s).map(t -> fn.apply(t)));
    }
    private static <S, T> State<S, T> suspended(Function1<? super S, Free<supplier,Tuple2<S, T>>> runF) {
        return new State<>(s -> Function0.suspend(Lambda.λK(()->runF.apply(s))));
    }

    public <R> State<S, R> flatMap(Function<? super T,? extends  State<S, R>> f) {
        return suspended(s -> runState.apply(s).flatMap(t -> Free.done(f.apply(t._2()).run(t._1()))));
    }
    public static <S, T> State<S, T> constant(T constant) {
        return state(s -> Tuple.tuple(s, constant));
    }

    /*
  * Perform a For Comprehension over a State, accepting 3 generating function.
          * This results in a four level nested internal iteration over the provided States.
   *
           *  <pre>
   * {@code
   *
   *   import static com.aol.cyclops2.reactor.States.forEach4;
   *
      forEach4(State.just(1),
              a-> State.just(a+1),
              (a,b) -> State.<Integer>just(a+b),
              a                  (a,b,c) -> State.<Integer>just(a+b+c),
              Tuple::tuple)
   *
   * }
   * </pre>
          *
          * @param value1 top level State
   * @param value2 Nested State
   * @param value3 Nested State
   * @param value4 Nested State
   * @param yieldingFunction Generates a result per combination
   * @return State with a combined value generated by the yielding function
   */
    public  <R1, R2, R3, R4> State<S,R4> forEach4(Function<? super T, ? extends State<S,R1>> value2,
                                                  BiFunction<? super T, ? super R1, ? extends State<S,R2>> value3,
                                                  Function3<? super T, ? super R1, ? super R2, ? extends State<S,R3>> value4,
                                                  Function4<? super T, ? super R1, ? super R2, ? super R3, ? extends R4> yieldingFunction) {


        return this.flatMap(in -> {

            State<S,R1> a = value2.apply(in);
            return a.flatMap(ina -> {
                State<S,R2> b = value3.apply(in,ina);
                return b.flatMap(inb -> {

                    State<S,R3> c = value4.apply(in,ina,inb);

                    return c.map(in2 -> {

                        return yieldingFunction.apply(in, ina, inb, in2);

                    });

                });


            });


        });

    }



    /**
     * Perform a For Comprehension over a State, accepting 2 generating function.
     * This results in a three level nested internal iteration over the provided States.
     *
     *  <pre>
     * {@code
     *
     *   import static com.aol.cyclops2.reactor.States.forEach3;
     *
    forEach3(State.just(1),
    a-> State.just(a+1),
    (a,b) -> State.<Integer>just(a+b),
    Tuple::tuple)
     *
     * }
     * </pre>
     *
     * @param value1 top level State
     * @param value2 Nested State
     * @param value3 Nested State
     * @param yieldingFunction Generates a result per combination
     * @return State with a combined value generated by the yielding function
     */
    public <R1, R2, R4> State<S,R4> forEach3(Function<? super T, ? extends State<S,R1>> value2,
                                             BiFunction<? super T, ? super R1, ? extends State<S,R2>> value3,
                                             Function3<? super T, ? super R1, ? super R2, ? extends R4> yieldingFunction) {

        return this.flatMap(in -> {

            State<S,R1> a = value2.apply(in);
            return a.flatMap(ina -> {
                State<S,R2> b = value3.apply(in,ina);
                return b.map(in2 -> {
                    return yieldingFunction.apply(in, ina, in2);

                });



            });

        });

    }


    /**
     * Perform a For Comprehension over a State, accepting a generating function.
     * This results in a two level nested internal iteration over the provided States.
     *
     *  <pre>
     * {@code
     *
     *   import static com.aol.cyclops2.reactor.States.forEach;
     *
    forEach(State.just(1),
    a-> State.just(a+1),
    Tuple::tuple)
     *
     * }
     * </pre>
     *
     * @param value1 top level State
     * @param value2 Nested State
     * @param yieldingFunction Generates a result per combination
     * @return State with a combined value generated by the yielding function
     */
    public <R1, R4> State<S,R4> forEach2(Function<? super T, State<S,R1>> value2,
                                         BiFunction<? super T, ? super R1, ? extends R4> yieldingFunction) {

        return this.flatMap(in -> {

            State<S,R1> a = value2.apply(in);
            return a.map(in2 -> {
                return yieldingFunction.apply(in, in2);

            });

        });

    }
    public static <S,T,R> State<S, R> tailRec(T initial, Function<? super T, ? extends  State<S,  ? extends Xor<T, R>>> fn) {
        return narrowK( State.Instances.<S> monadRec().tailRec(initial, fn));

    }


    public static <S, T> State<S, T> state(Function<? super S,? extends Tuple2<S, T>> runF) {

        return new State<>(s -> Free.done(runF.apply(s)));
    }

    public static <S> State<S, Nothing> of(S s) {
        return state(__ -> Tuple.tuple(s, (Nothing)Maybe.nothing()));
    }
    public static <S> State<S, Nothing> put(S s) {
        return of(s);
    }

    public static <W1,T,S> Nested<Higher<state,S>,W1,T> nested(State<S,Higher<W1,T>> nested, S value,InstanceDefinitions<W1> def2){
        return Nested.of(nested, Instances.definitions(value),def2);
    }
    public <W1> Product<Higher<state,S>,W1,T> product(S value,Active<W1,T> active){
       return Product.of(allTypeclasses(value), active);
    }
    public <W1> Coproduct<W1,Higher<state,S>,T> coproduct(S value,InstanceDefinitions<W1> def2){
        return Coproduct.right(this,def2,Instances.definitions(value));
    }

    public Active<Higher<state,S>,T> allTypeclasses(S value){
        return Active.of(this, Instances.definitions(value));
    }

    public <W2,R> Nested<Higher<Witness.state,S>,W2,R> mapM(S value, Function<? super T,? extends Higher<W2,R>> fn, InstanceDefinitions<W2> defs){
        return Nested.of(map(fn), Instances.definitions(value), defs);
    }
    public static <S,T> State<S,T> narrowK2(final Higher2<state, S,T> t) {
        return (State<S,T>)t;
    }
    public static <S,T> State<S,T> narrowK(final Higher<Higher<state, S>,T> t) {
        return (State)t;
    }
    public static class Instances {

        public static <S> InstanceDefinitions<Higher<state, S>> definitions(S val){
            return new InstanceDefinitions<Higher<state, S>>() {

                @Override
                public <T, R> Functor<Higher<state, S>> functor() {
                    return Instances.functor();
                }

                @Override
                public <T> Pure<Higher<state, S>> unit() {
                    return Instances.unit();
                }

                @Override
                public <T, R> Applicative<Higher<state, S>> applicative() {
                    return Instances.applicative();
                }

                @Override
                public <T, R> Monad<Higher<state, S>> monad() {
                    return Instances.monad();
                }

                @Override
                public <T, R> Maybe<MonadZero<Higher<state, S>>> monadZero() {
                    return Maybe.nothing();
                }

                @Override
                public <T> Maybe<MonadPlus<Higher<state, S>>> monadPlus() {
                    return Maybe.nothing();
                }

                @Override
                public <T> MonadRec<Higher<state, S>> monadRec() {
                    return Instances.monadRec();
                }

                @Override
                public <T> Maybe<MonadPlus<Higher<state, S>>> monadPlus(Monoid<Higher<Higher<state, S>, T>> m) {
                    return Maybe.nothing();
                }

                @Override
                public <C2, T> Traverse<Higher<state, S>> traverse() {
                    return Instances.traverse(val);
                }

                @Override
                public <T> Foldable<Higher<state, S>> foldable() {
                    return Instances.foldable(val);
                }

                @Override
                public <T> Maybe<Comonad<Higher<state, S>>> comonad() {
                    return Maybe.nothing();
                }

                @Override
                public <T> Maybe<Unfoldable<Higher<state, S>>> unfoldable() {
                    return Maybe.nothing();
                }
            };
        }
        public static <S> Functor<Higher<state, S>> functor() {
            return new Functor<Higher<state, S>>() {
                @Override
                public <T, R> Higher<Higher<state, S>, R> map(Function<? super T, ? extends R> fn, Higher<Higher<state, S>, T> ds) {
                    return narrowK(ds).map(fn);
                }
            };
        }
        public static <S> Pure<Higher<state, S>> unit() {
            return new Pure<Higher<state, S>>() {

                @Override
                public <T> Higher<Higher<state, S>, T> unit(T value) {
                    return State.constant(value);
                }
            };
        }
        public static <S> Applicative<Higher<state, S>> applicative() {
            return new Applicative<Higher<state, S>>() {

                @Override
                public <T, R> Higher<Higher<state, S>, R> ap(Higher<Higher<state, S>, ? extends Function<T, R>> fn, Higher<Higher<state, S>, T> apply) {
                    State<S, ? extends Function<T, R>> f = narrowK(fn);
                    State<S, T> ap = narrowK(apply);
                    return f.flatMap(fn1->ap.map(a->fn1.apply(a)));
                }

                @Override
                public <T, R> Higher<Higher<state, S>, R> map(Function<? super T, ? extends R> fn, Higher<Higher<state, S>, T> ds) {
                    return Instances.<S>functor().map(fn,ds);
                }

                @Override
                public <T> Higher<Higher<state, S>, T> unit(T value) {
                    return Instances.<S>unit().unit(value);
                }
            };
        }
        public static <S> Monad<Higher<state, S>> monad() {
            return new Monad<Higher<state, S>>() {


                @Override
                public <T, R> Higher<Higher<state, S>, R> ap(Higher<Higher<state, S>, ? extends Function<T, R>> fn, Higher<Higher<state, S>, T> apply) {
                    return Instances.<S>applicative().ap(fn,apply);
                }

                @Override
                public <T, R> Higher<Higher<state, S>, R> map(Function<? super T, ? extends R> fn, Higher<Higher<state, S>, T> ds) {
                    return Instances.<S>functor().map(fn,ds);
                }

                @Override
                public <T> Higher<Higher<state, S>, T> unit(T value) {
                    return Instances.<S>unit().unit(value);
                }

                @Override
                public <T, R> Higher<Higher<state, S>, R> flatMap(Function<? super T, ? extends Higher<Higher<state, S>, R>> fn, Higher<Higher<state, S>, T> ds) {
                    return narrowK(ds).flatMap(fn.andThen(h->narrowK(h)));
                }
            };
        }
        public static <S> Traverse<Higher<state, S>> traverse(S defaultValue) {
            return new Traverse<Higher<state, S>>() {
                @Override
                public <C2, T, R> Higher<C2, Higher<Higher<state, S>, R>> traverseA(Applicative<C2> applicative, Function<? super T, ? extends Higher<C2, R>> fn, Higher<Higher<state, S>, T> ds) {
                    State<S, T> s = narrowK(ds);
                    Higher<C2, R> x = fn.apply(s.eval(defaultValue));
                    return applicative.map(r->State.constant(r),x);
                }

                @Override
                public <C2, T> Higher<C2, Higher<Higher<state, S>, T>> sequenceA(Applicative<C2> applicative, Higher<Higher<state, S>, Higher<C2, T>> ds) {
                    return traverseA(applicative,Function.identity(),ds);
                }

                @Override
                public <T, R> Higher<Higher<state, S>, R> ap(Higher<Higher<state, S>, ? extends Function<T, R>> fn, Higher<Higher<state, S>, T> apply) {
                        return Instances.<S>applicative().ap(fn,apply);
                }

                @Override
                public <T> Higher<Higher<state, S>, T> unit(T value) {
                    return Instances.<S>unit().unit(value);
                }

                @Override
                public <T, R> Higher<Higher<state, S>, R> map(Function<? super T, ? extends R> fn, Higher<Higher<state, S>, T> ds) {
                    return Instances.<S>functor().map(fn,ds);
                }
            };
        }

        public static <S> Foldable<Higher<state,S>> foldable(S val) {
            return new Foldable<Higher<state, S>>() {


                @Override
                public <T> T foldRight(Monoid<T> monoid, Higher<Higher<state, S>, T> ds) {
                    return monoid.foldRight(narrowK(ds).eval(val));

                }

                @Override
                public <T> T foldLeft(Monoid<T> monoid, Higher<Higher<state, S>, T> ds) {
                    return monoid.foldLeft(narrowK(ds).eval(val));
                }

                @Override
                public <T, R> R foldMap(Monoid<R> mb, Function<? super T, ? extends R> fn, Higher<Higher<state, S>, T> nestedA) {
                    return foldLeft(mb,narrowK(nestedA).<R>map(fn));
                }
            };
        }
        public static <S> MonadRec<Higher<state,S>> monadRec() {
            return new MonadRec<Higher<state,S>>() {
                @Override
                public <T, R> Higher<Higher<state, S>, R> tailRec(T initial, Function<? super T, ? extends Higher<Higher<state, S>, ? extends Xor<T, R>>> fn) {
                    return narrowK(fn.apply(initial)).flatMap( eval ->
                            eval.visit(s->narrowK(tailRec(s,fn)),p->State.constant(p)));
                }
            };
        }


    }
    public static  <S,T> Kleisli<Higher<state, S>,State<S,T>,T> kindKleisli(){
        return Kleisli.of(Instances.monad(), State::widen);
    }
    public static <S,T> Higher<Higher<state, S>, T> widen(State<S,T> narrow) {
        return narrow;
    }
    public static  <S,T> Cokleisli<Higher<state, S>,T,State<S,T>> kindCokleisli(){
        return Cokleisli.of(State::narrowK);
    }
}



