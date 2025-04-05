package com.depchain.consensus;

import com.depchain.utils.*;
import com.depchain.blockchain.Block;
import com.depchain.networking.*;

public interface Role {
    void start() throws Exception;
    void processMessage(String sourceId, AuthenticatedMessage message) throws Exception;
    void processClientCommand(String command, String payload) throws Exception;
    void processMemberMessage(String memberName, String command, String payload, AuthenticatedMessage message) throws Exception;
    void logReceivedMessagesStatus() throws Exception;
    void handleReadMessage(Message message) throws Exception;
    void handleCollectedMessage(AuthenticatedMessage message) throws Exception;
    void handleStateMessage(AuthenticatedMessage message) throws Exception;
    void ProposeBlock(Block block) throws Exception;
    void handleCheckBalanceMessage(Message message) throws Exception;
    void handleTransactionMessage(Message message) throws Exception;
    void handleAckMessage(Message message) throws Exception;
    void handleDecideMessage(Message message) throws Exception;
    void handleAbortMessage(Message message) throws Exception;
    void saveBlock(Block block) throws Exception;
    void decided() throws Exception;
    void aborted() throws Exception;
}