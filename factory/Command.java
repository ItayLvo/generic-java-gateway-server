package factory;

import com.google.gson.JsonObject;
import connectionservice.RespondableChannel;

public interface Command {

    JsonObject getData();

    void execute(RespondableChannel respondableChannel);
}
