package com.example.completablefuture;

public class RealLifeWithoutCompletableFutureExample extends CompletableFutureExample {

    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        cars().forEach(car -> {
            car.setRating(rating(car.manufacturerId));
            System.out.println(car);
        });

        long end = System.currentTimeMillis();

        System.out.println("Took " + (end - start) + " ms.");
    }

}

