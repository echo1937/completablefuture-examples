package com.example.completablefuture;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class CompletableFutureExamples {

    static ExecutorService executor = Executors.newFixedThreadPool(3, new ThreadFactory() {
        int count = 1;

        @Override
        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, "custom-executor-" + count++);
        }
    });

    static Random random = new Random();

    public static void main(String[] args) {
        try {
//            allOfAsyncExample();
            completeExceptionallyExample();
        } finally {
            executor.shutdown();
        }
    }

    // 1、 创建一个完成的CompletableFuture
    static void completedFutureExample() {
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message");
        // isDone用来返回future对象是否已经完成
        assertTrue(cf.isDone());
        assertEquals("message", cf.getNow(null));
    }

    // 2、运行一个简单的异步阶段
    static void runAsyncExample() {
        Runnable runnable = () -> {
            // https://stackoverflow.com/questions/2213340/what-is-a-daemon-thread-in-java
            printTimeAndThread("runAsync");
            assertTrue(Thread.currentThread().isDaemon());
            randomSleep();
        };

        // https://stackoverflow.com/questions/60159153/completablefuture-runasync-vs-supplyasync-when-to-choose-one-over-the-other
        CompletableFuture<Void> cf = CompletableFuture.runAsync(runnable);
        assertFalse(cf.isDone());
        sleepEnough();
        assertTrue(cf.isDone());
    }

    // 3、在前一个阶段上应用函数
    static void thenApplyExample() {
        Function<String, String> thenApply = s -> {
            printTimeAndThread("thenApply");
            // thenApply默认情况下不是在守护线程执行
            assertFalse(Thread.currentThread().isDaemon());
            return s.toUpperCase();
        };
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApply(thenApply);
        assertEquals("MESSAGE", cf.getNow(null));
    }

    // 4、在前一个阶段上异步应用函数
    static void thenApplyAsyncExample() {
        Function<String, String> thenApplyAsync = s -> {
            printTimeAndThread("thenApplyAsync");
            // thenApplyAsync默认情况下是在守护线程执行
            assertTrue(Thread.currentThread().isDaemon());
            randomSleep();
            return s.toUpperCase();
        };
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApplyAsync(thenApplyAsync);
        assertNull(cf.getNow(null));
        assertEquals("MESSAGE", cf.join());
    }

    // 5、使用定制的Executor在前一个阶段上异步应用函数
    static void thenApplyAsyncWithExecutorExample() {
        Function<String, String> thenApplyAsync = s -> {
            printTimeAndThread("thenApplyAsync");
            assertTrue(Thread.currentThread().getName().startsWith("custom-executor-"));
            assertFalse(Thread.currentThread().isDaemon());
            randomSleep();
            return s.toUpperCase();
        };
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApplyAsync(thenApplyAsync, executor);

        assertNull(cf.getNow(null));
        assertEquals("MESSAGE", cf.join());
    }

    // 6、消费前一阶段的结果
    static void thenAcceptExample() {
        StringBuilder result = new StringBuilder();
        Consumer<String> thenAccept = result::append;
        CompletableFuture.completedFuture("thenAccept message").thenAccept(thenAccept);
        assertTrue("Result was empty", result.length() > 0);
    }

    // 7、异步地消费迁移阶段的结果
    static void thenAcceptAsyncExample() {
        StringBuilder result = new StringBuilder();
        Consumer<String> thenAcceptAsync = s -> {
            printTimeAndThread("thenAcceptAsync");
            result.append(s);
        };
        CompletableFuture<Void> cf = CompletableFuture.completedFuture("thenAcceptAsync message").thenAcceptAsync(thenAcceptAsync);
        cf.join();
        assertTrue("Result was empty", result.length() > 0);
    }

    // 8、完成计算异常
    static void completeExceptionallyExample() {
        Function<String, String> thenApplyAsync = String::toUpperCase;
        // executor是一个delayed executor, 在执行前会延迟一秒。
        Executor executor = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApplyAsync(thenApplyAsync, executor);

        BiFunction<String, Throwable, String> biFunction = (s, th) -> (th != null) ? "message upon cancel" : "";
        // 1、exceptionHandler处理异常, 在异常情况下返回message upon cancel
        CompletableFuture<String> exceptionHandler = cf.handle(biFunction);

        // 2、completeExceptionally方法使用给定的异常来结束Future
        cf.completeExceptionally(new RuntimeException("completed exceptionally"));

        // 2.1、断言cf.isCompletedExceptionally()为true, 如果为false则抛出AssertionError, 并输出message作为错误提示信息。
        //     由于executor需要延迟1秒, 所以thenApplyAsync还未执行完成就以completeExceptionally的方式结束了
        assertTrue("Was not completed exceptionally", cf.isCompletedExceptionally());

        try {
            cf.join();
            fail("Should have thrown an exception");
        } catch (CompletionException ex) { // just for testing
            assertEquals("completed exceptionally", ex.getCause().getMessage());
        }

        // 1.1、exceptionHandler最终的值为："message upon cancel"
        assertEquals("message upon cancel", exceptionHandler.join());

    }

    static void cancelExample() {
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApplyAsync(String::toUpperCase,
                CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS));
        CompletableFuture<String> cf2 = cf.exceptionally(throwable -> "canceled message");
        assertTrue("Was not canceled", cf.cancel(true));
        assertTrue("Was not completed exceptionally", cf.isCompletedExceptionally());
        assertEquals("canceled message", cf2.join());
    }

    static void applyToEitherExample() {
        String original = "Message";
        CompletableFuture<String> cf1 = CompletableFuture.completedFuture(original)
                .thenApplyAsync(s -> delayedUpperCase(s));
        CompletableFuture<String> cf2 = cf1.applyToEither(
                CompletableFuture.completedFuture(original).thenApplyAsync(s -> delayedLowerCase(s)),
                s -> s + " from applyToEither");
        assertTrue(cf2.join().endsWith(" from applyToEither"));
    }

    static void acceptEitherExample() {
        String original = "Message";
        StringBuilder result = new StringBuilder();
        CompletableFuture<Void> cf = CompletableFuture.completedFuture(original)
                .thenApplyAsync(s -> delayedUpperCase(s))
                .acceptEither(CompletableFuture.completedFuture(original).thenApplyAsync(s -> delayedLowerCase(s)),
                        s -> result.append(s).append("acceptEither"));
        cf.join();
        assertTrue("Result was empty", result.toString().endsWith("acceptEither"));
    }

    static void runAfterBothExample() {
        String original = "Message";
        StringBuilder result = new StringBuilder();
        CompletableFuture.completedFuture(original).thenApply(String::toUpperCase).runAfterBoth(
                CompletableFuture.completedFuture(original).thenApply(String::toLowerCase),
                () -> result.append("done"));
        assertTrue("Result was empty", result.length() > 0);
    }

    static void thenAcceptBothExample() {
        String original = "Message";
        StringBuilder result = new StringBuilder();
        CompletableFuture.completedFuture(original).thenApply(String::toUpperCase).thenAcceptBoth(
                CompletableFuture.completedFuture(original).thenApply(String::toLowerCase),
                (s1, s2) -> result.append(s1 + s2));
        assertEquals("MESSAGEmessage", result.toString());
    }

    static void thenCombineExample() {
        String original = "Message";
        CompletableFuture<String> cf = CompletableFuture.completedFuture(original).thenApply(s -> delayedUpperCase(s))
                .thenCombine(CompletableFuture.completedFuture(original).thenApply(s -> delayedLowerCase(s)),
                        (s1, s2) -> s1 + s2);
        assertEquals("MESSAGEmessage", cf.getNow(null));
    }

    static void thenCombineAsyncExample() {
        String original = "Message";
        CompletableFuture<String> cf = CompletableFuture.completedFuture(original)
                .thenApplyAsync(s -> delayedUpperCase(s))
                .thenCombine(CompletableFuture.completedFuture(original).thenApplyAsync(s -> delayedLowerCase(s)),
                        (s1, s2) -> s1 + s2);
        assertEquals("MESSAGEmessage", cf.join());
    }

    static void thenComposeExample() {
        String original = "Message";
        CompletableFuture<String> cf = CompletableFuture.completedFuture(original).thenApply(s -> delayedUpperCase(s))
                .thenCompose(upper -> CompletableFuture.completedFuture(original).thenApply(s -> delayedLowerCase(s))
                        .thenApply(s -> upper + s));
        assertEquals("MESSAGEmessage", cf.join());
    }

    static void anyOfExample() {
        StringBuilder result = new StringBuilder();
        List<String> messages = Arrays.asList("a", "b", "c");
        List<CompletableFuture<String>> futures = messages.stream()
                .map(msg -> CompletableFuture.completedFuture(msg).thenApply(s -> delayedUpperCase(s)))
                .collect(Collectors.toList());
        CompletableFuture.anyOf(futures.toArray(new CompletableFuture[futures.size()])).whenComplete((res, th) -> {
            if (th == null) {
                assertTrue(isUpperCase((String) res));
                result.append(res);
            }
        });
        assertTrue("Result was empty", result.length() > 0);
    }

    static void allOfExample() {
        StringBuilder result = new StringBuilder();
        List<String> messages = Arrays.asList("a", "b", "c");
        List<CompletableFuture<String>> futures = messages.stream()
                .map(msg -> CompletableFuture.completedFuture(msg).thenApply(s -> delayedUpperCase(s)))
                .collect(Collectors.toList());
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).whenComplete((v, th) -> {
            futures.forEach(cf -> assertTrue(isUpperCase(cf.getNow(null))));
            result.append("done");
        });
        assertTrue("Result was empty", result.length() > 0);
    }

    static void allOfAsyncExample() {
        StringBuilder result = new StringBuilder();
        List<String> messages = Arrays.asList("a", "b", "c");
        List<CompletableFuture<String>> futures = messages.stream()
                .map(msg -> CompletableFuture.completedFuture(msg).thenApplyAsync(s -> delayedUpperCase(s)))
                .collect(Collectors.toList());
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]))
                .whenComplete((v, th) -> {
                    futures.forEach(cf -> assertTrue(isUpperCase(cf.getNow(null))));
                    result.append("done");
                });
        allOf.join();
        assertTrue("Result was empty", result.length() > 0);
    }

    private static boolean isUpperCase(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLowerCase(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String delayedUpperCase(String s) {
        randomSleep();
        return s.toUpperCase();
    }

    private static String delayedLowerCase(String s) {
        randomSleep();
        return s.toLowerCase();
    }

    private static void randomSleep() {
        try {
            Thread.sleep(random.nextInt(1000));
        } catch (InterruptedException e) {
            // ...
        }
    }

    private static void sleepEnough() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            // ...
        }
    }

    private static void printTimeAndThread(String tag) {
        String result = new StringJoiner("\t|\t")
                .add(String.valueOf(System.currentTimeMillis()))
                .add(String.valueOf(Thread.currentThread().getId()))
                .add(Thread.currentThread().getName())
                .add(tag)
                .toString();
        System.out.println(result);
    }

}
