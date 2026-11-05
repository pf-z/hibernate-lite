package me.pfzh.hibernatelite.examples;

/**
 * Runs every example in order.
 *
 * <pre>
 * mvn test-compile
 * mvn exec:java -Dexec.mainClass=me.pfzh.hibernatelite.examples.RunAll -Dexec.classpathScope=test
 * </pre>
 *
 * <p>Each example opens its own in-memory database, so order does not matter.
 * Run a single example by naming its class instead, for example
 * {@code -Dexec.mainClass=me.pfzh.hibernatelite.examples.PaginationExample}.</p>
 */
public final class RunAll {

    public static void main(String[] args) {
        CrudExample.main(args);
        QueryOperatorExample.main(args);
        PaginationExample.main(args);
        BulkOperationExample.main(args);
        JpqlExample.main(args);
        TransactionExample.main(args);
        EscapeHatchExample.main(args);
        LifecycleExample.main(args);
        ErrorHandlingExample.main(args);
        OptimisticLockingExample.main(args);
        System.out.println();
        System.out.println("All examples finished.");
    }
}
