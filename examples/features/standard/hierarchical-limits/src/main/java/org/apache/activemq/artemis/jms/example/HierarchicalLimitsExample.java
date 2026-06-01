/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.jms.example;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

/**
 * This example demonstrates hierarchical limits with a multi-level queue hierarchy.
 *
 * Queue Hierarchy: continent/house/ACUnit and continent/house/PoolUnit
 * - 3 Continents: America, Europe, Asia
 * - 100 houses per continent
 * - 2 anycast queues per house: ACUnit and PoolUnit
 *
 * The hierarchical-max-messages is set to 10 by default, but continent level has 100.
 *
 * The example:
 * 1. Sends messages continuously to each house's ACUnit and PoolUnit queues
 * 2. Initially all consumers work fine
 * 3. Then removes consumer for one specific house - that house's producers should start failing
 * 4. Then removes all consumers from America - all America producers should start failing
 */
public class HierarchicalLimitsExample {

   private static final String[] CONTINENTS = {"America", "Europe", "Asia"};
   private static final int HOUSES_PER_CONTINENT = 100;
   private static final String[] UNIT_TYPES = {"ACUnit", "PoolUnit"};

   private static final List<ConsumerHolder> allConsumers = new ArrayList<>();
   private static final List<ProducerHolder> allProducers = new ArrayList<>();

   public static void main(final String[] args) throws Exception {
      Connection connection = null;
      ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

      try {
         // Step 2. look-up the JMS connection factory object from JNDI
         ConnectionFactory cf = new ActiveMQConnectionFactory("tcp://localhost:61616");

         // Step 3. Create a JMS Connection
         connection = cf.createConnection();

         // Step 4. Start the connection
         connection.start();

         System.out.println("\n=== Creating consumers for all queues ===");

         // Step 5. Create consumers for all continents, houses, and unit types
         for (String continent : CONTINENTS) {
            for (int house = 0; house < HOUSES_PER_CONTINENT; house++) {
               for (String unitType : UNIT_TYPES) {
                  String queueName = continent + "." + house + "." + unitType;

                  Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                  Queue queue = session.createQueue(queueName);
                  MessageConsumer consumer = session.createConsumer(queue);

                  ConsumerHolder holder = new ConsumerHolder(continent, house, unitType, session, consumer);
                  consumer.setMessageListener(holder);
                  allConsumers.add(holder);
               }
            }
         }

         System.out.println("Created " + allConsumers.size() + " consumers");

         System.out.println("\n=== Creating producers and starting message sending ===");

         // Step 6. Create producers and schedule message sending for all queues
         for (String continent : CONTINENTS) {
            for (int house = 0; house < HOUSES_PER_CONTINENT; house++) {
               for (String unitType : UNIT_TYPES) {
                  String queueName = continent + "." + house + "." + unitType;

                  Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                  Queue queue = session.createQueue(queueName);
                  MessageProducer producer = session.createProducer(queue);

                  ProducerHolder holder = new ProducerHolder(continent, house, unitType, session, producer);
                  allProducers.add(holder);
                  AtomicInteger messageSent = new AtomicInteger();
                  AtomicBoolean facedException = new AtomicBoolean();

                  // Schedule message sending every 100ms
                  scheduler.scheduleAtFixedRate(() -> {
                     try {
                        int msgNum = messageSent.incrementAndGet();
                        TextMessage message = session.createTextMessage("Message " + msgNum + " to " + queueName);
                        producer.send(message);
                     } catch (JMSException e) {
                        if (!facedException.get()) {
                           facedException.set(true);
                           System.err.println("ERROR sending to " + queueName + ": " + e.getMessage());
                        }
                     }
                  }, 1, 1, TimeUnit.MILLISECONDS);
               }
            }
         }

         System.out.println("Created " + allProducers.size() + " producers and started scheduled sending");

         // Step 7. Let messages flow for a while
         System.out.println("\n=== Phase 1: All consumers active - all messages should be delivered ===");
         Thread.sleep(10_000);

         // Step 8. Remove consumer for one specific house (America/house50)
         System.out.println("\n=== Phase 2: Removing consumers for America/50/* ===");
         System.out.println("Expected: Producers for America/50/ACUnit and America/50/PoolUnit should start failing");

         removeConsumersForHouse("America", 50);
         Thread.sleep(60_000);

         // Step 9. Remove all consumers from America
         System.out.println("\n=== Phase 3: Removing all consumers from America ===");
         System.out.println("Expected: All producers for America/* should start failing");

         removeConsumersForContinent("America");
         Thread.sleep(60_000);

         System.out.println("\n=== Example completed ===");
         System.out.println("Check the output above to verify:");
         System.out.println("1. Initially all messages were delivered successfully");
         System.out.println("2. After removing America/50/* consumers, those producers started failing");
         System.out.println("3. After removing all America consumers, all America producers started failing");
         System.out.println("4. Europe and Asia producers continued working throughout");

      } finally {
         // Cleanup
         scheduler.shutdownNow();

         if (connection != null) {
            connection.close();
         }
      }
   }

