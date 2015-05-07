/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import ch.qos.logback.core.layout.EchoLayout;
import com.google.common.collect.Maps;
import org.apache.streams.config.StreamsConfigurator;
import org.apache.streams.console.ConsolePersistWriter;
import org.apache.streams.core.StreamBuilder;
import org.apache.streams.elasticsearch.ElasticsearchPersistWriter;
import org.apache.streams.graph.GraphPersistWriter;
import org.apache.streams.local.builders.LocalStreamBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Copies documents into a new index
 */
public class MboxPipeline implements Runnable {

    public final static String STREAMS_ID = "MboxPipeline";

    private final static Logger LOGGER = LoggerFactory.getLogger(MboxPipeline.class);

    public MboxPipeline() {
    }

    public static void main(String[] args)
    {
        LOGGER.info(StreamsConfigurator.config.toString());

        MboxPipeline mboxPipeline = new MboxPipeline();

        new Thread(mboxPipeline).start();

    }

    public void run() {

        MboxProvider mboxProvider = new MboxProvider();

//        GraphPersistWriter graphPersistWriter = new GraphPersistWriter();
//        ConsolePersistWriter consolePersistWriter = new ConsolePersistWriter();
        ElasticsearchPersistWriter elasticsearchPersistWriter = new ElasticsearchPersistWriter();

        Map<String, Object> streamConfig = Maps.newHashMap();
        streamConfig.put(LocalStreamBuilder.STREAM_IDENTIFIER_KEY, STREAMS_ID);
        streamConfig.put(LocalStreamBuilder.TIMEOUT_KEY, 7 * 24 * 60 * 1000);
        StreamBuilder builder = new LocalStreamBuilder(20000, streamConfig);

        builder.newReadCurrentStream(MboxProvider.STREAMS_ID, mboxProvider);
        builder.addStreamsPersistWriter(ElasticsearchPersistWriter.STREAMS_ID, elasticsearchPersistWriter, 1, MboxProvider.STREAMS_ID);
        builder.start();
    }
}
