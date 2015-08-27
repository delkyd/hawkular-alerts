/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hawkular.alerts.rest

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

import org.hawkular.alerts.api.model.condition.ThresholdCondition
import org.hawkular.alerts.api.model.dampening.Dampening
import org.hawkular.alerts.api.model.Severity
import org.hawkular.alerts.api.model.trigger.Tag
import org.hawkular.alerts.api.model.trigger.Trigger
import org.hawkular.alerts.api.json.MemberCondition
import org.hawkular.alerts.api.json.MemberTrigger
import org.hawkular.alerts.api.model.trigger.Mode
import org.junit.Test

import java.util.Map
import java.util.HashMap

/**
 * Triggers REST tests.
 *
 * @author Lucas Ponce
 */
class TriggersITest extends AbstractITestBase {

    @Test
    void findInitialTriggers() {
        def resp = client.get(path: "triggers")
        def data = resp.data
        assertEquals(200, resp.status)
        assert data.size() > 0
        for (int i = 0; i < data.size(); i++) {
            Trigger t = data[i]
            println t.toString()
        }
    }

    @Test
    void createTrigger() {
        Trigger testTrigger = new Trigger("test-trigger-1", "No-Metric");

        // remove if it exists
        def resp = client.delete(path: "triggers/test-trigger-1")
        assert(200 == resp.status || 404 == resp.status)

        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // Should not be able to create the same triggerId again
        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(400, resp.status)

        resp = client.get(path: "triggers/test-trigger-1");
        assertEquals(200, resp.status)
        assertEquals("No-Metric", resp.data.name)

        testTrigger.setName("No-Metric-Modified")
        testTrigger.setSeverity(Severity.CRITICAL)
        resp = client.put(path: "triggers/test-trigger-1", body: testTrigger)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-1")
        assertEquals(200, resp.status)
        assertEquals("No-Metric-Modified", resp.data.name)
        assertEquals("CRITICAL", resp.data.severity)

        resp = client.delete(path: "triggers/test-trigger-1")
        assertEquals(200, resp.status)

        // Test create w/o assigning a TriggerId, it should generate a UUID
        testTrigger.setId(null);
        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)
        assertNotNull(resp.data.id)

