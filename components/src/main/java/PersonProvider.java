import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.streams.core.StreamsDatum;
import org.apache.streams.core.StreamsProvider;
import org.apache.streams.core.StreamsResultSet;
import org.apache.streams.jackson.StreamsJacksonMapper;
import org.apache.streams.pojo.json.Activity;
import org.apache.streams.pojo.json.ActivityObject;
import org.apache.streams.pojo.json.Actor;
import org.joda.time.DateTime;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

public class PersonProvider implements StreamsProvider {

    ObjectMapper mapper;
    InputStream is;

    boolean done = false;

    @Override
    public void startStream() {
        throw new NotImplementedException();
    }

    @Override
    public StreamsResultSet readCurrent() {
        Preconditions.checkNotNull(mapper);
        try {
            Preconditions.checkState(is.available() > 0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        ObjectNode inputNode = null;
        try {
            inputNode = mapper.readValue(is, ObjectNode.class);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Preconditions.checkNotNull(inputNode);

        Iterator<Map.Entry<String, JsonNode>> peopleNodeList = inputNode.fields();
        Preconditions.checkState(inputNode.elements().hasNext());

        Queue<StreamsDatum> result = Queues.newConcurrentLinkedQueue();

        Map.Entry<String, JsonNode> projectItem;
        while( peopleNodeList.hasNext() ) {
            projectItem = peopleNodeList.next();

            Actor person = new Actor();
            person.setId(projectItem.getKey());
            person.setDisplayName(projectItem.getValue().get("name").textValue());
            person.setAdditionalProperty("screenName", projectItem.getKey());
            person.setAdditionalProperty("member", projectItem.getValue().get("member").booleanValue());
            person.setObjectType("person");

            ArrayNode projectsArrayNode = (ArrayNode) projectItem.getValue().get("projects");
            for( Iterator<JsonNode> projectIterator = projectsArrayNode.elements(); projectIterator.hasNext(); ) {

                JsonNode projectIdNode = projectIterator.next();

                Activity activity = new Activity();
                activity.setActor(person);

                ActivityObject object = new ActivityObject();
                String projectId = null;
                if( projectIdNode.asText().endsWith("-pmc")) {
                    projectId = StringUtils.stripEnd(projectIdNode.asText(), "-pmc");

                    object.setId(projectIdNode.asText());
                    object.setDisplayName(projectId + " PMC");
                    object.setObjectType("pmc");
                    activity.setVerb("member");

                }
                else {

                    object.setId(projectIdNode.asText());
                    object.setDisplayName(projectIdNode.asText());
                    object.setObjectType("project");
                    activity.setVerb("committer");

                }

                activity.setId(person.getId()+":"+activity.getVerb()+":"+object.getId());
                activity.setObject(object);
                result.add( new StreamsDatum(activity, projectItem.getKey() ) );

            }

        }

        return new StreamsResultSet(result);
    }

    @Override
    public StreamsResultSet readNew(BigInteger bigInteger) {
        throw new NotImplementedException();
    }

    @Override
    public StreamsResultSet readRange(DateTime dateTime, DateTime dateTime1) {
        throw new NotImplementedException();
    }

    @Override
    public boolean isRunning() {
        return !done;
    }

    @Override
    public void prepare(Object o) {
        mapper = StreamsJacksonMapper.getInstance();
        is = PersonProvider.class.getResourceAsStream("/testdata/people.json");
    }

    @Override
    public void cleanUp() {
        done = true;
    }
}