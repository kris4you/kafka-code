package com.example.demo;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

public class ConsumerDemoWithThreads {
   static final Logger logger= LoggerFactory.getLogger(ConsumerDemoWithThreads.class);

     static class ConsumerRunnable implements Runnable{

        CountDownLatch latch;
        KafkaConsumer consumer;
        ConsumerRunnable(String bootservers, String groupId, String topic, CountDownLatch latch){
            Properties properties=new Properties();
            properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,bootservers);
            properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
            consumer=new KafkaConsumer(properties);
            consumer.subscribe(Arrays.asList(topic));
            this.latch=latch;
        }
        @Override
        public void run() {
            try {
                while (true) {

                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord record : records) {
                        logger.info("record key{},value{},partion{},offset{},topic{}", record.key(), record.value(),
                                record.partition(), record.offset(), record.topic());
                    }
                }
            }catch(WakeupException exception){
                logger.info("received signal");
            }finally {
                logger.info("closing consumer");
                consumer.close();
                latch.countDown();
            }

        }
        public void shutDown(){
            logger.info("Calling in shutdown method");
            consumer.wakeup();
        }
    }
    public static void main(String[] args) throws InterruptedException {

        CountDownLatch latch=new CountDownLatch(1);
        Runnable consumerRunnable=new ConsumerRunnable("127.0.0.1:9092","fifth_group","first_topic",latch);
        Thread t=new Thread(consumerRunnable);
        t.start();

//

        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            logger.info("entering into shutdownhook");
            ((ConsumerRunnable) consumerRunnable).shutDown();
            try {
                latch.await();
            } catch (InterruptedException e) {
                logger.info("entering into shutdownhook");
            }
        }));
        try {
            latch.await();
        }catch(Exception e){
            logger.info("Shutting down the thread");
        }
        logger.info("Application exited");
    }
}
