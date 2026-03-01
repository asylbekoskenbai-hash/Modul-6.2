import java.util.*;
import java.util.concurrent.*;

public class Main {
    enum TransportType { PLANE, TRAIN, BUS }
    enum ServiceClass { ECONOMY, BUSINESS }

    static class TripDetails {
        TransportType transport;
        double distance;
        ServiceClass serviceClass;
        int passengers;
        int children;
        int seniors;
        boolean hasBaggage;
        boolean isGroup;
        List<String> stopovers;

        public TripDetails(TransportType transport, double distance, ServiceClass serviceClass,
                           int passengers, int children, int seniors, boolean hasBaggage,
                           boolean isGroup, List<String> stopovers) {
            this.transport = transport;
            this.distance = distance;
            this.serviceClass = serviceClass;
            this.passengers = passengers;
            this.children = children;
            this.seniors = seniors;
            this.hasBaggage = hasBaggage;
            this.isGroup = isGroup;
            this.stopovers = stopovers;
        }
    }

    interface ICostCalculationStrategy {
        double calculateCost(TripDetails details) throws IllegalArgumentException;
    }

    static class PlaneCostStrategy implements ICostCalculationStrategy {
        private static final double BASE_RATE_PER_KM = 5.0;
        private static final double BUSINESS_MULTIPLIER = 2.5;
        private static final double BAGGAGE_FEE = 50.0;
        private static final double STOPOVER_FEE = 100.0;
        private static final double CHILD_DISCOUNT = 0.7;
        private static final double SENIOR_DISCOUNT = 0.85;

        @Override
        public double calculateCost(TripDetails details) {
            validate(details);
            double base = details.distance * BASE_RATE_PER_KM;
            if (details.serviceClass == ServiceClass.BUSINESS) {
                base *= BUSINESS_MULTIPLIER;
            }
            double passengerCost = base * details.passengers;
            double discount = 1.0;
            if (details.children > 0) discount *= CHILD_DISCOUNT;
            if (details.seniors > 0) discount *= SENIOR_DISCOUNT;
            if (details.isGroup) discount *= 0.9;
            passengerCost *= discount;

            double extra = 0;
            if (details.hasBaggage) extra += BAGGAGE_FEE * details.passengers;
            if (details.stopovers != null) extra += STOPOVER_FEE * details.stopovers.size();

            return passengerCost + extra;
        }

        private void validate(TripDetails details) {
            if (details.distance <= 0) throw new IllegalArgumentException("Расстояние должно быть положительным");
            if (details.passengers <= 0) throw new IllegalArgumentException("Количество пассажиров должно быть > 0");
            if (details.children < 0 || details.seniors < 0) throw new IllegalArgumentException("Некорректное количество детей/пенсионеров");
        }
    }

    static class TrainCostStrategy implements ICostCalculationStrategy {
        private static final double BASE_RATE_PER_KM = 2.0;
        private static final double BUSINESS_MULTIPLIER = 1.8;
        private static final double BAGGAGE_FEE = 20.0;
        private static final double CHILD_DISCOUNT = 0.6;
        private static final double SENIOR_DISCOUNT = 0.8;

        @Override
        public double calculateCost(TripDetails details) {
            validate(details);
            double base = details.distance * BASE_RATE_PER_KM;
            if (details.serviceClass == ServiceClass.BUSINESS) {
                base *= BUSINESS_MULTIPLIER;
            }
            double passengerCost = base * details.passengers;
            double discount = 1.0;
            if (details.children > 0) discount *= CHILD_DISCOUNT;
            if (details.seniors > 0) discount *= SENIOR_DISCOUNT;
            if (details.isGroup) discount *= 0.85;
            passengerCost *= discount;

            double extra = 0;
            if (details.hasBaggage) extra += BAGGAGE_FEE * details.passengers;

            return passengerCost + extra;
        }

        private void validate(TripDetails details) {
            if (details.distance <= 0) throw new IllegalArgumentException("Расстояние должно быть положительным");
            if (details.passengers <= 0) throw new IllegalArgumentException("Количество пассажиров должно быть > 0");
        }
    }

