package com.example.demo;

import com.google.gson.JsonParser;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

class ElasticClient {
    static final Logger logger = LoggerFactory.getLogger(ElasticClient.class);
    URI connUri = URI.create("https://a857dggkhe:k0j2h2w4za@kafka-course-2870140317.us-east-1.bonsaisearch.net:443");
    String[] auth = connUri.getUserInfo().split(":");
    RestHighLevelClient client;

    public ElasticClient() {
        BasicCredentialsProvider cp = new BasicCredentialsProvider();
        cp.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(auth[0], auth[1]));
        client = new RestHighLevelClient(
                RestClient.builder(new HttpHost(connUri.getHost(), connUri.getPort(), connUri.getScheme()))
                        .setHttpClientConfigCallback(
                                httpAsyncClientBuilder -> httpAsyncClientBuilder.setDefaultCredentialsProvider(cp)
                                        .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())));

    }

    public RestHighLevelClient getClient() {
        return client;
    }

    public void shutDown() throws IOException {
        client.close();
    }
}

class ConsumerClient implements Runnable {

    static final Logger logger = LoggerFactory.getLogger(ConsumerClient.class);
    KafkaConsumer consumer;
    CountDownLatch latch;
    RestHighLevelClient client;
    boolean status = false;

    ConsumerClient(String bootservers, String groupId, String topic, CountDownLatch latch, RestHighLevelClient client) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootservers);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");

        consumer = new KafkaConsumer(properties);
        consumer.subscribe(Arrays.asList(topic));
        this.latch = latch;
        this.client = client;


    }

    public void run() {
        try {
            sendMessage();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage() throws IOException {
        try {
            while (true) {

                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                logger.info("Max Records : {}",records.count());
                BulkRequest bulkRequest=new BulkRequest();
                for (ConsumerRecord record : records) {
                    try {
                        String recordId=getIdfromJson(record.value().toString(),"id_str");

                        IndexRequest request = new IndexRequest("limittest");
                        request.id(recordId);
                        String text=getIdfromJson(record.value().toString(),"text");
                        request.source("text", text);
                        bulkRequest.add(request);
                        if (status) throw new Exception("error");

                    } catch (NullPointerException|ElasticsearchStatusException e) {
                        logger.error("error",e);
                    }
                }
                if(records.count()>0) {
                    BulkResponse responses = client.bulk(bulkRequest, RequestOptions.DEFAULT);

                    Thread.sleep(1000);
                    logger.info("Commiting the records {}", responses.getItems().length);
                    consumer.commitSync();
                    logger.info("Records are commited");
                }
            }
        } catch (Exception exception) {
            logger.info("received signal {}",exception);
        } finally {
            logger.info("closing consumer");
            client.close();
            consumer.close();
            latch.countDown();

        }

    }

    public String getIdfromJson(String json,String field) {

       return JsonParser.parseString(json).getAsJsonObject().
                    get(field).getAsString();

    }

    public void shutDown() {
        logger.info("Calling in shutdown method");
        status = true;
        consumer.wakeup();
    }
}

public class ConsumerTest {
    static final Logger logger = LoggerFactory.getLogger(ConsumerTest.class);

    public static void main(String[] args) throws IOException, InterruptedException {

        ElasticClient elasticClient = new ElasticClient();
        CountDownLatch latch = new CountDownLatch(1);
        ConsumerClient client = new ConsumerClient("127.0.0.1:9092", "kafka_elastic", "twitter_tweets", latch, elasticClient.getClient());
        Thread t = new Thread(client);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            client.shutDown();
            logger.info("in shutdown");
            try {
                latch.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }));
        latch.await();
        logger.info("Application is exited");
    }
}
