package connectionservice;

import java.nio.ByteBuffer;

public interface RespondableChannel {
    void respond(ByteBuffer data);
}
