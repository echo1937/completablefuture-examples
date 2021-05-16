package com.example.completablefuture;

import java.util.Arrays;
import java.util.List;

public class CompletableFutureExample {

    static float rating(int manufacturer) {
        try {
            simulateDelay();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        switch (manufacturer) {
            case 2:
                return 4f;
            case 3:
                return 4.1f;
            case 7:
                return 4.2f;
            default:
                return 5f;
        }
    }

    static List<Car> cars() {
        return Arrays.asList(
                new Car(1, 3, "Fiesta", 2017),
                new Car(2, 7, "Camry", 2014),
                new Car(3, 2, "M2", 2008));
    }

    private static void simulateDelay() throws InterruptedException {
        Thread.sleep(5000);
    }
}
