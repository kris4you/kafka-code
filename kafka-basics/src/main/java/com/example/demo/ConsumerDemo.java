package com.example.demo;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

public class ConsumerDemo {

    public static void main(String[] args) {
        Logger logger= LoggerFactory.getLogger(ConsumerDemo.class);
        Properties properties=new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,"127.0.0.1:9092");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "fourth");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
        KafkaConsumer consumer=new KafkaConsumer(properties);
        consumer.subscribe(Arrays.asList("first_topic"));

        while(true){

           ConsumerRecords<String,String> records=consumer.poll(Duration.ofMillis(100));
           for(ConsumerRecord record:records) {
               logger.info("record key{},value{},partion{},offset{},topic{}", record.key(),record.value(),
                       record.partition(),record.offset(),record.topic());
           }
        }
    }
}
