
package volttable_segv;

import java.util.Collections;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

public class Initializer extends VoltProcedure {

    //    final SQLStmt insertStmt = new SQLStmt("insert into t_poc_1 values (?, ?, ?, ?, ?)");

    public VoltTable[] run() {
        // final int NUM_ROWS = 3;
        // final int BATCH_SIZE = 3;
        // final int numBatches = NUM_ROWS / BATCH_SIZE;
        // //Random rand = new Random();
        // String[] types = {"mcc_code", "merchant_black_list", "time_for_distance"};

        // for (int j = 0; j < numBatches; ++j) {
        //     for (int i = 0; i < BATCH_SIZE; ++i) {
        //         String theType = types[i % types.length];
        //         //int suffix = j * BATCH_SIZE + i;
        //         //theType += Integer.toString(suffix);

        //         char aChar1 = (char) ('a' + (i % 3));
        //         char aChar2 = (char) ('b' + (i % 3));
        //         char aChar3 = (char) ('c' + (i % 3));
        //         String value1 = String.join("", Collections.nCopies(50, "" + aChar1));
        //         String value2 = String.join("", Collections.nCopies(50, "" + aChar2));
        //         String value3 = String.join("", Collections.nCopies(50, "" + aChar3));

        //         int ver = i % 127;
        //         voltQueueSQL(insertStmt, ver, theType, value1, value2, value3);
        //     }

        //     voltExecuteSQL(j == numBatches - 1);
        // }

        return null;
    }

}
