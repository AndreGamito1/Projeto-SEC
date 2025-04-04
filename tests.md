I. Tests Focusing on Byzantine Replicas (Non-Leaders)

These replicas participate in consensus (e.g., voting, acknowledging) but deviate from the protocol. Assume you have f Byzantine replicas in these scenarios.

    Replica(s) Vote YES on Invalid Proposal:

        Scenario: A Byzantine client submits an invalid transaction (e.g., insufficient funds, bad signature). The honest leader correctly identifies it as invalid and sends abort messages. However, f Byzantine replicas pretend the block is valid and send YES votes for this invalid proposal.

        Setup: N nodes total, f Byzantine replicas (configured to vote YES incorrectly), 1 Byzantine client sending the bad tx, 1 honest leader.

        Expected Outcome (Safety): Honest replicas should ignore the YES votes corresponding to an invalid/non-existent proposal from the leader. They should follow the honest leader's valid proposal (or lack thereof). No invalid transaction should be committed. The system state on honest nodes remains consistent and correct.

    Replica(s) Vote NO on Valid Proposal:

        Scenario: The honest leader proposes a valid block containing valid transactions. f Byzantine replicas maliciously vote NO.

        Setup: N nodes total, f Byzantine replicas (configured to vote NO on valid blocks), honest clients sending valid txs, 1 honest leader.

        Expected Outcome (Safety & Liveness): Honest replicas should correctly validate the leader's proposal. With N = 3f + 1, there are N - f = 2f + 1 honest+Byzantine-voting-no nodes. The number of honest nodes is N - f. If the consensus requires 2f + 1 YES votes (a common threshold), the f NO votes from Byzantine nodes plus the f honest nodes = 2f votes which might not be enough to reach the commit threshold if the leader doesn't vote or its vote isn't counted specially. However, typically the threshold is based on total voting nodes. With N-f honest nodes voting YES, and potentially the leader itself voting YES, the threshold (f+1 or 2f+1 depending on protocol phase) should still be met by the honest nodes. The valid block should be committed. If the protocol stalls, it points to a potential liveness issue or incorrect threshold calculation/handling in your implementation. Safety (not committing bad blocks) must hold.

    Replica(s) Equivocate on Votes:

        Scenario: The honest leader proposes a valid block. f Byzantine replicas send YES votes to some honest peers and NO votes to other honest peers for the same proposal.

        Setup: N nodes total, f Byzantine replicas (configured to equivocate votes), honest clients, 1 honest leader.

        Expected Outcome (Safety): Honest nodes should detect the equivocation if they gossip/share votes or if the protocol relies on collecting a quorum of consistent votes. Depending on the specific BFT protocol (e.g., PBFT phases), equivocal votes might be discarded or trigger a fault handling mechanism. The system should not reach conflicting decisions. Honest nodes should either commit the valid block (if enough consistent YES votes are gathered despite equivocation) or potentially stall and trigger a view change/leader rotation (if the equivocation prevents reaching a quorum). No incorrect state should be committed.

    Replica(s) Omit Votes:

        Scenario: The honest leader proposes a valid block. f Byzantine replicas simply do not send their votes within the expected timeframe.

        Setup: N nodes total, f Byzantine replicas (configured to omit votes), honest clients, 1 honest leader.

        Expected Outcome (Liveness): Similar to voting NO. Honest nodes should proceed based on the votes received. If the number of votes from honest nodes is sufficient to meet the quorum, the block should be committed. If not receiving votes from f nodes prevents reaching the quorum, the system might stall, potentially triggering timeout mechanisms and trigerring abortion of commit.

    Replica(s) Flood Network with Gossip/Messages:

        Scenario: Byzantine replicas send excessive amounts of valid (but perhaps redundant) or invalid consensus messages (not proposals, as leader is honest) to try and overwhelm honest nodes' processing or network capacity.

        Setup: N nodes total, f Byzantine replicas (configured for message flooding).

        Expected Outcome: Honest nodes should have mechanisms (e.g., rate limiting, ignoring duplicates, basic validation) to handle message floods without crashing or violating safety. Liveness might be impacted if processing capacity is genuinely exceeded.

II. Tests Focusing on Byzantine Clients

These tests verify that the system (specifically the nodes, starting with the honest leader validating transactions) correctly handles malicious or malformed inputs from clients.

    Client Sends Transaction with Invalid Signature:

        Scenario: A Byzantine client creates a valid transaction structure but signs it incorrectly or tampers with it after signing.

        Setup: 1 Byzantine client, at least 1 honest node (including the leader).

        Expected Outcome: All honest nodes (especially the leader when selecting transactions for a proposal) must detect the invalid signature and reject the transaction. It should never be included in a block or executed.

    Client Sends Transaction with Insufficient Native DepCoin Balance:

        Scenario: A Byzantine client attempts a DepCoin transfer for more than their current balance.

        Setup: 1 Byzantine client (with known low balance), at least 1 honest node.

        Expected Outcome: Honest nodes must check the sender's balance against the amount + fees (if applicable) and reject the transaction. It should not be committed.
 
    Client Transaction Spamming:

        Scenario: A Byzantine client sends a very high volume of valid or invalid transactions to the network.

        Setup: 1 Byzantine client (configured for spamming), honest nodes.

        Expected Outcome: Honest nodes should handle the load gracefully. Invalid transactions should be rejected efficiently. Valid transactions might fill up blocks faster. Test if the system remains responsive and if transactions from honest clients can still get included eventually (liveness). Check for resource exhaustion on honest nodes.
