package volttable_segv;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;



public class UACThread implements Runnable {

    final private Random m_random = new Random();
    final private List<Integer> m_logicProcNumbers; // synchronized

    UACThread(TheApp theClient) {
        m_logicProcNumbers = theClient.getLogicProcNumbers();
    }

    @Override
    public void run() {
        System.out.println("Started UACThread...");
        try (BufferedReader br = new BufferedReader(new FileReader("./numbers.txt"))) {
            List<Integer> logicProcNumbers = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                logicProcNumbers.add(Integer.parseInt(line));
            }
            m_logicProcNumbers.addAll(logicProcNumbers);
        }
        catch (Exception e) {
            System.err.println("Problem opening numbers.txt for starting logic procs");
            System.exit(-1);
        }

        while (true) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Main thread is finished...
                return;
            }

            // Choose a new procedure number to add
            int nextProcNum = m_random.nextInt(100000);
            while (m_logicProcNumbers.contains(nextProcNum)) {
                nextProcNum = m_random.nextInt(100000);
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter("./numbers.txt", true))) {
                bw.write(Integer.toString(nextProcNum) + "\n");
            }
            catch (Exception e) {
                System.err.println("Problem writing numbers.txt for generating logic procs");
                System.exit(-1);
            }

            Runtime r = Runtime.getRuntime();
            int returnCode = 0;
            try {
                Process p = r.exec("./gen_classes_and_uac.sh ./numbers.txt");
                returnCode = p.waitFor();
            }
            catch (Exception e) {
                System.err.println("Could not generate new classes and update application catalog: "
                        + e.getMessage());
                System.exit(-1);
            }

            if (returnCode != 0) {
                System.err.println("Something went wrong calling UAC, return code: " + returnCode);
                System.exit(-1);
            }

            m_logicProcNumbers.add(nextProcNum);
        }
    }
}
