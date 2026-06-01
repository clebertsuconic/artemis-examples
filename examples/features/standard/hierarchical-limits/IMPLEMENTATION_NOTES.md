# Hierarchical Limits Example - Implementation Notes

## Overview

This example demonstrates the hierarchical limits functionality for Apache Artemis queues. It creates a multi-level queue hierarchy and shows how hierarchical message limits work across different levels.

## Created Files

1. **pom.xml** - Maven project configuration
2. **src/main/java/org/apache/activemq/artemis/jms/example/HierarchicalLimitsExample.java** - Main example code
3. **src/main/resources/jndi.properties** - JNDI configuration for connection factory
4. **src/main/resources/activemq/server0/broker.xml** - Broker configuration with hierarchical limits
5. **readme.md** - User-facing documentation
6. **IMPLEMENTATION_NOTES.md** - This file

## Queue Hierarchy Structure

```
continent/house/unit
├── America/
│   ├── 0/
│   │   ├── ACUnit
│   │   └── PoolUnit
│   ├── 1/
│   │   ├── ACUnit
│   │   └── PoolUnit
│   └── ... (up to 99)
├── Europe/
│   └── ... (same structure, 100 houses)
└── Asia/
    └── ... (same structure, 100 houses)
```

Total: 600 anycast queues (3 continents × 100 houses × 2 unit types)

## Hierarchical Limits Configuration

The example expects the broker to be configured with:

- **Default limit**: 10 messages (for house and unit levels)
- **Continent limit**: 100 messages (for continent level)

This needs to be configured in the broker's `broker.xml` file using address-settings.

## Example Flow

### Phase 1: Normal Operation (5 seconds)
- Creates 600 consumers (one per queue)
- Creates 600 producers
- Uses ScheduledExecutorService to send messages every 100ms to each queue
- All messages are delivered successfully
- Periodic output shows message counts

### Phase 2: Single House Failure (5 seconds)
- Removes consumers for `America/50/ACUnit` and `America/50/PoolUnit`
- Messages accumulate in these queues
- Once hierarchical limit is hit, producers start getting errors
- All other queues continue working normally

### Phase 3: Continent-wide Failure (5 seconds)
- Removes all consumers for America continent (200 consumers)
- Messages accumulate across all America queues
- Once hierarchical limit is hit, all America producers start getting errors
- Europe and Asia queues continue working normally

## Key Implementation Details

### ConsumerHolder
- Implements MessageListener
- Tracks received message count per queue
- Prints periodic updates (every 50 messages)

### ProducerHolder
- Holds producer and session references
- Tracks error state to avoid duplicate error logging
- Used by scheduled tasks to send messages

### Scheduled Message Sending
- Uses ScheduledExecutorService with 10 threads
- Each producer sends a message every 100ms
- Catches JMSException and logs errors (only once per producer)

### Consumer Removal
- `removeConsumersForHouse()` - Removes consumers for specific house
- `removeConsumersForContinent()` - Removes all consumers for a continent
- Properly closes consumers and sessions

## Running the Example

```bash
cd examples/features/standard/hierarchical-limits
mvn verify
```

Or with manual broker control:
```bash
mvn -PnoServer verify
```

## Expected Broker Configuration

The broker needs to be configured with hierarchical limits in `broker.xml`:

```xml
<address-settings>
   <!-- Default for all queues -->
   <address-setting match="#">
      <hierarchical-max-messages>10</hierarchical-max-messages>
   </address-setting>
   
   <!-- Continent level overrides -->
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

Note: In Artemis address-settings, the wildcard character is `.` not `/`, so queue names need to use dots as separators, or the configuration needs to be adjusted accordingly.

## Testing the Functionality

When running the example, verify:

1. ✓ Initially all messages flow without errors
2. ✓ After removing America/50/* consumers, errors appear for those queues only
3. ✓ After removing all America consumers, errors appear for all America queues
4. ✓ Europe and Asia queues continue working throughout
5. ✓ Error messages mention hierarchical limits

## Integration with Apache Artemis

This example is designed to work with the hierarchical limits feature being developed in `/home/csuconic/work/apache/apache-artemis`. Once that feature is complete and merged, this example will demonstrate its capabilities.

## Future Enhancements

Possible improvements:
- Add configuration file to automatically configure hierarchical limits
- Add metrics/statistics output showing limit enforcement
- Add visual representation of message distribution across hierarchy
- Add ability to dynamically adjust limits during runtime
- Add more complex failure scenarios (partial recovery, etc.)
