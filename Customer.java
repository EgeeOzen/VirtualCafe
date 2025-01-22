import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.NoSuchElementException;

public class Customer {
    private static final String SERVER_ADDRESS = "localhost";// Server's address
    private static final int SERVER_PORT = 12345;// Port to connect to the server



    //Shutdown hook for handling SIGINT with CTRL+C
    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nSIGINT received. Exiting the cafe. Goodbye!");
        }));

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println(in.readLine());
            Scanner scanner = new Scanner(System.in);

            // Ask the user name and send it to the server
            System.out.print("Enter your name: ");
            out.println(scanner.nextLine());

            while (true) {
                try {
                    // Displaying the Menu
                    System.out.println("\n<--- Virtual Cafe Commands --->");
                    System.out.println("Please Type Your Command:");
                    System.out.println("  -> To place an order: 'order ...");
                    System.out.println("  -> To check order status: 'order status'");
                    System.out.println("  -> To collect your order: 'collect'");
                    System.out.println("  -> To exit the cafe: 'exit'");
                    System.out.print("Your Command is: ");
                    // Read user input and send the command to the server
                    String command = scanner.nextLine();
                    out.println(command);

                    if (command.equalsIgnoreCase("exit")) {
                        System.out.println("You have exited the cafe. See You Again, GOODBYE :)");
                        break;
                    }

                    // Handling server response
                    String response;
                    while ((response = in.readLine()) != null) {
                        System.out.println("Server: " + response);
                        if (!in.ready()) {
                            break;
                        }
                    }
                } catch (NoSuchElementException e) {
                    System.out.println("\n Exiting from the system.");
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Connection Error Detected: " + e.getMessage());
        }
    }
}
