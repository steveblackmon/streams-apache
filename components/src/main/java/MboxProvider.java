import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Header;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageBuilder;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.mboxiterator.CharBufferWrapper;
import org.apache.james.mime4j.mboxiterator.MboxIterator;
import org.apache.james.mime4j.message.BodyPart;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.message.HeaderImpl;
import org.apache.james.mime4j.stream.Field;
import org.apache.streams.core.StreamsDatum;
import org.apache.streams.core.StreamsProvider;
import org.apache.streams.core.StreamsResultSet;
import org.apache.streams.jackson.StreamsJacksonMapper;
import org.apache.streams.pojo.json.Activity;
import org.apache.streams.pojo.json.ActivityObject;
import org.apache.streams.pojo.json.Actor;
import org.apache.streams.pojo.json.Provider;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.DefaultHandler;
import sun.reflect.generics.reflectiveObjects.LazyReflectiveObjectGenerator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MboxProvider implements StreamsProvider {

    public final static String STREAMS_ID = "MboxProvider";

    private final static Logger LOGGER = LoggerFactory.getLogger(MboxProvider.class);

    private final static CharsetEncoder ENCODER = Charset.forName("ISO-8859-1").newEncoder();

    ObjectMapper mapper;
    List<MboxIterator> mboxList;

    private final int DEFAULT_MESSAGE_SIZE = 10 * 1024;
    // number of chars oin our largest test message
    private static final int CHARS_IN_MAX_MSG = 100000;
    private static final int MORE_THAN_FILE_SIZE = 13291;

    boolean done = false;

    public void startStream() {
        throw new NotImplementedException();
    }

    public StreamsResultSet readCurrent() {
        Preconditions.checkNotNull(mapper);

        Queue<StreamsDatum> results = Queues.newConcurrentLinkedQueue();

        for (MboxIterator mbox : mboxList) {

            Collection<StreamsDatum> result = processMbox(mbox);

            results.addAll(result);
        }

        return new StreamsResultSet(results);
    }

    public Collection<StreamsDatum> processMbox(MboxIterator mbox) {

        Iterator<CharBufferWrapper> iterator = mbox.iterator();
        Integer count = 0;
        Collection<StreamsDatum> result = Lists.newArrayList();

        while(iterator.hasNext())

        {
            StringBuffer txtBody = new StringBuffer();
            StringBuffer htmlBody = new StringBuffer();
            List<BodyPart> attachments = new ArrayList();

            CharBufferWrapper item = iterator.next();
            try {
                Message message = message(item.asInputStream(ENCODER.charset()));
                LOGGER.debug("To: " + message.getTo().toString());
                LOGGER.debug("From: " + message.getFrom().toString());
                LOGGER.debug("Subject: " + message.getSubject());

                if (message.isMultipart()) {
                    Multipart multipart = (Multipart) message.getBody();
                    parseBodyParts(txtBody, htmlBody, attachments, multipart);
                } else {
                    //If it's single part message, just get text body
                    String text = getTxtPart(message);
                    txtBody.append(text);
                }

                LOGGER.debug("txtBody: ", txtBody.toString());
                LOGGER.debug("htmlBody: ", htmlBody.toString());
                LOGGER.debug("attachments", attachments.size());

                Actor actor = new Actor();
                actor.setId(message.getFrom().get(0).toString());
                actor.setDisplayName(message.getFrom().get(0).getName());
                actor.setObjectType("person");

                Activity activity = new Activity();
                activity.setActor(actor);

                if (!Strings.isNullOrEmpty(txtBody.toString()))
                    activity.setContent(txtBody.toString());
                else if (!Strings.isNullOrEmpty(htmlBody.toString()))
                    activity.setContent(htmlBody.toString());

                if( !Strings.isNullOrEmpty(message.getSubject()))
                    activity.setAdditionalProperty("summary", message.getSubject());

                Provider provider = new Provider();
                provider.setDisplayName("MboxProvider");
                provider.setId("MboxProvider");
                provider.setObjectType("application");
                activity.setProvider(provider);
                activity.setPublished(new DateTime(message.getDate()));

                StreamsDatum messageDatum = new StreamsDatum(activity, message.getMessageId(), new DateTime(message.getDate()));

                Map<String, Object> metadata = Maps.newHashMap();
                for( Field field : message.getHeader().getFields()) {
                    metadata.put(field.getName(), field.getBody());
                }
                messageDatum.setMetadata(metadata);
                result.add(messageDatum);

                count++;
            } catch (Throwable e) {
                LOGGER.warn("Bad thing happened.", e);
            }
        }

        LOGGER.info("Read messages: ",count);

        return result;
    }

    public StreamsResultSet readNew(BigInteger bigInteger) {
        throw new NotImplementedException();
    }

    public StreamsResultSet readRange(DateTime dateTime, DateTime dateTime1) {
        throw new NotImplementedException();
    }

    public boolean isRunning() {
        return !done;
    }

    public void prepare(Object o) {
        mapper = StreamsJacksonMapper.getInstance();
        mboxList = Lists.newArrayList();

        for( int year = 2009; year <= 2015; year++ )
            for( int month = 01; month <= 12; month++ ) {
                NumberFormat format = DecimalFormat.getInstance();
                format.setMinimumIntegerDigits(2);
                format.setMaximumIntegerDigits(4);
                format.setGroupingUsed(false);
                String partFile = "/tmp/community-dev/"+format.format(year)+format.format(month)+".mbox";
                try {
                    mboxList.add( MboxIterator.fromFile(partFile).maxMessageSize(CHARS_IN_MAX_MSG).build() );
                } catch (Exception e) {
                    LOGGER.error("", e);
                }
            }

    }

    public void cleanUp() {
        try {
            for( MboxIterator mbox : mboxList )
                mbox.close();
        } catch( Exception e ) {}
        finally {
            done = true;
        }
    }

    /**
     * Parse a message and return a simple {@link String} representation of some important fields.
     *
     * @param messageBytes the message as {@link java.io.InputStream}
     * @return String
     * @throws IOException
     * @throws MimeException
     */
    private static Message message(InputStream messageBytes) throws IOException, MimeException {
        MessageBuilder builder = new DefaultMessageBuilder();
        Message message = builder.parseMessage(messageBytes);
        return message;
    }

    /**
     * This method classifies bodyPart as text, html or attached file
     *
     * @param multipart
     * @throws IOException
     */
    private void parseBodyParts(StringBuffer txtBody, StringBuffer htmlBody, List<BodyPart> attachments, Multipart multipart) throws IOException {
        for (Entity partEntity : multipart.getBodyParts()) {
            BodyPart part = (BodyPart) partEntity;
            if (part.getMimeType().equals("text/plain")) {
                String txt = getTxtPart(part);
                txtBody.append(txt);
            } else if (part.getMimeType().equals("text/html")) {
                String html = getTxtPart(part);
                htmlBody.append(html);
            } else if (part.getDispositionType() != null && !part.getDispositionType().equals("")) {
                //If DispositionType is null or empty, it means that it's multipart, not attached file
                attachments.add(part);
            }

            //If current part contains other, parse it again by recursion
            if (part.isMultipart()) {
                parseBodyParts(txtBody, htmlBody, attachments, (Multipart) part.getBody());
            }
        }
    }

    /**
     *
     * @param part
     * @return
     * @throws IOException
     */
    private String getTxtPart(Entity part) throws IOException {
        //Get content from body
        TextBody tb = (TextBody) part.getBody();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        tb.writeTo(baos);
        return new String(baos.toByteArray());
    }

}