package com.depchain.consensus;

import com.depchain.consensus.*;
import com.depchain.utils.*;

public class Main {

    public static void main(String[] args) {
        Logger.initFromArgs("--log=1,2,3,4,5,6"); 
        if (args.length < 1) {
            System.out.println("Please provide a member name as an argument.");
            return;
        }
        try {
            Member member = new Member(args[0]);
            member.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Creating member: " + args[0]);
    }
}