    static class BusCostStrategy implements ICostCalculationStrategy {
        private static final double BASE_RATE_PER_KM = 0.8;
        private static final double BAGGAGE_FEE = 10.0;
        private static final double CHILD_DISCOUNT = 0.5;
        private static final double SENIOR_DISCOUNT = 0.7;

        @Override
        public double calculateCost(TripDetails details) {
            validate(details);
            double base = details.distance * BASE_RATE_PER_KM;
            double passengerCost = base * details.passengers;
            double discount = 1.0;
            if (details.children > 0) discount *= CHILD_DISCOUNT;
            if (details.seniors > 0) discount *= SENIOR_DISCOUNT;
            if (details.isGroup) discount *= 0.8;
            passengerCost *= discount;

            double extra = 0;
            if (details.hasBaggage) extra += BAGGAGE_FEE * details.passengers;

            return passengerCost + extra;
        }

        private void validate(TripDetails details) {
            if (details.distance <= 0) throw new IllegalArgumentException("Расстояние должно быть положительным");
            if (details.passengers <= 0) throw new IllegalArgumentException("Количество пассажиров должно быть > 0");
        }
    }

    static class TravelBookingContext {
        private ICostCalculationStrategy strategy;

        public void setStrategy(ICostCalculationStrategy strategy) {
            this.strategy = strategy;
        }

        public double calculateTripCost(TripDetails details) {
            if (strategy == null) throw new IllegalStateException("Стратегия не выбрана");
            return strategy.calculateCost(details);
        }
    }

    interface IObserver {
        void update(String stockSymbol, double newPrice);
    }

    interface ISubject {
        void registerObserver(String stockSymbol, IObserver observer);
        void removeObserver(String stockSymbol, IObserver observer);
        void notifyObservers(String stockSymbol);
    }

    static class Stock {
        String symbol;
        double price;

        Stock(String symbol, double price) {
            this.symbol = symbol;
            this.price = price;
        }
    }

    static class StockExchange implements ISubject {
        private Map<String, Stock> stocks = new ConcurrentHashMap<>();
        private Map<String, List<IObserver>> observersByStock = new ConcurrentHashMap<>();
        private ExecutorService executor = Executors.newCachedThreadPool();

        public void setStockPrice(String symbol, double newPrice) {
            Stock stock = stocks.get(symbol);
            if (stock == null) {
                stock = new Stock(symbol, newPrice);
                stocks.put(symbol, stock);
            } else {
                stock.price = newPrice;
            }
            System.out.printf("[%tT] Биржа: цена %s изменена на %.2f\n", new Date(), symbol, newPrice);
            notifyObservers(symbol);
        }

        @Override
        public void registerObserver(String stockSymbol, IObserver observer) {
            observersByStock.computeIfAbsent(stockSymbol, k -> new CopyOnWriteArrayList<>()).add(observer);
            System.out.printf("Наблюдатель %s подписан на %s\n", observer.getClass().getSimpleName(), stockSymbol);
        }

        @Override
        public void removeObserver(String stockSymbol, IObserver observer) {
            List<IObserver> list = observersByStock.get(stockSymbol);
            if (list != null) {
                list.remove(observer);
                System.out.printf("Наблюдатель %s отписан от %s\n", observer.getClass().getSimpleName(), stockSymbol);
            }
        }

        @Override
        public void notifyObservers(String stockSymbol) {
            List<IObserver> observers = observersByStock.get(stockSymbol);
            if (observers != null) {
                double price = stocks.get(stockSymbol).price;
                for (IObserver obs : observers) {
                    executor.submit(() -> {
                        try {
                            obs.update(stockSymbol, price);
                        } catch (Exception e) {
                            System.err.println("Ошибка при уведомлении: " + e);
                        }
                    });
                }
            }
        }

        public void printReport() {
            System.out.println("\n=== ОТЧЕТ ПО ПОДПИСЧИКАМ ===");
            for (Map.Entry<String, List<IObserver>> entry : observersByStock.entrySet()) {
                System.out.printf("Акция %s: %d подписчиков\n", entry.getKey(), entry.getValue().size());
                for (IObserver obs : entry.getValue()) {
                    System.out.printf("  - %s\n", obs);
                }
            }
            System.out.println("============================\n");
        }