        // Delete the trigger
        resp = client.delete(path: "triggers/" + resp.data.id)
        assertEquals(200, resp.status)
    }

    @Test
    void testGroupTrigger() {
        Trigger groupTrigger = new Trigger("group-trigger", "group-trigger");
        groupTrigger.setEnabled(false);

        // remove if it exists
        def resp = client.delete(path: "triggers/groups/group-trigger", query: [keepNonOrphans:false,keepOrphans:false])
        assert(200 == resp.status || 404 == resp.status)

        // create the group
        resp = client.post(path: "triggers/groups", body: groupTrigger)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/group-trigger")
        assertEquals(200, resp.status)
        groupTrigger = (Trigger)resp.data;
        assertEquals( true, groupTrigger.isGroup() );

        ThresholdCondition cond1 = new ThresholdCondition("group-trigger", "DataId1-Token",
            ThresholdCondition.Operator.GT, 10.0);
        Map<String, Map<String, String>> dataIdMemberMap = new HashMap<>();
        MemberCondition memberCondition = new MemberCondition( cond1, dataIdMemberMap );

        resp = client.post(path: "triggers/groups/group-trigger/conditions", body: memberCondition)
        assertEquals(resp.toString(), 200, resp.status)
        assertEquals(1, resp.data.size())

        // create member 1
        Map<String,String> dataIdMap = new HashMap<>(1);
        dataIdMap.put("DataId1-Token", "DataId1-Child1");
        MemberTrigger memberTrigger = new MemberTrigger("group-trigger", "member1", "member1", null, dataIdMap);
        resp = client.post(path: "triggers/groups/members", body: memberTrigger);
        assertEquals(200, resp.status)

        // create member 2
        dataIdMap.put("DataId1-Token", "DataId1-Child2");
        memberTrigger = new MemberTrigger("group-trigger", "member2", "member2", null, dataIdMap);
        resp = client.post(path: "triggers/groups/members", body: memberTrigger);
        assertEquals(200, resp.status)

        // orphan member2, it should no longer get updates
        resp = client.post(path: "triggers/groups/members/member2/orphan");
        assertEquals(200, resp.status)

        // add dampening to the group
        Dampening groupDampening = Dampening.forRelaxedCount("group-trigger", Mode.FIRING, 2, 4);
        resp = client.post(path: "triggers/groups/group-trigger/dampenings", body: groupDampening);
        assertEquals(200, resp.status)

        // add tag to the group
        Tag groupTag = new Tag("group-trigger", "group-category", "group-name", true);
        resp = client.post(path: "triggers/groups/tags", body: groupTag)
        assertEquals(200, resp.status)

        // add another condition to the group
        ThresholdCondition cond2 = new ThresholdCondition("group-trigger", "DataId2-Token",
            ThresholdCondition.Operator.LT, 20.0);
        dataIdMap.clear();
        dataIdMap.put("member1", "DataId2-Member1");
        dataIdMemberMap.put("DataId2-Token", dataIdMap);
        memberCondition = new MemberCondition(cond2, dataIdMemberMap);
        resp = client.post(path: "triggers/groups/group-trigger/conditions", body: memberCondition)
        assertEquals(200, resp.status)

        // update the group trigger
        groupTrigger.setAutoDisable( true );
        resp = client.put(path: "triggers/groups/group-trigger", body: groupTrigger)
        assertEquals(200, resp.status)

        // update the group dampening to the group
        String did = groupDampening.getDampeningId();
        groupDampening = Dampening.forRelaxedCount("group-trigger", Mode.FIRING, 2, 6);
        resp = client.put(path: "triggers/groups/group-trigger/dampenings/" + did, body: groupDampening);
        assertEquals(200, resp.status)

        // query w/o orphans
        resp = client.get(path: "triggers/groups/group-trigger/members", query: [includeOrphans:"false"])
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());
        Trigger member = (Trigger)resp.data[0];
        assertEquals( false, member.isOrphan() );
        assertEquals( "member1", member.getName() );

        // query w orphans
        resp = client.get(path: "triggers/groups/group-trigger/members", query: [includeOrphans:"true"])
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size());
        member = (Trigger)resp.data[0];
        Trigger orphanMember = (Trigger)resp.data[1];
        if ( member.isOrphan() ) {
            member = (Trigger)resp.data[1];
            orphanMember = (Trigger)resp.data[0];
        }

        // get the group trigger
        resp = client.get(path: "triggers/group-trigger")
        assertEquals(200, resp.status)
        groupTrigger = (Trigger)resp.data;

        assertEquals( "group-trigger", groupTrigger.getName() );
        assertEquals( true, groupTrigger.isGroup() );
        assertEquals( true, groupTrigger.isAutoDisable());
        resp = client.get(path: "triggers/group-trigger/dampenings")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());
        resp = client.get(path: "triggers/group-trigger/tags", query: [category:"group-category"])
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());
        resp = client.get(path: "triggers/group-trigger/conditions")
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size());
        String groupConditionId = resp.data[0].conditionId;

        assertEquals( "member1", member.getName() );
        assertEquals( false, member.isOrphan() );
        assertEquals( true, member.isAutoDisable());
        resp = client.get(path: "triggers/member1/dampenings")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());
        resp = client.get(path: "triggers/member1/tags", query: [category:"group-category"])
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());
        resp = client.get(path: "triggers/member1/conditions")
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size());

        assertEquals( "member2", orphanMember.getName() );
        assertEquals( true, orphanMember.isOrphan() );
        assertEquals( false, orphanMember.isAutoDisable());
        resp = client.get(path: "triggers/member2/dampenings")
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size());
        resp = client.get(path: "triggers/member2/tags", query: [category:"group-category"])
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size());
        resp = client.get(path: "triggers/member2/conditions")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());

        // unorphan member2
        dataIdMap.clear();
        dataIdMap.put("DataId1-Token", "DataId1-Child2");
        dataIdMap.put("DataId2-Token", "DataId2-Child2");
        memberTrigger = new MemberTrigger("group-trigger", "member2", "member2", null, dataIdMap);
        resp = client.post(path: "triggers/groups/members/member2/unorphan", body: memberTrigger);
        assertEquals(200, resp.status)

        // delete group tag
        resp = client.put(path: "triggers/groups/group-trigger/tags",
                          query: [category:"group-category",name:"group-name"])
        assertEquals(200, resp.status)

        // delete group dampening
        resp = client.delete(path: "triggers/groups/group-trigger/dampenings/" + did)
        assertEquals(200, resp.status)

        // delete group condition
        resp = client.delete(path: "triggers/groups/group-trigger/conditions/" + groupConditionId)
        assertEquals(200, resp.status)

        // get the group trigger
        resp = client.get(path: "triggers/group-trigger")
        assertEquals(200, resp.status)
        groupTrigger = (Trigger)resp.data;

        assertEquals( "group-trigger", groupTrigger.getName() );
        assertEquals( true, groupTrigger.isGroup() );
        assertEquals( true, groupTrigger.isAutoDisable());
        resp = client.get(path: "triggers/group-trigger/dampenings")
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size());
        resp = client.get(path: "triggers/group-trigger/tags", query: [category:"group-category"])
        assertEquals(200, resp.status)
        assertEquals(0, resp.data.size());
        resp = client.get(path: "triggers/group-trigger/conditions")
        assertEquals(200, resp.status)
        assertEquals(1, resp.data.size());

        // query w/o orphans
        resp = client.get(path: "triggers/groups/group-trigger/members", query: [includeOrphans:"false"])
        assertEquals(200, resp.status)
        assertEquals(2, resp.data.size());
        for(int i=0; i < resp.data.size(); ++i) {
            member = (Trigger)resp.data[i];
            String name = member.getName();
            assertEquals( false, member.isOrphan() );
            assertEquals( true, member.isAutoDisable());
            resp = client.get(path: "triggers/" + name + "/dampenings")
            assertEquals(200, resp.status)
            assertEquals(0, resp.data.size());
            resp = client.get(path: "triggers/" + name + "/tags", query: [category:"group-category"])
            assertEquals(200, resp.status)
            assertEquals(0, resp.data.size());
            resp = client.get(path: "triggers/" + name + "/conditions")
            assertEquals(200, resp.status)
            assertEquals(1, resp.data.size());
        }

        // remove group trigger
        resp = client.delete(path: "triggers/groups/group-trigger")
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/group-trigger")
        assertEquals(404, resp.status)
        resp = client.get(path: "triggers/member1")
        assertEquals(404, resp.status)
        resp = client.get(path: "triggers/member2")
        assertEquals(404, resp.status)
    }

    @Test
    void testTag() {
        Trigger testTrigger = new Trigger("test-trigger-1", "No-Metric");

        // remove if it exists
        def resp = client.delete(path: "triggers/test-trigger-1")
        assert(200 == resp.status || 404 == resp.status)

        // create the test trigger
        resp = client.post(path: "triggers", body: testTrigger)
        assertEquals(200, resp.status)

        // make sure the test trigger exists
        resp = client.get(path: "triggers/test-trigger-1");
        assertEquals(200, resp.status)
        assertEquals("No-Metric", resp.data.name)

        Tag testTag = new Tag("test-trigger-1", "test-category", "test-name", true);
        resp = client.post(path: "triggers/tags", body: testTag)
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-1/tags", query: [category:"test-category"] );
        assertEquals(200, resp.status)
        assertEquals("test-name", resp.data.iterator().next().name)

        resp = client.get(path: "triggers/test-trigger-1/tags");
        assertEquals(200, resp.status)
        assertEquals("test-name", resp.data.iterator().next().name)

        resp = client.get(path: "triggers/tag", query: [category:"test-category"] );
        assertEquals(200, resp.status)
        assertEquals("test-trigger-1", resp.data.iterator().next().id)

        resp = client.get(path: "triggers/tag", query: [name:"test-name"] );
        assertEquals(200, resp.status)
        assertEquals("test-trigger-1", resp.data.iterator().next().id)

        resp = client.get(path: "triggers/tag", query: [category:"test-category",name:"test-name"] );
        assertEquals(200, resp.status)
        assertEquals("test-trigger-1", resp.data.iterator().next().id)

        resp = client.get(path: "triggers/tag", query: [category:"funky",name:"funky"] );
        assertEquals(200, resp.status)
        assertEquals(false, resp.data.iterator().hasNext())

        // delete the tag
        resp = client.put(path: "triggers/test-trigger-1/tags", query: [category:"test-category"] )
        assertEquals(200, resp.status)

        resp = client.get(path: "triggers/test-trigger-1/tags");
        assertEquals(200, resp.status)

        // delete the trigger
        resp = client.delete(path: "triggers/test-trigger-1")
        assertEquals(200, resp.status)
    }

}
