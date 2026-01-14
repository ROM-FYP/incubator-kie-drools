package org.drools.benchmark.waltzdb;

import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class MiningTraceLogger extends DefaultAgendaEventListener {

    private BufferedWriter writer;
    private long transactionId = 0; // Acts as your "Case ID" for mining
    private int sequenceCounter = 0; // Ordering within the transaction

    public MiningTraceLogger(String filePath) {
        try {
            writer = new BufferedWriter(new FileWriter(filePath));
            // CSV Header: CaseID is the transaction, Activity is the Rule Name
            writer.write("CaseID,SequenceNr,Activity,Timestamp\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Call this before insertion/fireAllRules to mark a new "Request"
    public void startNewTransaction() {
        transactionId++;
        sequenceCounter = 0;
    }

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        try {
            sequenceCounter++;
            String ruleName = event.getMatch().getRule().getName();
            long timestamp = System.currentTimeMillis();

            // Write straight to CSV
            // We sanitize ruleName to remove commas to prevent CSV breakage
            writer.write(String.format("%d,%d,%s,%d\n",
                    transactionId,
                    sequenceCounter,
                    ruleName.replace(",", " "),
                    timestamp));
            writer.flush(); // Ensure data is written
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
