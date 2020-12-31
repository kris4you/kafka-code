package com.example.demo;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

public class Producer {



    public static void main(String[] args) {

        Logger logger= LoggerFactory.getLogger(Producer.class);
        Properties properties=new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,"127.0.0.1:9092");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String,String> kafkaProducer=new KafkaProducer<>(properties);


        IntConsumer myfun= i->{
            ProducerRecord<String,String>record=new ProducerRecord<>("first_topic","World"+i,"Fuck"+i);
            kafkaProducer.send(record,(recordMetadata,e)->{

                if(e==null){
                    logger.info("offset {},topic {},partition {},timestamp{}",
                            recordMetadata.offset(),recordMetadata.topic(),recordMetadata.partition(),recordMetadata.timestamp());
                }
                else{
                    logger.error("Error",e);
                }
            });
        };
        IntStream.range(0,10).forEach(myfun);
        kafkaProducer.close();


    }
}
