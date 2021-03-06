/*
 * Copyright 2015-2016 Red Hat, Inc. and/or its affiliates
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
import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import net.sf.json.groovy.JsonSlurper

def url = "http://localhost:8080/hawkular/alerts/"
def tenant = "my-organization"

RESTClient client = new RESTClient(url, ContentType.JSON)

client.handler.failure = { resp ->
    def mapper = new JsonSlurper()
    def error = mapper.parseText(resp.entity.content.text)
    println "Status error: ${resp.status} \nerrorMsg: [${error.errorMsg}]"
    return resp
}

if (System.getProperties().containsKey("hawkular")) {
    /*
        User: jdoe
        Password: password
        String encodedCredentials = Base64.getMimeEncoder()
        .encodeToString("$testUser:$testPasword".getBytes("utf-8"))
     */
    client.defaultRequestHeaders.Authorization = "Basic amRvZTpwYXNzd29yZA=="
} else {
    client.defaultRequestHeaders.put("Hawkular-Tenant", tenant)
}

// Send some random events

while (true) {

    // Deployment event

    def rnd = new Random()

    def operations = ["deployment", "undeployment"]
    def containers = ["containerX", "containerY", "containerZ"]
    def apps = ["appA", "appB", "appC"]
    def i = rnd.nextInt(2), j = rnd.nextInt(3), k = rnd.nextInt(3)

    def deploymentEvent = [:]
    deploymentEvent.id = UUID.randomUUID().toString()
    deploymentEvent.ctime = System.currentTimeMillis()
    deploymentEvent.dataId = "events-source"
    deploymentEvent.category = "DEPLOYMENT"
    deploymentEvent.text = "${operations[i]} of ${apps[j]} on ${containers[k]}".toString()
    deploymentEvent.tags = [operation: "${operations[i]}".toString(), app: "${apps[j]}".toString(), container:  "${containers[k]}".toString()]

    println "Sending ${deploymentEvent}"

    client.post(path: "events", body: deploymentEvent)

    // Log event

    def messages = []
    messages[0] = "ERROR [org.hawkular.alerts.actions.api] (ServerService Thread Pool -- 62) HAWKALERT240006: Plugin [aerogear] cannot be started. Error: [Configure org.hawkular.alerts.actions.aerogear.root.server.url, org.hawkular.alerts.actions.aerogear.application.id and org.hawkular.alerts.actions.aerogear.master.secret]"
    messages[1] = "WARN [org.hawkular.alerts.engine.impl.CassDefinitionsServiceImpl] (ServerService Thread Pool -- 62) [15] Retrying connecting to Cassandra cluster in [3000]ms..."
    messages[2] = "INFO [org.jboss.as] (Controller Boot Thread) WFLYSRV0025: WildFly Full 9.0.1.Final (WildFly Core 1.0.1.Final) started in 18402ms - Started 1064 of 1278 services (292 services are lazy, passive or on-demand)"
    def l = rnd.nextInt(3)

    def logEvent = [:]
    logEvent.id = UUID.randomUUID().toString()
    logEvent.ctime = System.currentTimeMillis()
    logEvent.dataId = "events-source"
    logEvent.category = "LOG"
    logEvent.text = "${messages[l]}".toString()
    logEvent.tags = [app: "${apps[j]}".toString()]

    println "Sending ${logEvent}"

    client.post(path: "events", body: logEvent)

    sleep(500)
}

