This app attempts to mimic the user's workload along with their
pattern of running UAC frequently.

The database starts off with a procedure call CommonProc that accepts
as its first argument a string selector that causes it to dispatch to
a particular BusinessLogic class that executes a few SQL statements.

For each invocation of UAC, it adds a new "BusinessLogic" class is
added to the server's jar, and a corresponding SQL statement is added
to CommonProc.

In order to generate unique SQL statements and ensure we are actually
planning them (instead of using cached plans), each BusinessProc has a
number between 0-99999 associated with it.  BusinessProc65714 for
example will execute a SQL statement that does a join of tables 6, 5,
7, 1, 4, in that order.

So TheApps's main thread continually invokes CommonProc with whatever
BusinessLogic classes are available, while a background thread called
UACThread adds new BusinessLogic via calling a shell script called
gen_classes_and_uac.sh.

UACThread maintains a file called numbers.txt that is used to tell the
gen_classes_and_uac.sh which BusinessLogic classes and SQL statements
should be generated.

Still to be done:
- CommonProc should be single-partitioned.  The last parameter to CommonProc can be used to do the partitioning.
- The tables being selected from should be populated with actual data
- The main thread should populate VoltTables with values to be passed to BusinessLogic classes, which should extract values from the VoltTable and pass them as params to the SQLStmts that it executes
- The generated SQL statements should be made to accept parameters
- TheApp should be able to run on a cluster with more than one node, but probably should only run UAC from one node, while the other nodes can discover with BusinessLogic classes are available by storing numbers.txt on a shared disk.
- The BusinessLogic classes should queue large numbers of SQL statements.  User's test case they had 300 queued.

Worth investigating: For result sets that are very large (could be
caused by queueing large numbers of statements), we allocate a buffer
for it in the EE and notify the topend via a call to
fallbackToEEAllocatedBuffer.  It could be that this interleaved with
calls to UAC is causing instability?  This is just a wild guess.
