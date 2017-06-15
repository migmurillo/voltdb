#!/usr/bin/env python


from os.path import isfile
import random
import sys

def gen_select_stmt(table_numbers):
    table_numbers_str = str(table_numbers)
    stmt = 'select t1.value1 \n  from '
    tbl_num = table_numbers_str[-1]
    table_numbers_str = table_numbers_str[0:-1]
    stmt += ('table' + tbl_num + ' t1')
    alias_num = '2'

    for j in range(len(table_numbers_str)):
        last_alias_num = str(int(alias_num) - 1)
        tbl_num = table_numbers_str[-1]
        table_numbers_str = table_numbers_str[0:-1]
        stmt += ('\n  inner join table' + tbl_num + ' as t' + alias_num
                 + ' on t' + last_alias_num + '.pk = t' + alias_num + '.pk ')
        alias_num = str(int(alias_num) + 1)
    stmt += '\n  order by 1'
    return stmt

common_proc_template = '''
package volttable_segv;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

public class CommonProc extends VoltProcedure {

%(statements)s

    public VoltTable[] run(String str,
                           VoltTable vtIn1,
                           VoltTable vtIn2,
                           VoltTable vtIn3,
                           VoltTable vtIn4,
                           VoltTable vtIn5,
                           int intParam) {
        BusinessLogicProcedure subProc = null;

%(dispatch)s
        assert (subProc != null);
        return subProc.run(vtIn1, vtIn2, vtIn3, vtIn4, vtIn5);
    }

}
'''

def gen_common_procedure(set_of_statements):
    sql_stmts = ''
    for table_numbers in set_of_statements:
        stmt = gen_select_stmt(table_numbers).encode('string_escape').replace('\\n', '"\n        + "')
        sql_stmts += ('    public static final SQLStmt stmt' + str(table_numbers) + ' = new SQLStmt("' + stmt + '");\n')

    dispatch_code = ''
    first_one = True
    for table_numbers in set_of_statements:
        dispatch_code += '        '
        if not first_one:
            dispatch_code += 'else '
        else:
            first_one = False

        dispatch_code += ('if (str.equals("BusinessLogic' + str(table_numbers) + '"))\n'
                          + '            subProc = new BusinessLogic' + str(table_numbers) + '(this);\n')

    common_proc = (common_proc_template % {"statements": sql_stmts, "dispatch": dispatch_code})
    with open("procedures/volttable_segv/CommonProc.java", "w") as common_proc_java_file:
        common_proc_java_file.write(common_proc)

business_logic_proc_superclass = '''
package volttable_segv;

import org.voltdb.VoltTable;

public abstract class BusinessLogicProcedure {
    public abstract VoltTable[] run(VoltTable vtIn1,
                                    VoltTable vtIn2,
                                    VoltTable vtIn3,
                                    VoltTable vtIn4,
                                    VoltTable vtIn5);
}
'''

business_logic_proc_template = '''
package volttable_segv;

import org.voltdb.SQLStmt;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;

public class BusinessLogic%(proc_num)s extends BusinessLogicProcedure {

    private final VoltProcedure m_commonProc;

    BusinessLogic%(proc_num)s(VoltProcedure commonProc) {
        m_commonProc = commonProc;
    }

    public VoltTable[] run(VoltTable vtIn1,
                           VoltTable vtIn2,
                           VoltTable vtIn3,
                           VoltTable vtIn4,
                           VoltTable vtIn5) {
        m_commonProc.voltQueueSQL(CommonProc.stmt%(proc_num)s);
        m_commonProc.voltQueueSQL(CommonProc.stmt%(proc_num)s);
        m_commonProc.voltQueueSQL(CommonProc.stmt%(proc_num)s);
        m_commonProc.voltQueueSQL(CommonProc.stmt%(proc_num)s);
        m_commonProc.voltQueueSQL(CommonProc.stmt%(proc_num)s);
        return m_commonProc.voltExecuteSQL();
    }

}
'''

def gen_business_logic_procedures(set_of_statements):
    superclass_file_name = 'procedures/volttable_segv/BusinessLogicProcedure.java'
    if not isfile(superclass_file_name):
        with open(superclass_file_name, "w") as superclass_file:
            superclass_file.write(business_logic_proc_superclass)

    for table_numbers in set_of_statements:
        logic_proc = (business_logic_proc_template % {'proc_num': str(table_numbers)})
        file_name = ('procedures/volttable_segv/BusinessLogic%(proc_num)s.java' % {'proc_num':str(table_numbers)})
        with open(file_name, "w") as logic_proc_java_file:
            logic_proc_java_file.write(logic_proc)

statements = set()

if len(sys.argv) < 2:
    print ("Please supply a path as the first argument to " + sys.argv[0] + ".")
    print ("This should be a list of numbers one on each line, corresponding to")
    print ("The java files to be generated")
    sys.exit(1)

with open(sys.argv[1], "r") as numbers_file:
    statements = numbers_file.readlines()
    statements = [numstr.strip() for numstr in statements]

print "Generating java procedures..."

gen_common_procedure(statements)
gen_business_logic_procedures(statements)

print "Done."
