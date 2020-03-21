package com.easydarwin.pushstream;

import android.support.annotation.NonNull;

import org.easydarwin.util.AbstractSubscriber;
import org.reactivestreams.Publisher;

import io.reactivex.Single;
import io.reactivex.subjects.PublishSubject;

/**
 * RxHelper
 *
 * @author levi
 * @date 2020-3-20
 */
public class RxHelper {

    public static <T> Single<T> single(@NonNull Publisher<T> t, @NonNull T defaultValueIfNotNull) {
        if (defaultValueIfNotNull != null) return Single.just(defaultValueIfNotNull);
        final PublishSubject sub = PublishSubject.create();
        t.subscribe(new AbstractSubscriber<T>() {
            @Override
            public void onNext(T t) {
                super.onNext(t);
                sub.onNext(t);
            }

            @Override
            public void onError(Throwable t) {
                super.onError(t);
                sub.onComplete();
            }

            @Override
            public void onComplete() {
                super.onComplete();
                sub.onComplete();
            }
        });
        return sub.firstOrError();
    }
}
