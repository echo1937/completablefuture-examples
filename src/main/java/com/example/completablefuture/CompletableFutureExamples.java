package com.example.completablefuture;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.StringJoiner;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
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
            allOfAsyncExample();
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
        Runnable runAsync = () -> {
            // https://stackoverflow.com/questions/2213340/what-is-a-daemon-thread-in-java
            printTimeAndThread("runAsync");
            assertTrue(Thread.currentThread().isDaemon());
            randomSleep();
        };

        // https://stackoverflow.com/questions/60159153/completablefuture-runasync-vs-supplyasync-when-to-choose-one-over-the-other
        CompletableFuture<Void> cf = CompletableFuture.runAsync(runAsync);
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
        // cf是异步的，所以需要cf.join以后才能assertTrue的condition为真
        assertTrue("Result was empty", result.length() > 0);
    }

    // 8、完成计算异常
    static void completeExceptionallyExample() {
        Function<String, String> thenApplyAsync = String::toUpperCase;
        // executor是一个delayed executor, 在执行前会延迟一秒。
        Executor executor = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApplyAsync(thenApplyAsync, executor);

        BiFunction<String, Throwable, String> biFunction = (s, th) -> (th != null) ? "message upon cancel" : "";
        // CompletableFuture提供了三种处理异常的方式，分别是exceptionally、handle和whenComplete方法
        CompletableFuture<String> exceptionHandler = cf.handle(biFunction);

        // 1. completeExceptionally方法使用给定的异常来结束Future
        cf.completeExceptionally(new RuntimeException("completed exceptionally"));

        // 2. 断言cf.isCompletedExceptionally()为true, 如果为false则抛出AssertionError, 并输出message作为错误提示信息。
        //    由于executor需要延迟1秒, 所以thenApplyAsync还未执行完成就以completeExceptionally的方式结束了
        assertTrue("Was not completed exceptionally", cf.isCompletedExceptionally());

        try {
            cf.join();
            fail("Should have thrown an exception");
        } catch (CompletionException ex) { // just for testing
            assertEquals("completed exceptionally", ex.getCause().getMessage());
        }

        // exceptionHandler最终的值为："message upon cancel"
        assertEquals("message upon cancel", exceptionHandler.join());

    }

    // 9、取消计算
    static void cancelExample() {
        Function<String, String> thenApplyAsync = String::toUpperCase;
        // executor是一个delayed executor, 在执行前会延迟一秒。
        Executor executor = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS);
        CompletableFuture<String> cf = CompletableFuture.completedFuture("message").thenApplyAsync(thenApplyAsync, executor);

        // CompletableFuture提供了三种处理异常的方式，分别是exceptionally、handle和whenComplete方法
        Function<Throwable, String> throwableStringFunction = throwable -> "canceled message";
        CompletableFuture<String> cf2 = cf.exceptionally(throwableStringFunction);

        // cancel等价于completeExceptionally(new CancellationException())。
        assertTrue("Was not canceled", cf.cancel(true));
        assertTrue("Was not completed exceptionally", cf.isCompletedExceptionally());
        assertEquals("canceled message", cf2.join());
    }

    // 10、在两个完成的阶段其中之一上应用函数
    static void applyToEitherExample() {
        String original = "Message";
        Function<String, String> delayedUpperCase = CompletableFutureExamples::delayedUpperCase;
        Function<String, String> delayedLowerCase = CompletableFutureExamples::delayedLowerCase;
        Function<String, String> function = s -> s + " from applyToEither";

        CompletableFuture<String> cf1 = CompletableFuture.completedFuture(original).thenApplyAsync(delayedUpperCase);
        CompletableFuture<String> cf2 = CompletableFuture.completedFuture(original).thenApplyAsync(delayedLowerCase);
        CompletableFuture<String> cf3 = cf1.applyToEither(cf2, function);

        assertTrue(cf3.join().endsWith(" from applyToEither"));
        System.out.println(cf3.join());
    }

    // 11、在两个完成的阶段其中之一上调用消费函数
    static void acceptEitherExample() {
        String original = "Message";
        StringBuilder result = new StringBuilder();
        Function<String, String> delayedUpperCase = CompletableFutureExamples::delayedUpperCase;
        Function<String, String> delayedLowerCase = CompletableFutureExamples::delayedLowerCase;
        Consumer<String> consumer = s -> result.append(s).append("acceptEither");

        CompletableFuture<String> cf1 = CompletableFuture.completedFuture(original).thenApplyAsync(delayedUpperCase);
        CompletableFuture<String> cf2 = CompletableFuture.completedFuture(original).thenApplyAsync(delayedLowerCase);
        CompletableFuture<Void> cf = cf1.acceptEither(cf2, consumer);

        cf.join();
        assertTrue("Result was empty", result.toString().endsWith("acceptEither"));
    }

    // 12、在两个阶段都执行完后运行一个 Runnable
    static void runAfterBothExample() {
        String original = "Message";
        StringBuilder result = new StringBuilder();
        // 注意下面所有的阶段都是同步执行的，第一个阶段执行大写转换，第二个阶段执行小写转换。
        CompletableFuture<String> cf1 = CompletableFuture.completedFuture(original).thenApply(String::toUpperCase);
        CompletableFuture<String> cf2 = CompletableFuture.completedFuture(original).thenApply(String::toLowerCase);
        Runnable runnable = () -> result.append("done");

        cf1.runAfterBoth(cf2, runnable);

        assertTrue("Result was empty", result.length() > 0);
        System.out.println(result);
    }

    // 13、 使用BiConsumer处理两个阶段的结果
    static void thenAcceptBothExample() {
        String original = "Message";
        StringBuilder result = new StringBuilder();
        CompletableFuture<String> cf1 = CompletableFuture.completedFuture(original).thenApply(String::toLowerCase);
        CompletableFuture<String> cf2 = CompletableFuture.completedFuture(original).thenApply(String::toUpperCase);

        BiConsumer<String, String> biConsumer = (s1, s2) -> result.append(s1).append(s2);
        cf1.thenAcceptBoth(cf2, biConsumer);
        assertEquals("messageMESSAGE", result.toString());
    }

    // 14、使用BiFunction处理两个阶段的结果
    static void thenCombineExample() {
        String original = "Message";
        CompletableFuture<String> cf1 = CompletableFuture.completedFuture(original).thenApply(CompletableFutureExamples::delayedLowerCase);
        CompletableFuture<String> cf2 = CompletableFuture.completedFuture(original).thenApply(CompletableFutureExamples::delayedUpperCase);
        BiFunction<String, String, String> biFunction = (s1, s2) -> s1 + s2;

        CompletableFuture<String> cf = cf1.thenCombine(cf2, biFunction);
        assertEquals("messageMESSAGE", cf.getNow(null));
    }

    // 15、异步使用BiFunction处理两个阶段的结果
    static void thenCombineAsyncExample() {
        String original = "Message";
        CompletableFuture<String> cf1 = CompletableFuture.completedFuture(original).thenApplyAsync(CompletableFutureExamples::delayedLowerCase);
        CompletableFuture<String> cf2 = CompletableFuture.completedFuture(original).thenApplyAsync(CompletableFutureExamples::delayedUpperCase);
        BiFunction<String, String, String> biFunction = (s1, s2) -> s1 + s2;

        // 依赖的前两个阶段异步地执行，所以thenCombine()也异步地执行，即使它没有Async后缀。
        CompletableFuture<String> cf = cf1.thenCombine(cf2, biFunction);
        assertEquals("messageMESSAGE", cf.join());
    }

    // 16、组合 CompletableFuture
    static void thenComposeExample() {
        String original = "Message";
        CompletableFuture<String> cf1 = CompletableFuture.completedFuture(original).thenApply(CompletableFutureExamples::delayedLowerCase);
        CompletableFuture<String> cf2 = CompletableFuture.completedFuture(original).thenApply(CompletableFutureExamples::delayedUpperCase);

        // https://stackoverflow.com/questions/43019126/completablefuture-thenapply-vs-thencompose/43025751
        Function<String, CompletableFuture<String>> function = upper -> cf2.thenApply(s -> upper + s);
        CompletableFuture<String> cf = cf1.thenCompose(function);
        assertEquals("messageMESSAGE", cf.join());
    }

    // 17、当几个阶段中的一个完成，创建一个完成的阶段
    static void anyOfExample() {
        StringBuilder result = new StringBuilder();
        List<String> messages = Arrays.asList("a", "b", "c");

        Function<String, CompletableFuture<String>> function = msg -> CompletableFuture.completedFuture(msg).thenApply(CompletableFutureExamples::delayedUpperCase);
        BiConsumer<Object, Throwable> biConsumer = (res, th) -> {
            if (th == null) {
                assertTrue(isUpperCase((String) res));
                result.append(res);
            }
        };

        CompletableFuture.anyOf(messages.stream().map(function).toArray(CompletableFuture[]::new)).whenComplete(biConsumer);
        assertTrue("Result was empty", result.length() > 0);
        System.out.println(result);
    }

    // 18、当所有的阶段都完成后创建一个阶段
    static void allOfExample() {
        StringBuilder result = new StringBuilder();
        List<String> messages = Arrays.asList("a", "b", "c");

        // String to CompletableFuture<String>
        Function<String, CompletableFuture<String>> function = msg -> CompletableFuture.completedFuture(msg).thenApply(CompletableFutureExamples::delayedUpperCase);
        List<CompletableFuture<String>> futures = messages.stream().map(function).collect(Collectors.toList());

        BiConsumer<Void, Throwable> biConsumer = (v, th) -> {
            futures.forEach(cf -> assertTrue(isUpperCase(cf.getNow(null))));
            result.append("done");
        };

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete(biConsumer);

        assertTrue("Result was empty", result.length() > 0);
        System.out.println(result);
    }

    // 19、当所有的阶段都完成后异步地创建一个阶段
    static void allOfAsyncExample() {
        StringBuilder result = new StringBuilder();
        List<String> messages = Arrays.asList("a", "b", "c");

        // Async: String to CompletableFuture<String>
        Function<String, CompletableFuture<String>> function = msg -> CompletableFuture.completedFuture(msg).thenApplyAsync(CompletableFutureExamples::delayedUpperCase);
        List<CompletableFuture<String>> futures = messages.stream().map(function).collect(Collectors.toList());

        BiConsumer<Void, Throwable> biConsumer = (v, th) -> {
            futures.forEach(cf -> assertTrue(isUpperCase(cf.getNow(null))));
            result.append("done");
        };

        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).whenComplete(biConsumer);

        // 使用thenApplyAsync()替换那些单个的CompletableFutures的方法，allOf()会在通用池中的线程中异步地执行。所以我们需要调用join方法等待它完成。
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
