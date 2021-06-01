package com.example.completablefuture;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class RealLifeCompletableFutureExample extends CompletableFutureExample {

    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        // 1、首先异步调用cars方法获得Car的列表，它返回CompletionStage场景。cars消费一个远程的REST API。
        Supplier<List<Car>> supplier = CompletableFutureExample::cars;

        Function<List<Car>, CompletableFuture<List<Car>>> thenCompose = cars -> {

            // 通过rating(manufacturerId), 修改Car对象的rating值
            Function<Car, CompletableFuture<Car>> function = car -> {
                Supplier<Float> supplyAsync = () -> rating(car.manufacturerId);
                Function<Throwable, Float> exceptionally = th -> -1f;
                Function<Float, Car> thenApply = rate -> {
                    car.setRating(rate);
                    return car;
                };
                return CompletableFuture.supplyAsync(supplyAsync).exceptionally(exceptionally).thenApply(thenApply);
            };
            // 2、然后我们复合一个CompletionStage填写每个汽车的评分，通过rating(manufacturerId)返回一个CompletionStage, 它会异步地获取汽车的评分(可能又是一个REST API调用)
            List<CompletableFuture<Car>> updatedCars = cars.stream().map(function).collect(Collectors.toList());

            Function<Void, List<Car>> thenApply = v -> updatedCars.stream()
                    // .map(CompletionStage::toCompletableFuture)
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            // 3、当所有的汽车填好评分后，我们结束这个列表，所以我们调用allOf得到最终的阶段，它在前面阶段所有阶段完成后才完成。
            return CompletableFuture.allOf(updatedCars.toArray(new CompletableFuture[0])).thenApply(thenApply);
        };

        // 4、在最终的阶段调用whenComplete(),我们打印出每个汽车和它的评分。
        BiConsumer<List<Car>, Throwable> whenComplete = (cars, th) -> {
            if (th == null) {
                cars.forEach(System.out::println);
            } else {
                throw new RuntimeException(th);
            }
        };

        CompletableFuture
                .supplyAsync(supplier)
                .thenCompose(thenCompose)
                .whenComplete(whenComplete)
//                .toCompletableFuture()  // toCompletableFuture()返回CompletableFuture本身
                .join();

        long end = System.currentTimeMillis();

        System.out.println("Took " + (end - start) + " ms.");
    }

}
