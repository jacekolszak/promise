package com.github.jacekolszak.promises;

import java.util.ArrayList;
import java.util.List;

/**
 * Promise is:
 * <ul>
 * <li>a <b>placeholder</b> for a value (or an exception) returned from an <b>asynchronous</b> operation
 * <li>internally a promise has three states: pending, resolved, rejected
 * <li>after a promise is resolved or rejected its state and value can never be changed
 * <li>asynchronous code can be written by chaining promises together
 * <li>exception thrown by a promise is propagated through promise chains
 * </ul>
 */
public class Promise<RESULT> implements Thenable<RESULT> {

    private final List<NextPromise> next = new ArrayList<>();

    private PromiseStatus status = PromiseStatus.PENDING;

    private PromiseValue value;

    /**
     * Construct a new Promise with executor code. Executor should either resolve or reject the promise using
     * the supplied PromiseCallbacks object. Executor can reject a promise also by throwing an exception.
     *
     * @param executor This piece of code is executed immediately. When executor throws an exception then
     *                 the exception is caught and promise is rejected using this exception.
     */
    public Promise(CheckedConsumer<PromiseCallbacks<RESULT>> executor) {
        try {
            executor.accept(new PromiseCallbacks<>(this));
        } catch (Throwable throwable) {
            doReject(throwable);
        }
    }

    Promise() {
    }

    void setResult(Object result) {
        if (!isValueSet()) {
            this.status = PromiseStatus.RESOLVED;
            this.value = new PromiseValue(result);
            fire(result);
        }
    }

    void setException(Throwable e) {
        if (!isValueSet()) {
            this.status = PromiseStatus.REJECTED;
            this.value = new PromiseValue(e);
            fireError(e);
        }
    }

    void doResolvePromise(Thenable<RESULT> promise) {
        promise.thenVoid(this::doResolve);
        promise.catchVoid(this::doReject);
    }

    synchronized void doResolve(RESULT result) {
        if (result instanceof Thenable) {
            doResolvePromise((Thenable<RESULT>) result);
        } else {
            setResult(result);
        }
    }

    synchronized void doReject(Throwable exception) {
        setException(exception);
    }

    @Override
    public synchronized <NEW_RESULT> Promise<NEW_RESULT> then(CheckedFunction<RESULT, NEW_RESULT> callback) {
        SuccessPromise<RESULT, NEW_RESULT> next = new SuccessPromise<>(callback);
        addNext(next);
        fireIfNecessarily();
        return (Promise<NEW_RESULT>) next;
    }

    @Override
    public synchronized <NEW_RESULT> Promise<NEW_RESULT> catchReturn(CheckedFunction<Throwable, NEW_RESULT> callback) {
        ErrorPromise next = new ErrorPromise<>(callback);
        addNext(next);
        fireIfNecessarily();
        return next;
    }

    private void fireIfNecessarily() {
        if (isValueSet()) {
            if (status == PromiseStatus.RESOLVED) {
                fire(value.value);
            } else {
                fireError((Throwable) value.value);
            }
        }
    }

    private void fire(Object result) {
        next.stream().forEach(next -> next.doResolve(result));
    }

    private void fireError(Throwable exception) {
        next.stream().forEach(next -> next.doReject(exception));
    }

    private void addNext(Promise<RESULT> promise) {
        this.next.add(new NextPromise(promise));
    }

    private boolean isValueSet() {
        return value != null;
    }

    @Override
    public String toString() {
        return "Promise(" +
                "status=" + status +
                ", value=" + value + ")";
    }

    /**
     * Create a Promise that resolves passed object immediately.
     *
     * @param promiseOrValue If it is a Promise then resolve the Promise first.
     */
    public static <V> Promise<V> resolve(V promiseOrValue) {
        return new Promise<>(p -> p.resolve(promiseOrValue));
    }

    /**
     * Special case of {@link Promise#resolve(Object)} method which resolves passed Promise. Method created to
     * avoid casting.
     */
    public static <T> Promise<T> resolve(Thenable<T> promise) {
        return new Promise<>(p -> p.resolve((T) promise));
    }

    /**
     * Create a Promise that rejects with passed exception immediately.
     */
    public static <R extends Throwable> Promise<R> reject(R exception) {
        return new Promise<>(p -> p.reject(exception));
    }

    /**
     * Create a Promise that resolves when all of the passed promises have resolved, or rejects with the reason of the
     * first passed promise that rejects.
     */
    public static Promise<Object[]> all(Object... promisesOrValues) {
        return new Promise<>(p -> new PromiseAll(promisesOrValues, p));
    }

    /**
     * Create a Promise that resolves or rejects as soon as one of the promises resolves or rejects, with the value or
     * reason from that promise.
     */
    public static Promise<Object> race(Object... promisesOrValues) {
        return new Promise<>(p -> new PromiseRace(promisesOrValues, p));
    }

}
