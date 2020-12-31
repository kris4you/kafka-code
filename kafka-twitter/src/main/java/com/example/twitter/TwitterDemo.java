package com.example.twitter;


import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


class KafkaProducerClient{
    static final Logger logger = LoggerFactory.getLogger(KafkaProducerClient.class);
     private KafkaProducer<String,String> kafkaProducer;
     private String topic;

    KafkaProducerClient(String bootStrapServers, String topic) {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootStrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,"true");
        properties.put(ProducerConfig.ACKS_CONFIG,"all");
        properties.put(ProducerConfig.RETRIES_CONFIG,String.valueOf(Integer.MAX_VALUE));
        properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,"5");
        properties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,"snappy");
        properties.put(ProducerConfig.BATCH_SIZE_CONFIG,"32768");
        properties.put(ProducerConfig.LINGER_MS_CONFIG,"20");
        kafkaProducer = new KafkaProducer<>(properties);
        this.topic = topic;

    }

    public void sendMessage(String message){

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, null, message);

        kafkaProducer.send(record,(recordMetadata,e)->{

            if(e==null){
                logger.info("offset {},topic {},partition {},timestamp{}",
                        recordMetadata.offset(),recordMetadata.topic(),recordMetadata.partition(),recordMetadata.timestamp());
            }
            else{
                logger.error("Error",e);
            }
        });
    }
    public void shutdown(){
        kafkaProducer.close();
    }
}
class TwitterClient {

    static final Logger logger = LoggerFactory.getLogger(TwitterClient.class);
    public static final String CONSUMER_KEY = "WvA0i4LkdqUmU1mYBGSJdv0LH";
    public static final String CONSUMER_SECRET = "xEVWn9PoaPbSY3P7mj7bMmpspgM0L7ClHsvHOZCqoWalaP5ZeA";
    public static final String TOKEN = "1341877305162027009-b85VO9MQgoCGnIle7ellQp0hiBQxp8";
    public static final String SECRET = "r9UJ2YrC4lqUjixZZx1OE81zS7FogHkKhjrr7eo2lLEzg";



    public Client getClient(BlockingQueue<String> msgQueue) {
        logger.info("enter the client method");
        Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();
        List<String> terms = Lists.newArrayList("kafka","sports");
        hosebirdEndpoint.trackTerms(terms);
        Authentication hosebirdAuth = new OAuth1(CONSUMER_KEY, CONSUMER_SECRET, TOKEN, SECRET);
        ClientBuilder builder = new ClientBuilder()
                .name("Hosebird-Client-01")
                .hosts(hosebirdHosts)
                .authentication(hosebirdAuth)
                .endpoint(hosebirdEndpoint)
                .processor(new StringDelimitedProcessor(msgQueue));

        return builder.build();
    }

    public void shutdown(Client client) {
        logger.info("shutting down client method");
        client.stop();
    }
}

public class TwitterDemo {

    static final Logger logger = LoggerFactory.getLogger(TwitterDemo.class);
    public static void main(String[] args) {
        BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(100000);
        TwitterClient twitterClient = new TwitterClient();
        KafkaProducerClient producerClient=new KafkaProducerClient("127.0.0.1:9092", "twitter_tweets");
        Client client = twitterClient.getClient(msgQueue);
        client.connect();
        logger.info("Connected to Client");
        Runtime.getRuntime().addShutdownHook(new Thread(()-> {
            logger.info("Shutting down Client");
            twitterClient.shutdown(client);
            producerClient.shutdown();
        }
        ));
        while (!client.isDone()) {
            try {
                String msg = msgQueue.poll(5, TimeUnit.SECONDS);
                if(msg!=null)
                producerClient.sendMessage(msg);
            } catch (InterruptedException e) {
                client.stop();
                logger.error("Error", e);
            }
        }
        logger.info(" completed Client");
    }

}

