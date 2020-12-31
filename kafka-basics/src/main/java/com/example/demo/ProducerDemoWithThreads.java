package com.example.demo;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ProducerDemoWithThreads {

    static final Logger logger = LoggerFactory.getLogger(ProducerDemoWithThreads.class);

    static class ProducerRunnable implements Runnable {
        KafkaProducer<String, String> kafkaProducer;
        CountDownLatch latch;
        String topic;

        ProducerRunnable(String bootStrapServers, String topic, CountDownLatch latch) {
            Properties properties = new Properties();
            properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapServers);
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            kafkaProducer = new KafkaProducer<>(properties);
            this.topic = topic;
            this.latch = latch;
        }

        @Override
        public void run() {
            try {
                int i = 0;
                while (true) {
                    ProducerRecord<String, String> record = new ProducerRecord<>(topic, "World" + i, "Fuck" + i);
                    i++;
                    kafkaProducer.send(record,(recordMetadata,e)->{

                        if(e==null){
                            logger.info("offset {},topic {},partition {},timestamp{}",
                                    recordMetadata.offset(),recordMetadata.topic(),recordMetadata.partition(),recordMetadata.timestamp());
                        }
                        else{
                            logger.error("Error",e);
                        }
                    });
                    if (i % 10 == 0)
                        kafkaProducer.flush();
                }
            } catch (Exception e) {
                logger.info("In last exception producer not avialbe");
                latch.countDown();
            }

        }

        public void shutdown() {
            kafkaProducer.close();
        }
    }


    public static void main(String[] args) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        ProducerRunnable runnable = new ProducerRunnable("127.0.0.1:9092", "first_topic", latch);
        Thread t = new Thread(runnable);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Entering in shutdown method");
            runnable.shutdown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        latch.await();
        logger.info("Application is closed");

    }
}
