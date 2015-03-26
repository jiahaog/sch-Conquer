package Sockets;

import AwesomeSockets.AwesomeServerSocket;
import Constants.DotsConstants;
import Dots.*;
import Model.*;

import java.io.IOException;
import java.util.Scanner;

/**
 *
 * Primary object to be run by the server for the game
 * Created by JiaHao on 25/2/15.
 */
public class DotsServer {

    private final int port;

    public DotsServer(int port) {
        this.port = port;
    }

    public void start() throws IOException {

        // initialize server
        AwesomeServerSocket server = new AwesomeServerSocket(port);
        server.acceptClient();

        // initialize game object
        DotsGame dotsGame = new DotsGame();
        DotsLocks dotsLocks = dotsGame.getGameLocks();

        // init listener thread
        DotsServerClientListener dotsClientListener = new DotsServerClientListener(server, dotsGame);
        Thread listenerThread = new Thread(dotsClientListener);

        // init scanner thread
        Scanner scanner = new Scanner(System.in);
        DotsServerScannerListener dotsScannerListener = new DotsServerScannerListener(dotsGame, scanner);
        Thread scannerThread = new Thread(dotsScannerListener);

        // starts threads
        listenerThread.start();
        scannerThread.start();

        // block for game sequence
        while (dotsGame.isGameRunning()) {

            // we acquire a lock and wait upon the game locks here.
            synchronized (dotsLocks) {

                while (!dotsLocks.isBoardChanged()) {

                    try {
                        dotsLocks.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }


                // when we are notified, we will do board updating,
                doBoardUpdating(server, dotsGame);

                // and reset the boardchanged variable in dotsLocks to false.
                dotsLocks.setBoardChanged(false);

            }

        }

        // closes server and clients when game over
        server.closeServer();

        System.out.println("Game over");
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException {

        DotsServer dotsServer = new DotsServer(DotsConstants.CLIENT_PORT);
        dotsServer.start();
        System.out.println("Ended");

    }

    /**
     * This function is called when the main thread is notified and awakened, indicating that
     * the board has been changed and the screen needs updating
     * @param serverSocket server to send the board to
     * @param dotsGame the game object
     * @throws IOException if sending message through the socket fails
     */
    private static void doBoardUpdating(AwesomeServerSocket serverSocket, DotsGame dotsGame) throws IOException {

        // update the board on the current device
        // TODO change this to a method to update the screen when on android
        // TODO use a interface callback here to make it more modular
        dotsGame.getDotsBoard().printWithIndex();


        // update the board on the remote device
        sendBoardToClient(serverSocket, dotsGame);

    }

    /**
     * Helper method to package and send the board to the client
     */
    private static void sendBoardToClient(AwesomeServerSocket serverSocket, DotsGame dotsGame) throws IOException {

        DotsMessageBoard messageBoard = new DotsMessageBoard(dotsGame.getDotsBoard());
        DotsSocketHelper.sendMessageToClient(serverSocket, messageBoard);

    }

}

/**
 * Thread to listen for input from the scanner
 * TODO translate the scanner to system.in or something to parse touches on android to maintain modularity?
 */
class DotsServerScannerListener implements Runnable {

    private final Scanner scanner;
    private final DotsGame dotsGame;

    public DotsServerScannerListener(DotsGame dotsGame, Scanner scanner) {
        this.scanner = scanner;
        this.dotsGame = dotsGame;
    }

    @Override
    public void run() {

        while (this.dotsGame.isGameRunning()) {

            DotsInteraction dotsInteraction = DotsSocketHelper.getInteractionFromScanner(0, this.scanner);

            boolean result = this.dotsGame.doMove(dotsInteraction);


            if (result) {

                // TODO update the android screen with the corresponding method

                /**
                 * There are two types of game screen updates:
                 * 1. Update to show user touches when touch down and dragged
                 * 2. Update the entire board when elements are cleared
                 *
                 * In this function, we only update the first case, which is to show valid touches
                 * on user interaction
                 *
                 * The second case is dealt directly in the dotsGame object itself, which will notify the locks
                 * when the board needs to be updated, and will hence trigger the main thread where doBoardUpdating()
                 * will be triggered.
                 *
                 */

                updateScreenForTouchInteractions(dotsInteraction);


                // TODO send the valid interaction to the client player so that the client can see the move made by the other player

            }

        }

        System.out.println("Game over!");
    }

    /**
     * Method here to update the screen for touch interaction, i.e. reflect touches
     * @param dotsInteraction interaction object
     */
    private void updateScreenForTouchInteractions(DotsInteraction dotsInteraction) {

        // TODO add android to update screen for the touch interactions
        // TODO use a interface callback here to make it more modular
        // debug method to print valid interaction
        System.out.println("DRAW on screen touch interaction: " + dotsInteraction.toString());
    }

}

/**
 * Thread to listen and deal with messages from the client
 */
class DotsServerClientListener implements Runnable {

    private final AwesomeServerSocket serverSocket;

    private final DotsGame dotsGame;

    public DotsServerClientListener(AwesomeServerSocket serverSocket, DotsGame dotsGame) {
        this.serverSocket = serverSocket;
        this.dotsGame = dotsGame;

    }

    @Override
    public void run() {

        if (this.dotsGame == null) {
            throw new NullPointerException();
        }

        try {

            while (this.dotsGame.isGameRunning()) {

                // read and deal with messages from the client
                DotsMessage message = DotsSocketHelper.readMessageFromClient(serverSocket);
                this.dealWithMessage(message);


            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        System.out.println("Game over!");
    }


    /**
     * This method deals with messages sent from the client.
     * @param message
     * @throws IOException
     */
    private void dealWithMessage(DotsMessage message) throws IOException {

        // Right now, only one case of the message, which is an interaction.
        // The response here would be to respond with a boolean indicating the validity of the interaction
        if (message instanceof DotsMessageInteraction) {

            // process the interaction
            DotsInteraction receivedInteraction = ((DotsMessageInteraction) message).getDotsInteraction();
            boolean response = this.dotsGame.doMove(receivedInteraction);

            // Package and send a response to the client
            DotsMessageResponse dotsMessageResponse = new DotsMessageResponse(response);
            DotsSocketHelper.sendMessageToClient(this.serverSocket, dotsMessageResponse);

        } else {

            System.err.println("Invalid message type");

        }
    }
}
