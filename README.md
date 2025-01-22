# Virtual Cafe System

## Brief Description
The Virtual Cafe Management System is a multi thread client-server app which is written in Java programming language. 
The system allows customers to place their orders, check their order's status every time they want, collect their order when it is ready to, and exit from the server without any errors. 


## Features of the System

- **Multi Thread Server**: This feature allows the server to handle multiple tasks at the same time and makes it time efficient.

- **Order Management**: This allows customers to place their orders with various quantities of tea and coffee.

- **Order Status**: This provides customers a real time status of their order, they can use it any time they want. It tells them whether it is in waiting area, brewing or in the tray area.

- **Order Safety**: The server protects customers orders by associating them with their names and provides safety for someone to get another customer's order.

- **Implemented SIGINT signals for bonus point**: When you exit the code with "Ctrl + C" the code will exit the cafe with an appropriate message.


## How to Compile and Run
### Steps
1. **Compile both Server and Customer**
   javac Barista.java 
   javac Customer.java

2. **First Run the Server**
   java Barista


3. **Then Run the Customer**
   java Customer


4. **Now Everything Should Be Running**
   You can start using the system by typing into the terminal.