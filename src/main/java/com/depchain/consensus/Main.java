package com.depchain.consensus;

import com.depchain.consensus.*;
import com.depchain.utils.*;

public class Main {

    public static void main(String[] args) {
        StringBuilder logArgBuilder = new StringBuilder("--log=");
         logArgBuilder//.append(Logger.STUBBORN_LINKS).append(",")
                      .append(Logger.AUTH_LINKS).append(",")
                      .append(Logger.LEADER_ERRORS).append(",")
                      .append(Logger.CLIENT_LIBRARY).append(",")
                      .append(Logger.MEMBER).append(",")
                      .append(Logger.CONDITIONAL_COLLECT).append(",")
                      .append(Logger.EPOCH_CONSENSUS);
        String logArg = logArgBuilder.toString();
        Logger.initFromArgs(logArg);

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