   private static void removeConsumersForHouse(String continent, int house) throws JMSException {
      List<ConsumerHolder> toRemove = new ArrayList<>();

      for (ConsumerHolder holder : allConsumers) {
         if (holder.continent.equals(continent) && holder.house == house) {
            toRemove.add(holder);
         }
      }

      for (ConsumerHolder holder : toRemove) {
         holder.consumer.close();
         holder.session.close();
         allConsumers.remove(holder);
         System.out.println("Removed consumer for: " + holder.continent + "/" + holder.house + "/" + holder.unitType);
      }
   }

   private static void removeConsumersForContinent(String continent) throws JMSException {
      List<ConsumerHolder> toRemove = new ArrayList<>();

      for (ConsumerHolder holder : allConsumers) {
         if (holder.continent.equals(continent)) {
            toRemove.add(holder);
         }
      }

      for (ConsumerHolder holder : toRemove) {
         holder.consumer.close();
         holder.session.close();
         allConsumers.remove(holder);
         System.out.println("Removed consumer for: " + holder.continent + "/" + holder.house + "/" + holder.unitType);
      }
   }

   static class ConsumerHolder implements MessageListener {
      final String continent;
      final int house;
      final String unitType;
      final Session session;
      final MessageConsumer consumer;
      final AtomicInteger receivedCount = new AtomicInteger(0);

      ConsumerHolder(String continent, int house, String unitType, Session session, MessageConsumer consumer) {
         this.continent = continent;
         this.house = house;
         this.unitType = unitType;
         this.session = session;
         this.consumer = consumer;
      }

      @Override
      public void onMessage(Message message) {

         int count = receivedCount.incrementAndGet();
         if (count % 200 == 0) {
            try {
               TextMessage textMessage = (TextMessage) message;
               System.out.println("Consumer " + continent + "/" + house + "/" + unitType +
                                " received " + count + " messages (latest: " + textMessage.getText() + ")");
            } catch (JMSException e) {
               e.printStackTrace();
            }
         }
      }

      @Override
      public String toString() {
         return "ConsumerHolder{" + "continent='" + continent + '\'' + ", house=" + house + ", unitType='" + unitType + '\'' + ", session=" + session + ", consumer=" + consumer + ", receivedCount=" + receivedCount + '}';
      }
   }

   static class ProducerHolder {
      final String continent;
      final int house;
      final String unitType;
      final Session session;
      final MessageProducer producer;
      volatile boolean errorLogged = false;

      ProducerHolder(String continent, int house, String unitType, Session session, MessageProducer producer) {
         this.continent = continent;
         this.house = house;
         this.unitType = unitType;
         this.session = session;
         this.producer = producer;
      }
   }
}
