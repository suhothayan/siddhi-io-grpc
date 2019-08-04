package io.siddhi.extension.io.grpc.sink;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import io.siddhi.annotation.Example;
import io.siddhi.annotation.Extension;
import io.siddhi.annotation.Parameter;
import io.siddhi.annotation.util.DataType;
import io.siddhi.core.exception.ConnectionUnavailableException;
import io.siddhi.core.exception.SiddhiAppRuntimeException;
import io.siddhi.core.util.snapshot.state.State;
import io.siddhi.core.util.transport.DynamicOptions;
import io.siddhi.core.util.transport.OptionHolder;
import io.siddhi.extension.io.grpc.util.GrpcConstants;
import io.siddhi.extension.io.grpc.util.GrpcSourceRegistry;
import io.siddhi.query.api.exception.SiddhiAppValidationException;
import org.apache.log4j.Logger;
import org.wso2.grpc.Event;
import org.wso2.grpc.EventServiceGrpc;
import org.wso2.grpc.EventServiceGrpc.EventServiceFutureStub;

/**
 * {@code GrpcCallSink} Handle the gRPC publishing tasks and injects response into grpc-call-response source.
 */
@Extension(
        name = "grpc-call", namespace = "sink",
        description = "This extension publishes event data encoded into GRPC Classes as defined in the user input " +
                "jar. This extension has a default gRPC service classes jar added. The default service is called " +
                "\"EventService\" and it has 2 rpc's. They are process and consume. Process sends a request of type " +
                "Event and receives a response of the same type. Consume sends a request of type Event and expects " +
                "no response from gRPC server. Please note that the Event type mentioned here is not " +
                "io.siddhi.core.event.Event but a type defined in the default service protobuf given in the readme. " +
                "This grpc-call sink is used for scenarios where we send a request out and expect a response back. " +
                "In default mode this will use EventService process method.",
        parameters = {
                @Parameter(name = "url",
                        description = "The url to which the outgoing events should be published via this extension. " +
                                "This url should consist the host address, port, service name, method name in the " +
                                "following format. grpc://hostAddress:port/serviceName/methodName" ,
                        type = {DataType.STRING}),
                @Parameter(name = "sink.id",
                        description = "a unique ID that should be set for each gRPC sink. There is a 1:1 mapping " +
                                "between gRPC sinks and sources. Each sink has one particular source listening to " +
                                "the responses to requests published from that sink. So the same sink.id should be " +
                                "given when writing the source also." ,
                        type = {DataType.INT}),
        },
        examples = {
                @Example(
                        syntax = "@sink(type='grpc-call', " +
                                "url = 'grpc://194.23.98.100:8080/EventService/process', " +
                                "sink.id= '1', @map(type='json')) "
                                + "define stream FooStream (message String);",
                        description = "Here a stream named FooStream is defined with grpc sink. A grpc server " +
                                "should be running at 194.23.98.100 listening to port 8080. sink.id is set to 1 " +
                                "here. So we can write a source with sink.id 1 so that it will listen to responses " +
                                "for requests published from this stream. Note that since we are using " +
                                "EventService/process the sink will be operating in default mode"
                        //todo: add an example for generic service access
                )
        }
)
public class GrpcCallSink extends AbstractGrpcSink {
    private static final Logger logger = Logger.getLogger(GrpcCallSink.class.getName());
    protected String sinkID;

    @Override
    public void initSink(OptionHolder optionHolder) {
        if (optionHolder.isOptionExists(GrpcConstants.SINK_ID)) {
            this.sinkID = optionHolder.validateAndGetOption(GrpcConstants.SINK_ID).getValue();
        } else {
            if (optionHolder.validateAndGetOption(GrpcConstants.SINK_TYPE_OPTION)
                    .getValue().equalsIgnoreCase(GrpcConstants.GRPC_CALL_SINK_NAME)) {
                throw new SiddhiAppValidationException(siddhiAppContext.getName() + ": For grpc-call sink the " +
                        "parameter sink.id is mandatory for receiving responses. Please provide a sink.id");
            }
        }
    }

    @Override
    public void publish(Object payload, DynamicOptions dynamicOptions, State state)
            throws ConnectionUnavailableException {
        if (isDefaultMode) {
            if (!(payload instanceof String)) {
                throw new SiddhiAppRuntimeException(siddhiAppContext.getName() + ": Payload should be of type String " +
                        "for communicating with Micro Integrator but found " + payload.getClass().getName());
            }
            Event.Builder requestBuilder = Event.newBuilder();
            requestBuilder.setPayload((String) payload);
            Event sequenceCallRequest = requestBuilder.build();
            EventServiceFutureStub futureStub = EventServiceGrpc.newFutureStub(channel);
            ListenableFuture<Event> futureResponse =
                    futureStub.process(sequenceCallRequest);
            Futures.addCallback(futureResponse, new FutureCallback<Event>() {
                @Override
                public void onSuccess(Event result) {
                    GrpcSourceRegistry.getInstance().getGrpcCallResponseSourceSource(sinkID).onResponse(result);
                }

                @Override
                public void onFailure(Throwable t) {
                    if (logger.isDebugEnabled()) {
                        logger.debug(siddhiAppContext.getName() + ": " + t.getMessage());
                    }
                }
            }, MoreExecutors.directExecutor());
        } else {
            //todo: handle publishing to generic service
        }
    }
}
