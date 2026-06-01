# JMS Hierarchical Limits Example

If you have not already done so, [prepare the broker distribution](../../../../README.md#getting-started) before running the example.

To run the example, simply type **mvn verify** from this directory, or **mvn -PnoServer verify** if you want to start and create the broker manually.

## Overview

This example demonstrates hierarchical message limits on a multi-level queue hierarchy. Hierarchical limits allow you to set message limits at different levels of a queue hierarchy, where parent-level limits aggregate the messages from all child queues.

## Queue Hierarchy

The example uses the following queue hierarchy:

```
continent/house/unit
```

Where:
- **Continents**: America, Europe, Asia (3 continents)
- **Houses**: 0-99 per continent (100 houses per continent)
- **Units**: ACUnit, PoolUnit (2 anycast queues per house)

This creates a total of 600 queues (3 × 100 × 2).

## Hierarchical Limits Configuration

The broker is configured with:
- **Default hierarchical-max-messages**: 10 (for house and unit levels)
- **Continent level hierarchical-max-messages**: 100

This means:
- Each individual queue (e.g., `America/0/ACUnit`) can hold up to 10 messages
- Each house level (e.g., `America/0/*`) can hold up to 10 messages total across both ACUnit and PoolUnit
- Each continent level (e.g., `America/*`) can hold up to 100 messages total across all houses

## Example Phases

The example runs through three phases to demonstrate hierarchical limits in action:

### Phase 1: Normal Operation (5 seconds)
- All 600 consumers are active
- Messages are sent continuously to all queues via scheduled executors
- All messages are delivered successfully
- You'll see periodic output showing messages being received

### Phase 2: Single House Failure (5 seconds)
- Consumers for `America/50/ACUnit` and `America/50/PoolUnit` are removed
- Producers for these queues hit the hierarchical limit and start failing
- All other queues continue working normally
- You'll see error messages for the America/50 queues only

### Phase 3: Continent Failure (5 seconds)
- All consumers for the America continent are removed
- All producers for America/* queues hit the hierarchical limit and start failing
- Europe and Asia queues continue working normally
- You'll see error messages for all America queues

## Expected Output

You should see output similar to:

```
=== Creating consumers for all queues ===
Created 600 consumers

=== Creating producers and starting message sending ===
Created 600 producers and started scheduled sending

=== Phase 1: All consumers active - all messages should be delivered ===
Consumer America/0/ACUnit received 50 messages (latest: Message 123 to America/0/ACUnit)
Consumer Europe/5/PoolUnit received 50 messages (latest: Message 456 to Europe/5/PoolUnit)
...

=== Phase 2: Removing consumers for America/50/* ===
Expected: Producers for America/50/ACUnit and America/50/PoolUnit should start failing
Removed consumer for: America/50/ACUnit
Removed consumer for: America/50/PoolUnit
ERROR sending to America/50/ACUnit: Queue is full due to hierarchical limits
ERROR sending to America/50/PoolUnit: Queue is full due to hierarchical limits
...

=== Phase 3: Removing all consumers from America ===
Expected: All producers for America/* should start failing
Removed consumer for: America/0/ACUnit
Removed consumer for: America/0/PoolUnit
...
ERROR sending to America/0/ACUnit: Queue is full due to hierarchical limits
ERROR sending to America/15/PoolUnit: Queue is full due to hierarchical limits
...
```

## Key Concepts

1. **Hierarchical Limits**: Limits can be set at any level of the hierarchy, and parent levels aggregate counts from all children
2. **Anycast Queues**: Each unit type (ACUnit, PoolUnit) is an anycast queue receiving messages directly
3. **Cascading Failures**: When consumers stop, messages accumulate and hit hierarchical limits, causing producer failures
4. **Isolation**: Failures in one part of the hierarchy (e.g., one house or one continent) don't affect other parts

## Configuration Details

To configure hierarchical limits in your broker.xml, you would use address-settings like:

```xml
<address-settings>
   <!-- Default for house and unit levels -->
   <address-setting match="#">
      <hierarchical-max-messages>10</hierarchical-max-messages>
   </address-setting>
   
   <!-- Continent level override -->
   <address-setting match="America.#">
      <hierarchical-max-messages>100</hierarchical-max-messages>
   </address-setting>
   <address-setting match="Europe.#">
      <hierarchical-max-messages>100</hierarchical-max-messages>
   </address-setting>
   <address-setting match="Asia.#">
      <hierarchical-max-messages>100</hierarchical-max-messages>
   </address-setting>
</address-settings>
```

**Important Note**: Artemis internally converts `/` separators in queue names to `.` separators. So a queue named `America/50/ACUnit` in Java code becomes `America.50.ACUnit` internally. The address-settings patterns in broker.xml must use the dot notation with wildcard matching (`#` for multi-level wildcard, `*` for single-level wildcard).

The broker.xml file is included in `src/main/resources/activemq/server0/broker.xml` with the appropriate hierarchical limits configuration.
