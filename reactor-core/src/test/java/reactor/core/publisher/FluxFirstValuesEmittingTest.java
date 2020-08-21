package reactor.core.publisher;

import java.time.Duration;
import java.util.Arrays;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Scannable;
import reactor.test.StepVerifier;
import reactor.test.subscriber.AssertSubscriber;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FluxFirstValuesEmittingTest {

	@Test
	public void firstSourceEmittingValueIsChosen() {
		StepVerifier.withVirtualTime(() -> Flux.firstValues(
				Flux.just(1, 2, 3).delayElements(Duration.ofMillis(500L)),
				Flux.just(4, 5, 6).delayElements(Duration.ofMillis(1_000L))
		))
				.thenAwait(Duration.ofMillis(1_500L))
				.expectNext(1, 2, 3)
				.verifyComplete();

	}

	@Test
	public void firstSourceEmittingValueIsChosenOverErrorOrCompleteEmpty() {
		StepVerifier.withVirtualTime(() -> Flux.firstValues(
				Flux.just(1, 2, 3).delayElements(Duration.ofMillis(500L)),
				Flux.error(new RuntimeException("Boom!")),
				Flux.empty(),
				Flux.never()
		))
				.thenAwait(Duration.ofMillis(1_500L))
				.expectNext(1, 2, 3)
				.verifyComplete();

	}

	@Test
	public void onlyErrorOrCompleteEmptyEmitsError() {
		StepVerifier.withVirtualTime(() -> Flux.firstValues(
				Flux.error(new RuntimeException("Boom!")),
				Flux.empty()
		))
				.expectErrorSatisfies(e -> {
					assertThat(e).isInstanceOf(NoSuchElementException.class);
					assertThat(e.getMessage()).isEqualTo("All sources completed with error or without values");
					Throwable throwable = e.getSuppressed()[0];
					assertThat(throwable.getSuppressed()[0].getMessage()).isEqualTo("Boom!");
					assertThat(throwable.getSuppressed()[1].getMessage())
							.isEqualTo("source at index 1 completed empty");

				})
				.verify();
	}


	@Test
	public void arrayNull() {
		assertThrows(NullPointerException.class, () -> Flux.firstValues((Publisher<Integer>[]) null));
	}

	@Test
	public void iterableNull() {
		assertThrows(NullPointerException.class, () -> Flux.firstValues((Iterable<Publisher<Integer>>) null));
	}

	@Test
	public void requestAndCancelArePropagated() {
		StepVerifier.withVirtualTime(() -> Flux.firstValues(
				Flux.range(1, 10),
				Flux.range(11, 10)
		))
				.thenRequest(5)
				.expectNext(1, 2, 3, 4, 5)
				.thenCancel()
				.verify(Duration.ofSeconds(1L));
	}

	@Test
	public void singleArrayNullSource() {
		AssertSubscriber<Object> ts = AssertSubscriber.create();

		Flux.firstValues((Publisher<Object>) null)
				.subscribe(ts);

		ts.assertNoValues()
				.assertNotComplete()
				.assertError(NullPointerException.class);
	}

	@Test
	public void arrayOneIsNullSource() {
		AssertSubscriber<Object> ts = AssertSubscriber.create();

		Flux.firstValues(Flux.never(), null, Flux.never())
				.subscribe(ts);

		ts.assertNoValues()
				.assertNotComplete()
				.assertError(NullPointerException.class);
	}

	@Test
	public void singleIterableNullSource() {
		AssertSubscriber<Object> ts = AssertSubscriber.create();

		Flux.firstValues(Arrays.asList((Publisher<Object>) null))
				.subscribe(ts);

		ts.assertNoValues()
				.assertNotComplete()
				.assertError(NullPointerException.class);
	}

	@Test
	public void iterableOneIsNullSource() {
		AssertSubscriber<Object> ts = AssertSubscriber.create();

		Flux.firstValues(Arrays.asList(Flux.never(),
				(Publisher<Object>) null,
				Flux.never()))
				.subscribe(ts);

		ts.assertNoValues()
				.assertNotComplete()
				.assertError(NullPointerException.class);
	}

	@Test
	public void scanOperator() {
		@SuppressWarnings("unchecked")
		FluxFirstValuesEmitting<Integer> test = new FluxFirstValuesEmitting<>(Flux.range(1, 10), Flux.range(11, 10));

		assertThat(test.scan(Scannable.Attr.RUN_STYLE)).isSameAs(Scannable.Attr.RunStyle.SYNC);
	}

	@Test
	public void scanSubscriber() {
		CoreSubscriber<String> actual = new LambdaSubscriber<>(null, e -> {
		}, null, null);
		FluxFirstValuesEmitting.RaceValuesCoordinator<String> parent = new FluxFirstValuesEmitting.RaceValuesCoordinator<>(1);
		FluxFirstValuesEmitting.FirstValuesEmittingSubscriber<String> test = new FluxFirstValuesEmitting.FirstValuesEmittingSubscriber<>(actual, parent, 1);
		Subscription sub = Operators.emptySubscription();
		test.onSubscribe(sub);

		assertThat(test.scan(Scannable.Attr.PARENT)).isSameAs(sub);
		assertThat(test.scan(Scannable.Attr.ACTUAL)).isSameAs(actual);
		assertThat(test.scan(Scannable.Attr.RUN_STYLE)).isSameAs(Scannable.Attr.RunStyle.SYNC);
		assertThat(test.scan(Scannable.Attr.CANCELLED)).isFalse();
		parent.cancelled = true;
		assertThat(test.scan(Scannable.Attr.CANCELLED)).isTrue();
	}

	@Test
	public void scanRaceCoordinator() {
		CoreSubscriber<String> actual = new LambdaSubscriber<>(null, e -> {
		}, null, null);
		FluxFirstValuesEmitting.RaceValuesCoordinator<String> parent = new FluxFirstValuesEmitting.RaceValuesCoordinator<>(1);
		FluxFirstValuesEmitting.FirstValuesEmittingSubscriber<String> test = new FluxFirstValuesEmitting.FirstValuesEmittingSubscriber<>(actual, parent, 1);
		Subscription sub = Operators.emptySubscription();
		test.onSubscribe(sub);

		assertThat(test.scan(Scannable.Attr.PARENT)).isSameAs(sub);
		assertThat(test.scan(Scannable.Attr.ACTUAL)).isSameAs(actual);
		assertThat(parent.scan(Scannable.Attr.CANCELLED)).isFalse();
		parent.cancelled = true;
		assertThat(parent.scan(Scannable.Attr.CANCELLED)).isTrue();
	}

}