import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class Barista {
    private static final int PORT = 12345;// Port number for the server
    private static final Map<String, Order> orders = new ConcurrentHashMap<>();
    private static final Queue<Order> waitingArea = new ConcurrentLinkedQueue<>();
    private static final Queue<String> brewingArea = new ConcurrentLinkedQueue<>();
    private static final Queue<String> trayArea = new ConcurrentLinkedQueue<>();
    private static final Map<String, String> customerStatus = new ConcurrentHashMap<>();
    private static final Map<String, String> lastCustomerStatus = new ConcurrentHashMap<>();
    
    // Max brew capacity
    private static final int max_tea_brew = 2;
    private static final int max_coffee_brew = 2;
    private static final AtomicInteger currentTeaBrewing = new AtomicInteger(0);
    private static final AtomicInteger currentCoffeeBrewing = new AtomicInteger(0);

    private static final ReentrantLock statusLock = new ReentrantLock();

    public static void main(String[] args) {
        System.out.println("The Server is Running without Errors");

        new Thread(() -> {
            while (true) {
                processBrewing();
                try {
                    Thread.sleep(100); 
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }).start();

         // Starting the server and accepting connections
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server Error: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

                out.println("Welcome, May I have your order, please?");// Welcome Message

                String name = in.readLine();
                if (name == null || name.trim().isEmpty()) {
                    out.println("Error --> You must give a name to continue using the server, Disconnecting please Restart.");
                    return;
                }
                orders.put(name, new Order(name));// Register the customer

                String command;
                while ((command = in.readLine()) != null) {
                    command = command.trim().toLowerCase(); 
                    handleCommand(command, name, out);
                }
            } catch (IOException e) {
                System.err.println("Connection Error: " + e.getMessage());
            }
        }
        
        
        // Handles commands from the customers
        private void handleCommand(String command, String name, PrintWriter out) {
            try {
                statusLock.lock();
                System.out.println("ClientHandler Log --> Received command: '" + command + "' from customer: " + name);

                if (command.startsWith("order status")) {
                    showOrderStatus(name, out);
                } else if (command.startsWith("order ")) {
                    handleOrder(command, name, out);
                } else if (command.equalsIgnoreCase("collect")) {
                    collectOrder(name, out);
                } else if (command.equalsIgnoreCase("exit")) {
                    out.println("I am now exiting the cafe");
                    orders.remove(name);
                    System.out.println("ClientHandler Log --> Customer '" + name + "' has exited the cafe.");
                } else {
                    out.println("Please enter a valid Command. Please try again.");
                    System.out.println("ClientHandler Log --> Unknown command received from '" + name + "': " + command);
                }
            } finally {
                statusLock.unlock();
            }
        }


        // Takes the order from the client
        private void handleOrder(String command, String name, PrintWriter out) {
            Order order = orders.get(name);
            if (order == null) {
                order = new Order(name);
                orders.put(name, order);
            }

            try {
                String fullOrder = command.replace("order", "").trim();
                String[] items = fullOrder.split("and");

                for (String item : items) {
                    item = item.trim();
                    if (item.contains("tea")) {
                        int quantity = Integer.parseInt(item.split(" ")[0]);
                        for (int i = 0; i < quantity; i++) {
                            order.addItem("tea");
                        }
                    } else if (item.contains("coffee")) {
                        int quantity = Integer.parseInt(item.split(" ")[0]);
                        for (int i = 0; i < quantity; i++) {
                            order.addItem("coffee");
                        }
                    }
                }

                waitingArea.add(order);
                updateCustomerStatus(name); 
                out.println("Order received for " + name + ": " + order);
                System.out.println("ClientHandler Log --> Order placed by '" + name + "': " + order);
            } catch (Exception e) {
                out.println("Error parsing order. Ensure the format is correct.");
            }
        }



        // Display the  order status to the customer
        private void showOrderStatus(String name, PrintWriter out) {
            try {
                statusLock.lock();
                String status = customerStatus.getOrDefault(name, "There are no active orders for " + name);
                out.println(status);
                System.out.println("ClientHandler Log --> Displaying order status for '" + name + "': " + status);
            } finally {
                statusLock.unlock();
            }
        }

        // Handles the ready orders
            private void collectOrder(String name, PrintWriter out) {
                List<String> collectedItems = new ArrayList<>();
                synchronized (trayArea) {
                    Iterator<String> iterator = trayArea.iterator();
                    while (iterator.hasNext()) {
                        String trayItem = iterator.next();
                        String[] parts = trayItem.split(":");
                        if (parts[0].equals(name)) {
                            collectedItems.add(parts[1]);
                            iterator.remove();
                        }
                    }
                }
            
                if (collectedItems.isEmpty()) {
                    out.println("Your order is not ready for collection. Please Wait.");
                    System.out.println("ClientHandler Log --> No items ready for collection for '" + name + "'");
                } else {
                    StringBuilder collectionMessage = new StringBuilder("You have collected: ");
                    Map<String, Integer> itemCounts = new HashMap<>();
                    for (String item : collectedItems) {
                        itemCounts.put(item, itemCounts.getOrDefault(item, 0) + 1);
                    }
                    for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
                        collectionMessage.append(entry.getValue()).append(" ").append(entry.getKey()).append("(s), ");
                    }
                    collectionMessage.setLength(collectionMessage.length() - 2); 
                    out.println(collectionMessage.toString());
                    System.out.println("ClientHandler Log --> Collecting items for '" + name + "': " + collectionMessage);
                }
            
                // Update the customers order status
                updateCustomerStatus(name);
            }
        } 


    private static void processBrewing() {
        ExecutorService teaBrewers = Executors.newFixedThreadPool(max_tea_brew);
        ExecutorService coffeeBrewers = Executors.newFixedThreadPool(max_coffee_brew);

        while (true) {
            Order order = waitingArea.poll(); 
            if (order != null) {
                List<String> remainingItems = new ArrayList<>();

                for (String item : order.items) {
                    boolean addedToBrew = false;
                    if (item.equals("tea") && currentTeaBrewing.get() < max_tea_brew) {
                        currentTeaBrewing.incrementAndGet();
                        brewingArea.add(order.customerName + ":tea");
                        addedToBrew = true;
                        updateCustomerStatus(order.customerName);
                        teaBrewers.execute(() -> {
                            brewItem("tea", order.customerName);
                        });
                    } else if (item.equals("coffee") && currentCoffeeBrewing.get() < max_coffee_brew) {
                        currentCoffeeBrewing.incrementAndGet();
                        brewingArea.add(order.customerName + ":coffee");
                        addedToBrew = true;
                        updateCustomerStatus(order.customerName);
                        coffeeBrewers.execute(() -> {
                            brewItem("coffee", order.customerName);
                        });
                    }

                    if (!addedToBrew) {
                        remainingItems.add(item);
                    }
                }

                if (!remainingItems.isEmpty()) {
                    order.items.clear();
                    order.items.addAll(remainingItems);
                    waitingArea.add(order);
                    updateCustomerStatus(order.customerName);
                }
            }
            try {
                Thread.sleep(100); 
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }


    // Simulating brewing process
    private static void brewItem(String item, String customerName) {
        try {
            System.out.println("Barista log --> Brewing " + item + " for " + customerName);
            if (item.equals("tea")) {
                Thread.sleep(30000); //  30 seconds for tea
            } else if (item.equals("coffee")) {
                Thread.sleep(45000); //  45 seconds for coffee
            }

            synchronized (brewingArea) {
                brewingArea.remove(customerName + ":" + item);
                if (item.equals("tea")) {
                    currentTeaBrewing.decrementAndGet();
                } else if (item.equals("coffee")) {
                    currentCoffeeBrewing.decrementAndGet();
                }
            }

            synchronized (trayArea) {
                trayArea.add(customerName + ":" + item);
            }
            System.out.println("Barista log --> Moved " + item + " for " + customerName + " to tray area.");

            updateCustomerStatus(customerName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void updateCustomerStatus(String name) {
        try {
            statusLock.lock();
            int waitingTeas = 0, waitingCoffees = 0;
            int brewingTeas = 0, brewingCoffees = 0;
            int trayTeas = 0, trayCoffees = 0;

            for (Order o : waitingArea) {
                if (o.customerName.equals(name)) {
                    waitingTeas += Collections.frequency(o.items, "tea");
                    waitingCoffees += Collections.frequency(o.items, "coffee");
                }
            }

            for (String brewingItem : brewingArea) {
                String[] parts = brewingItem.split(":");
                if (parts[0].equals(name)) {
                    if (parts[1].equals("tea")) brewingTeas++;
                    if (parts[1].equals("coffee")) brewingCoffees++;
                }
            }

            for (String trayItem : trayArea) {
                String[] parts = trayItem.split(":");
                if (parts[0].equals(name)) {
                    if (parts[1].equals("tea")) trayTeas++;
                    if (parts[1].equals("coffee")) trayCoffees++;
                }
            }

            StringBuilder status = new StringBuilder("Order status for " + name + ":\n");
            if (waitingTeas > 0 || waitingCoffees > 0) {
                status.append("- ").append(waitingTeas).append(" tea(s) and ")
                      .append(waitingCoffees).append(" coffee(s) in waiting area\n");
            }
            if (brewingTeas > 0 || brewingCoffees > 0) {
                status.append("- ").append(brewingTeas).append(" tea(s) and ")
                      .append(brewingCoffees).append(" coffee(s) currently being prepared\n");
            }
            if (trayTeas > 0 || trayCoffees > 0) {
                status.append("- ").append(trayTeas).append(" tea(s) and ")
                      .append(trayCoffees).append(" coffee(s) in the tray area\n");
            }

            String newStatus = status.toString();
            if (!newStatus.equals(lastCustomerStatus.get(name))) {
                customerStatus.put(name, newStatus);
                lastCustomerStatus.put(name, newStatus);
                System.out.println("Status Update Log --> Status for '" + name + "' updated to:\n" + newStatus);
            }

        } finally {
            statusLock.unlock();
        }
    }

    static class Order {
        private final String customerName;
        private final List<String> items = new ArrayList<>();

        public Order(String customerName) {
            this.customerName = customerName;
        }

        public void addItem(String item) {
            items.add(item);
        }

        @Override
        public String toString() {
            return String.join(", ", items);
        }
    }
}
