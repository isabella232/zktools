package com.wepay.zktools.clustermgr.internal;

import com.wepay.zktools.clustermgr.Endpoint;
import com.wepay.zktools.clustermgr.PartitionInfo;
import com.wepay.zktools.test.util.Utils;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DynamicPartitionAssignmentPolicyTest {

    @Test
    public void test() {
        DynamicPartitionAssignmentPolicy policy = new DynamicPartitionAssignmentPolicy();

        Map<Integer, List<PartitionInfo>> assignmentMap = new HashMap<>();
        assignmentMap.put(0, Collections.singletonList(new PartitionInfo(0, 0)));
        assignmentMap.put(1, Collections.singletonList(new PartitionInfo(1, 0)));
        assignmentMap.put(2, Arrays.asList(new PartitionInfo(2, 0), new PartitionInfo(3, 0)));

        PartitionAssignment assignment = new PartitionAssignment(0, 4, assignmentMap);

        Map<Integer, ServerDescriptor> servers = new HashMap<>();

        servers.put(0, new ServerDescriptor(1, new Endpoint("host0", 6000), Collections.emptyList()));
        servers.put(1, new ServerDescriptor(1, new Endpoint("host1", 6000), Collections.emptyList()));
        servers.put(2, new ServerDescriptor(2, new Endpoint("host2", 6000), Collections.emptyList()));

        assignment = policy.update(1, assignment, 4, servers);

        assertEquals(3, assignment.numEndpoints);
        assertEquals(Utils.set(0, 1, 2), assignment.serverIds());
        assertEquals(1, assignment.partitionsFor(0).size());
        assertEquals(1, assignment.partitionsFor(1).size());
        assertEquals(2, assignment.partitionsFor(2).size());

        servers.remove(0);
        assignment = policy.update(1, assignment, 4, servers);

        assertEquals(2, assignment.numEndpoints);
        assertEquals(Utils.set(1, 2), assignment.serverIds());
        assertEquals(2, assignment.partitionsFor(1).size());
        assertEquals(2, assignment.partitionsFor(2).size());

        servers.remove(1);
        assignment = policy.update(2, assignment, 4, servers);

        assertEquals(1, assignment.numEndpoints);
        assertEquals(Utils.set(2), assignment.serverIds());
        assertEquals(4, assignment.partitionsFor(2).size());

        servers.remove(2);
        assignment = policy.update(2, assignment, 4, servers);

        assertEquals(0, assignment.numEndpoints);
        assertTrue(assignment.serverIds().isEmpty());
    }

    @Test
    public void testPreferredPartitions() {
        DynamicPartitionAssignmentPolicy policy = new DynamicPartitionAssignmentPolicy();

        Map<Integer, List<PartitionInfo>> assignmentMap = new HashMap<>();
        assignmentMap.put(1, Arrays.asList(new PartitionInfo(0, 0)));
        assignmentMap.put(2, Arrays.asList(new PartitionInfo(1, 0), new PartitionInfo(2, 0)));

        PartitionAssignment assignment = new PartitionAssignment(0, 3, assignmentMap);

        Map<Integer, ServerDescriptor> servers = new HashMap<>();
        servers.put(1, new ServerDescriptor(1, new Endpoint("host0", 6000), Collections.emptyList()));
        servers.put(2, new ServerDescriptor(2, new Endpoint("host1", 6000), Collections.emptyList()));

        assignment = policy.update(1, assignment, 3, servers);

        /** Verify partition assignment of the servers.
         *  Server 1 ====> P0
         *  Server 2 ====> P1, P2
         */
        assertEquals(2, assignment.numEndpoints);
        assertEquals(1, assignment.partitionsFor(1).size());
        assertTrue(assignment.partitionsFor(1).contains(new PartitionInfo(0, 0)));
        assertEquals(2, assignment.partitionsFor(2).size());
        assertTrue(assignment.partitionsFor(2).contains(new PartitionInfo(1, 0)));
        assertTrue(assignment.partitionsFor(2).contains(new PartitionInfo(2, 0)));

        // Move P2 from Server 2 to Server 1 by making P2 as preferred partition of Server 1.
        servers.put(1, new ServerDescriptor(1, new Endpoint("host0", 6000), Collections.singletonList(2)));
        assignment = policy.update(2, assignment, 3, servers);

        /** Verify partition assignment of the servers:
         *  Server 1 ====> P0, P2
         *  Server 2 ====> P1
         */
        assertEquals(2, assignment.numEndpoints);
        assertEquals(2, assignment.partitionsFor(1).size());
        assertTrue(assignment.partitionsFor(1).contains(new PartitionInfo(0, 0)));
        assertTrue(assignment.partitionsFor(1).contains(new PartitionInfo(2, 1)));
        assertEquals(1, assignment.partitionsFor(2).size());
        assertTrue(assignment.partitionsFor(2).contains(new PartitionInfo(1, 0)));

        // Move P2 from Server 1 to Server 2 by making P2 as preferred partition for Server 2.
        servers.put(2, new ServerDescriptor(2, new Endpoint("host1", 6000), Collections.singletonList(2)));

        /**
         *  Note: At this point P2 is assigned as preferred partition for both Server 1 and Server 2.
         *  Make sure to un-assign P2 as preferred partition of Server 1 if you want to successfully move P2 to
         *  Server 2.
         *  Otherwise, even after specifying P2 as a preferred partition of Server 2, P2 will get assigned to Server
         *  1 as we assign the partitions to the servers in sequential order.
         */
        servers.put(1, new ServerDescriptor(1, new Endpoint("host0", 6000), Collections.emptyList()));
        assignment = policy.update(3, assignment, 3, servers);

        /** Verify partition assignment of the servers:
         *  Server 1 ====> P0
         *  Server 2 ====> P1, P2
         */
        assertEquals(2, assignment.numEndpoints);
        assertEquals(1, assignment.partitionsFor(1).size());
        assertTrue(assignment.partitionsFor(1).contains(new PartitionInfo(0, 0)));
        assertEquals(2, assignment.partitionsFor(2).size());
        assertTrue(assignment.partitionsFor(2).contains(new PartitionInfo(1, 0)));
        assertTrue(assignment.partitionsFor(2).contains(new PartitionInfo(2, 2)));
    }

}
