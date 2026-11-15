package fr.tp.inf112.projects.robotsim.app;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.errors.WakeupException;

public class FactorySimulationEventConsumer {
	private static final Logger LOGGER = Logger.getLogger(FactorySimulationEventConsumer.class.getName());

	private final KafkaConsumer<String, String> consumer;
	private final RemoteSimulatorController controller;
	private volatile boolean consuming = true;
	private final CountDownLatch assigned = new CountDownLatch(1);
	private final CountDownLatch started = new CountDownLatch(1);
	private volatile RuntimeException failure;

	public FactorySimulationEventConsumer(final RemoteSimulatorController controller) {
		this.controller = controller;

		final Properties properties = new Properties();
		properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
		properties.put(ConsumerConfig.GROUP_ID_CONFIG, "Factory-Simulation-" + UUID.randomUUID());
		properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
		properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

		consumer = new KafkaConsumer<>(properties);
		consumer.subscribe(Collections.singletonList("simulation-" + controller.getCanvas().getId()));
	}

	public void awaitAssignment() throws InterruptedException {
		if (!assigned.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("Kafka topic assignment timed out");
		}
		if (failure != null) {
			throw failure;
		}
	}

	public void start() {
		started.countDown();
	}

	public void stop() {
		consuming = false;
		started.countDown();
		consumer.wakeup();
	}

	public void consumeMessages() {
		try {
			while (consumer.assignment().isEmpty()) {
				consumer.poll(Duration.ofMillis(100));
			}
			consumer.seekToEnd(consumer.assignment());
			for (final var partition : consumer.assignment()) {
				consumer.position(partition);
			}
			assigned.countDown();
			started.await();
			while (consuming && controller.isAnimationRunning()) {
				final ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
				if (consuming && !records.isEmpty()) {
					ConsumerRecord<String, String> last = null;
					for (final ConsumerRecord<String, String> record : records) {
						last = record;
					}
					LOGGER.fine(() -> "Received simulation event for " + controller.getCanvas().getId());
					controller.setCanvas(last.value());
				}
			}
		}
		catch (final WakeupException exception) {
			if (consuming) {
				throw exception;
			}
		}
		catch (final InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
		catch (final RuntimeException exception) {
			failure = exception;
			throw exception;
		}
		finally {
			assigned.countDown();
			consumer.close();
		}
	}
}
