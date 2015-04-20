import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.collect.Queues;
import org.apache.commons.lang.NotImplementedException;
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

public class ProjectProvider implements StreamsProvider {

    ObjectMapper mapper;
    InputStream is;

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

        Iterator<Map.Entry<String, JsonNode>> projectsNodeList = inputNode.fields();
        Preconditions.checkState(inputNode.elements().hasNext());

        Queue<StreamsDatum> result = Queues.newConcurrentLinkedQueue();

        Map.Entry<String, JsonNode> projectItem;
        while( projectsNodeList.hasNext() ) {
            projectItem = projectsNodeList.next();
            String projectId = projectItem.getKey();

            String pmcId = projectId.concat("-pmc");

            ActivityObject project = new ActivityObject();
            project.setId(projectId);

            Actor pmc = new Actor();
            pmc.setId(pmcId);

            Iterator<Map.Entry<String, JsonNode>> projectProperties;
            projectProperties = projectItem.getValue().fields();
            while( projectProperties.hasNext()) {
                Map.Entry<String, JsonNode> property = projectProperties.next();
                if( !property.getValue().isObject() )
                    project.setAdditionalProperty(property.getKey(), property.getValue());
            }

            Activity activity = new Activity();
            activity.setActor(pmc);
            activity.setVerb("member");
            activity.setObject(project);


            result.add( new StreamsDatum(activity, projectItem.getKey() ) );

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
        throw new NotImplementedException();
    }

    @Override
    public void prepare(Object o) {
        mapper = StreamsJacksonMapper.getInstance();
        is = ProjectProvider.class.getResourceAsStream("/testdata/projects.json");
    }

    @Override
    public void cleanUp() {

    }
}