        public void shutdown() {
            executor.shutdown();
        }
    }

    static class Trader implements IObserver {
        private String name;

        public Trader(String name) {
            this.name = name;
        }

        @Override
        public void update(String stockSymbol, double newPrice) {
            System.out.printf("[%tT] Трейдер %s: акция %s теперь стоит %.2f\n", new Date(), name, stockSymbol, newPrice);
        }

        @Override
        public String toString() {
            return "Trader " + name;
        }
    }

    static class TradingRobot implements IObserver {
        private String id;
        private double buyThreshold;
        private double sellThreshold;

        public TradingRobot(String id, double buyThreshold, double sellThreshold) {
            this.id = id;
            this.buyThreshold = buyThreshold;
            this.sellThreshold = sellThreshold;
        }

        @Override
        public void update(String stockSymbol, double newPrice) {
            if (newPrice < buyThreshold) {
                System.out.printf("[%tT] Робот %s: ПОКУПКА %s по цене %.2f (ниже порога %.2f)\n",
                        new Date(), id, stockSymbol, newPrice, buyThreshold);
            } else if (newPrice > sellThreshold) {
                System.out.printf("[%tT] Робот %s: ПРОДАЖА %s по цене %.2f (выше порога %.2f)\n",
                        new Date(), id, stockSymbol, newPrice, sellThreshold);
            } else {
                System.out.printf("[%tT] Робот %s: %s стабильна (%.2f)\n", new Date(), id, stockSymbol, newPrice);
            }
        }

        @Override
        public String toString() {
            return "Robot " + id + " [buy=" + buyThreshold + ", sell=" + sellThreshold + "]";
        }
    }

    static class EmailNotifier implements IObserver {
        private String email;

        public EmailNotifier(String email) {
            this.email = email;
        }

        @Override
        public void update(String stockSymbol, double newPrice) {
            System.out.printf("[%tT] Email на %s: акция %s изменилась до %.2f\n", new Date(), email, stockSymbol, newPrice);
        }

        @Override
        public String toString() {
            return "Email " + email;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("========== СИСТЕМА БРОНИРОВАНИЯ ПУТЕШЕСТВИЙ ==========");
        TravelBookingContext booking = new TravelBookingContext();
        TripDetails trip = new TripDetails(
                TransportType.PLANE,
                1200.0,
                ServiceClass.BUSINESS,
                3,
                1,
                1,
                true,
                false,
                Arrays.asList("Москва", "Франкфурт")
        );

        booking.setStrategy(new PlaneCostStrategy());
        System.out.printf("Стоимость перелета: %.2f\n", booking.calculateTripCost(trip));

        booking.setStrategy(new TrainCostStrategy());
        System.out.printf("Стоимость поезда: %.2f\n", booking.calculateTripCost(trip));

        booking.setStrategy(new BusCostStrategy());
        System.out.printf("Стоимость автобуса: %.2f\n", booking.calculateTripCost(trip));

        System.out.println("\n");

        System.out.println("========== БИРЖЕВАЯ СИСТЕМА ==========");
        StockExchange exchange = new StockExchange();

        Trader trader1 = new Trader("Иван");
        TradingRobot robot1 = new TradingRobot("Alpha", 140.0, 160.0);
        EmailNotifier email1 = new EmailNotifier("investor@example.com");

        exchange.registerObserver("AAPL", trader1);
        exchange.registerObserver("AAPL", robot1);
        exchange.registerObserver("GOOG", trader1);
        exchange.registerObserver("GOOG", email1);

        exchange.setStockPrice("AAPL", 150.0);
        Thread.sleep(500);
        exchange.setStockPrice("GOOG", 2800.0);
        Thread.sleep(500);
        exchange.setStockPrice("AAPL", 155.0);
        Thread.sleep(500);
        exchange.setStockPrice("AAPL", 165.0);
        Thread.sleep(500);
        exchange.setStockPrice("GOOG", 2750.0);

        exchange.printReport();

        exchange.removeObserver("AAPL", robot1);
        exchange.setStockPrice("AAPL", 130.0);

        Thread.sleep(1000);
        exchange.shutdown();
    }
}