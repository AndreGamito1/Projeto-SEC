package com.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.depchain.*;
import com.depchain.consensus.Member;
import com.depchain.blockchain.*;

/**
 * Test to demonstrate the Byzantine fault tolerance of the system
 * with one NoMan node that always aborts consensus.
 */
public class ByzantineNoManTest {
    
    private static final int TEST_DURATION_SECONDS = 60;
    private static final int NUM_TRANSACTIONS = 10;
    
    public static void main(String[] args) throws Exception {
        
        // Create a CountDownLatch for synchronization
        CountDownLatch latch = new CountDownLatch(1);
        
        // Create 4 members, making one of them a NoMan Byzantine node
        List<Member> members = createMembers();
        
        // Let the system stabilize before sending transactions
        System.out.println("Waiting for system to stabilize...");
        Thread.sleep(5000);
        
        // Send test transactions
        System.out.println("Sending test transactions...");
        sendTestTransactions(members.get(0));
        
        // Wait for test duration
        System.out.println("Test running for " + TEST_DURATION_SECONDS + " seconds...");
        latch.await(TEST_DURATION_SECONDS, TimeUnit.SECONDS);
        
        // Compare final blockchain states
        compareBlockchainStates(members);
        
        
        System.out.println("Test completed.");
    }
    
    /**
     * Creates 4 members, including one Byzantine NoMan node.
     */
    private static List<Member> createMembers() throws Exception {
        List<Member> members = new ArrayList<>();
        
        // Create 3 normal members
        for (int i = 0; i < 3; i++) {
            String memberName = "member" + i;
            Member member = new Member(memberName, "NORMAL");
            members.add(member);
            System.out.println("Created normal member: " + memberName);
        }
        
        // Create 1 Byzantine NoMan member
        Member byzantineMember = new Member("byzantineMember", "NOMAN");
        members.add(byzantineMember);
        System.out.println("Created Byzantine NoMan member: byzantineMember");
        
        // Wait for members to connect to each other
        Thread.sleep(2000);
        
        return members;
    }
    
    /**
     * Sends test transactions to the network through a given member.
     */
    private static void sendTestTransactions(Member sender) {
        try {
            for (int i = 0; i < NUM_TRANSACTIONS; i++) {
                // Create a simple transaction
                Transaction transaction = createTestTransaction(i);
                
                // Submit the transaction
                sender.submitTransaction(transaction);
                
                // Space out transactions slightly
                Thread.sleep(500);
            }
        } catch (Exception e) {
            System.err.println("Error sending transactions: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Creates a test transaction.
     */
    private static Transaction createTestTransaction(int index) {
        // This is a placeholder - replace with actual Transaction creation logic
        // For example:
        // return new Transaction(sender, recipient, amount, fee);
        
        // Using placeholder values:
        String sender = "Alice";
        String recipient = "Bob";
        double amount = 10.0 + index;
        double fee = 0.1;
        
        return new Transaction(sender, recipient, amount, fee);
    }
    
    /**
     * Compares the final blockchain states across all members.
     */
    private static void compareBlockchainStates(List<Member> members) {
        System.out.println("\n--- Final Blockchain State Comparison ---");
        
        // Print blockchain sizes
        for (Member member : members) {
            System.out.println(member.getName() + " blockchain size: " + member.getBlockchain().size() + " blocks");
        }
        
        // Check if normal members have consistent blockchain sizes
        boolean consistentSize = true;
        int normalSize = members.get(0).getBlockchain().size();
        
        for (int i = 1; i < members.size() - 1; i++) { // Skip Byzantine member
            if (members.get(i).getBlockchain().size() != normalSize) {
                consistentSize = false;
                break;
            }
        }
        
        System.out.println("Normal members have consistent blockchain sizes: " + consistentSize);
        
        // Check Byzantine member
        Member byzantineMember = members.get(members.size() - 1);
        System.out.println("Byzantine member (" + byzantineMember.getName() + ") blockchain size: " + 
                           byzantineMember.getBlockchain().size() + " blocks");
        
        // Check if Byzantine member's blockchain size differs
        boolean byzantineDiffers = byzantineMember.getBlockchain().size() != normalSize;
        System.out.println("Byzantine member's blockchain differs from normal members: " + byzantineDiffers);
        
        // Additional analysis
        System.out.println("\n--- Detailed Block Analysis ---");
        Member referenceNormalMember = members.get(0);
        for (int i = 0; i < Math.min(5, referenceNormalMember.getBlockchain().size()); i++) {
            System.out.println("Block " + i + " from normal member: " + 
                              referenceNormalMember.getBlockchain().get(i).getHash());
        }
        
        // Output summary
        System.out.println("\n--- Test Results Summary ---");
        System.out.println("Total transactions submitted: " + NUM_TRANSACTIONS);
        System.out.println("Final blockchain size in normal members: " + normalSize);
        if (byzantineDiffers) {
            System.out.println("The Byzantine NoMan member affected consensus as expected");
        } else {
            System.out.println("The Byzantine NoMan member did not disrupt consensus as expected");
        }
    }
}