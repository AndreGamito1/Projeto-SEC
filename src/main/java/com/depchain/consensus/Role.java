package com.depchain.consensus;

import com.depchain.utils.*;
import com.depchain.networking.*;

public interface Role {
    void start() throws Exception;
    void processMessage(String sourceId, AuthenticatedMessage message) throws Exception;
    void processClientCommand(String command, String payload) throws Exception;
    void processMemberMessage(String memberName, String command, String payload, AuthenticatedMessage message) throws Exception;
    void logReceivedMessagesStatus() throws Exception;
    void handleCollectedMessage(AuthenticatedMessage message) throws Exception;
    void handleStateMessage(AuthenticatedMessage message) throws Exception;
    void handleProposeMessage(Message message) throws Exception;
}